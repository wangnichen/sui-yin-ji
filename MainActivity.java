package com.xiaozhangben.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int PICK_FILE = 1001;
    private static final int IMAGE_PICK = 1002;
    private static final int MAX_FILE_SIZE = 1024 * 1024;
    private static final int MAX_IMAGE_SIZE = 1024 * 1024 * 2;

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return !url.startsWith("file:///android_asset/");
            }
        });

        webView.addJavascriptInterface(new NativeBridge(), "Native");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        if (requestCode == PICK_FILE) {
            handleFilePick(data.getData());
        } else if (requestCode == IMAGE_PICK) {
            handleImagePick(data.getData());
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void handleFilePick(Uri uri) {
        try {
            byte[] bytes = readStream(uri, MAX_FILE_SIZE);
            if (bytes == null || bytes.length == 0) {
                notifyJs("App._onFileError(\"文件为空\");");
                return;
            }
            String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
            notifyJs("window._pendingFileBase64=\"" + base64 + "\";");
            notifyJs("App._onFileReady();");
        } catch (Exception e) {
            notifyJs("App._onFileError(\"文件读取失败\");");
        }
    }

    private void handleImagePick(Uri uri) {
        try {
            byte[] bytes = readStream(uri, MAX_IMAGE_SIZE);
            if (bytes == null || bytes.length == 0) {
                notifyJs("App._onFileError(\"图片为空\");");
                return;
            }
            String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
            notifyJs("window._pendingImageBase64=\"" + base64 + "\";");
            notifyJs("App._onImageReady(window._pendingImageBase64);");
        } catch (Exception e) {
            notifyJs("App._onFileError(\"图片读取失败\");");
        }
    }

    private byte[] readStream(Uri uri, int maxSize) throws IOException {
        InputStream is = null;
        try {
            is = getContentResolver().openInputStream(uri);
            if (is == null) return null;
            byte[] buffer = new byte[maxSize];
            int total = 0;
            int n;
            while ((n = is.read(buffer, total, buffer.length - total)) > 0) {
                total += n;
            }
            return java.util.Arrays.copyOf(buffer, total);
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void writeFileSafe(File file, byte[] data) throws IOException {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            os.write(data);
            os.flush();
        } finally {
            if (os != null) {
                try { os.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void notifyJs(String script) {
        final WebView wv = webView;
        if (wv != null) {
            runOnUiThread(() -> wv.evaluateJavascript(script, null));
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public class NativeBridge {
        @JavascriptInterface
        public String saveFile(String base64Data, String fileName, String mimeType) {
            try {
                byte[] bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
                if (bytes == null || bytes.length == 0) return "ERROR: empty data";

                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) downloadsDir.mkdirs();

                File outFile = new File(downloadsDir, fileName);
                writeFileSafe(outFile, bytes);

                android.media.MediaScannerConnection.scanFile(webView.getContext(),
                    new String[]{outFile.getAbsolutePath()}, new String[]{mimeType}, null);

                return outFile.getAbsolutePath();
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public void openFilePicker() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    MainActivity.this.startActivityForResult(intent, PICK_FILE);
                } catch (Exception e) {
                    notifyJs("App._onFileError(\"无法打开文件选择器\");");
                }
            });
        }

        @JavascriptInterface
        public void pickImage() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setType("image/*");
                    MainActivity.this.startActivityForResult(intent, IMAGE_PICK);
                } catch (Exception e) {
                    notifyJs("App._onFileError(\"无法打开相册\");");
                }
            });
        }
    }
}
