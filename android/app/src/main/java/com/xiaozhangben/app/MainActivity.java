package com.xiaozhangben.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int PICK_FILE = 1001;
    private static final int IMAGE_PICK = 1002;
    private static final int TAKE_PHOTO = 1003;
    private static final int MAX_FILE_SIZE = 1024 * 1024;
    private static final int MAX_IMAGE_SIZE = 1024 * 1024 * 2;

    // 本地资源承载源：端侧离线 AI（Web Worker / WASM / fetch）需要 http 源。
    // 通过 shouldInterceptRequest 全部映射回 APK assets，不出网络、不联网。
    private static final String APP_HOST = "suiyinji.local";
    private static final String APP_URL = "http://" + APP_HOST + "/index.html";
    private static final String LEGACY_URL = "file:///android_asset/index.html";
    // LLM 模型体积（用于释放进度计算；模型替换时需同步更新）
    private static final long MODEL_ASSET_SIZE = 491400032L;

    private WebView webView;
    private SharedPreferences prefs;
    private volatile String modelStatus = "idle"; // idle / progress:N / ready / error
    private Thread modelThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("app", MODE_PRIVATE);

        // 透明状态栏：让内容延伸到状态栏下方，状态栏图标为深色（适配浅色背景）
        // 实现类似抖音的「通透」效果：状态栏区域透出页面/玻璃顶栏背景
        try {
            View decor = getWindow().getDecorView();
            int vis = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                vis |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(vis);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        } catch (Exception ignored) {}

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

        // 渲染性能：渲染进程优先级拉满（API 26+），防止 WebView 渲染线程被降级导致滑动掉帧
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(
                WebView.RENDERER_PRIORITY_IMPORTANT,
                /* whenInBackground= */ false);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return !(url.startsWith("file:///android_asset/") || url.startsWith("http://" + APP_HOST + "/"));
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // 本地 http 源请求全部由 APK assets / 应用目录应答（含 Range 分块），不产生任何网络流量
                try {
                    if (APP_HOST.equals(request.getUrl().getHost())) {
                        return serveLocal(request);
                    }
                } catch (Exception ignored) {}
                return null;
            }
        });

        webView.addJavascriptInterface(new NativeBridge(), "Native");
        // 首次升级启动走旧 file:// 源导出存量 localStorage，完成后切换到本地 http 源（后续启动直达）
        webView.loadUrl(prefs.getBoolean("migrated", false) ? APP_URL : LEGACY_URL);
    }

    // ===== 本地资源服务（assets + 已释放模型，支持 HEAD/Range/CORS隔离头） =====

    private WebResourceResponse serveLocal(WebResourceRequest req) {
        String path = req.getUrl().getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) path = "/index.html";
        if (path.contains("..")) return null;
        String assetPath = path.substring(1);
        boolean head = "HEAD".equalsIgnoreCase(req.getMethod());
        String mime = mimeFor(assetPath);
        boolean text = mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json");

        // LLM 模型优先读取已释放到应用目录的副本：支持 Range 分块随机读取，避免整包进内存
        if (assetPath.endsWith("model.gguf")) {
            File gguf = new File(getFilesDir(), "offline-ai/model.gguf");
            if (gguf.exists() && gguf.length() > 0) {
                try {
                    return serveGguf(gguf, head, req.getRequestHeaders().get("Range"), mime, text);
                } catch (Exception ignored) {}
            }
        }

        InputStream in = null;
        try {
            in = getAssets().open(assetPath);
        } catch (IOException e) {
            return notFound(text);
        }
        if (head) {
            try { in.close(); } catch (IOException ignored) {}
            in = new ByteArrayInputStream(new byte[0]);
        }
        return new WebResourceResponse(mime, text ? "UTF-8" : null, 200, "OK", headers(null, 0), in);
    }

    private WebResourceResponse serveGguf(File f, boolean head, String range, String mime, boolean text) throws IOException {
        long total = f.length();
        long start = 0, end = total - 1;
        int status = 200;
        String reason = "OK";
        if (range != null && range.startsWith("bytes=")) {
            String[] parts = range.substring(6).split("-", -1);
            try {
                if (parts[0].length() > 0) start = Long.parseLong(parts[0].trim());
                if (parts.length > 1 && parts[1].trim().length() > 0) end = Math.min(Long.parseLong(parts[1].trim()), total - 1);
            } catch (NumberFormatException ignored) {}
            if (start > 0 || end < total - 1) {
                status = 206;
                reason = "Partial Content";
            }
        }
        if (start >= total) { // 越界 Range
            Map<String, String> h = headers(null, total);
            h.put("Content-Range", "bytes */" + total);
            return new WebResourceResponse(mime, null, 416, "Range Not Satisfiable", h, new ByteArrayInputStream(new byte[0]));
        }
        InputStream data;
        if (head) {
            data = new ByteArrayInputStream(new byte[0]); // HEAD：只回长度
        } else {
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            raf.seek(start);
            data = new LimitedFileInputStream(raf, end - start + 1);
        }
        return new WebResourceResponse(mime, text ? "UTF-8" : null, status, reason,
                headers((status == 206 ? "bytes " + start + "-" + end + "/" + total : null), total), data);
    }

    private Map<String, String> headers(String contentRange, long contentLength) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept-Ranges", "bytes");
        if (contentRange != null) h.put("Content-Range", contentRange);
        if (contentLength > 0) h.put("Content-Length", String.valueOf(contentLength));
        // 与页面同源隔离：启用 SharedArrayBuffer，OCR/WASM 推理可用多线程
        h.put("Cross-Origin-Opener-Policy", "same-origin");
        h.put("Cross-Origin-Embedder-Policy", "require-corp");
        h.put("Cross-Origin-Resource-Policy", "same-origin");
        return h;
    }

    private WebResourceResponse notFound(boolean text) {
        return new WebResourceResponse(text ? "text/plain" : "application/octet-stream",
                text ? "UTF-8" : null, 404, "Not Found", headers(null, 0),
                new ByteArrayInputStream(new byte[0]));
    }

    private String mimeFor(String p) {
        String l = p.toLowerCase();
        if (l.endsWith(".html")) return "text/html";
        if (l.endsWith(".js") || l.endsWith(".mjs")) return "text/javascript";
        if (l.endsWith(".css")) return "text/css";
        if (l.endsWith(".wasm")) return "application/wasm";
        if (l.endsWith(".json") || l.endsWith(".map")) return "application/json";
        if (l.endsWith(".svg")) return "image/svg+xml";
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
        if (l.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }

    private static class LimitedFileInputStream extends InputStream {
        private final RandomAccessFile raf;
        private long remaining;

        LimitedFileInputStream(RandomAccessFile raf, long length) {
            this.raf = raf;
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = raf.read();
            if (b < 0) return -1;
            remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int n = raf.read(b, off, (int) Math.min(len, remaining));
            if (n > 0) remaining -= n;
            return n;
        }

        @Override
        public void close() throws IOException {
            raf.close();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == TAKE_PHOTO) {
            // 拍照走 EXTRA_OUTPUT，data 为 null，从临时文件读
            handleCameraShot();
            return;
        }
        if (data == null || data.getData() == null) return;

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

    // 退到后台时暂停 WebView 的定时器/渲染/网络，省电省内存；回前台自动恢复
    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
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

    /** 相机拍照完成：降采样到 2048px 长边、JPEG 压缩后回传（OCR 足够清晰，又不撑爆 WebView 内存） */
    private void handleCameraShot() {
        File shot = null;
        try {
            File dir = new File(getCacheDir(), "camera");
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (shot == null || f.lastModified() > shot.lastModified()) shot = f;
                }
            }
            if (shot == null || shot.length() == 0) {
                notifyJs("App._onFileError(\"照片为空\");");
                return;
            }
            android.graphics.BitmapFactory.Options opt = new android.graphics.BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(shot.getAbsolutePath(), opt);
            int maxDim = 2048, scale = 1;
            while (Math.max(opt.outWidth, opt.outHeight) / scale > maxDim) scale *= 2;
            opt.inJustDecodeBounds = false;
            opt.inSampleSize = Math.max(scale, 1);
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(shot.getAbsolutePath(), opt);
            if (bmp == null) {
                notifyJs("App._onFileError(\"照片解码失败\");");
                return;
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, bos);
            bmp.recycle();
            String base64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP);
            notifyJs("window._pendingImageBase64=\"" + base64 + "\";");
            notifyJs("App._onImageReady(window._pendingImageBase64);");
            shot.delete();
        } catch (Exception e) {
            notifyJs("App._onFileError(\"照片读取失败\");");
            if (shot != null) shot.delete();
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

    private byte[] readFileBytes(File f) throws IOException {
        InputStream is = null;
        try {
            is = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException ignored) {}
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

    /** 把 APK 内的 LLM 模型一次性释放到应用私有目录（支持 Range 分块读取，避免整包驻留内存） */
    private synchronized void ensureModelInternal() {
        if (modelThread != null && modelThread.isAlive()) return;
        final File out = new File(getFilesDir(), "offline-ai/model.gguf");
        if (out.exists() && out.length() > 0) {
            modelStatus = "ready";
            return;
        }
        modelStatus = "progress:0";
        modelThread = new Thread(() -> {
            try {
                File dir = new File(getFilesDir(), "offline-ai");
                if (!dir.exists()) dir.mkdirs();
                File tmp = new File(dir, "model.gguf.tmp");
                if (tmp.exists()) tmp.delete();

                long total = MODEL_ASSET_SIZE;
                try {
                    long fdLen = getAssets().openFd("offline-ai/llm/model.gguf").getLength();
                    if (fdLen > 0) total = fdLen;
                } catch (Exception ignored) {}

                InputStream in = getAssets().open("offline-ai/llm/model.gguf");
                OutputStream os = new FileOutputStream(tmp);
                byte[] buf = new byte[256 * 1024];
                long copied = 0, lastPct = -1;
                int n;
                while ((n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                    copied += n;
                    if (total > 0) {
                        long pct = copied * 100 / total;
                        if (pct != lastPct) {
                            lastPct = pct;
                            modelStatus = "progress:" + Math.min(99, pct);
                        }
                    }
                }
                os.flush();
                os.close();
                in.close();

                if (out.exists()) out.delete();
                if (!tmp.renameTo(out)) throw new IOException("rename failed");
                modelStatus = "ready";
            } catch (Throwable t) {
                modelStatus = "error";
            }
        }, "model-extract");
        modelThread.start();
    }

    public class NativeBridge {
        @JavascriptInterface
        public String saveFile(String base64Data, String fileName, String mimeType) {
            try {
                byte[] bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
                if (bytes == null || bytes.length == 0) return "ERROR: empty data";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+：本应用不申请存储权限，必须走 MediaStore（SAF 分区存储规范）
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
                    values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType);
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
                    android.net.Uri item = getContentResolver().insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (item == null) return "ERROR: MediaStore insert failed";
                    OutputStream os = null;
                    try {
                        os = getContentResolver().openOutputStream(item);
                        if (os == null) return "ERROR: openOutputStream failed";
                        os.write(bytes);
                        os.flush();
                    } finally {
                        if (os != null) { try { os.close(); } catch (IOException ignored) {} }
                    }
                    values.clear();
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(item, values, null, null);
                    return "下载目录/" + fileName;
                }

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

        /** 拍照识别：调起系统相机，高清输出到应用缓存（FileProvider），拍完回调 _onImageReady */
        @JavascriptInterface
        public void takePhoto() {
            runOnUiThread(() -> {
                try {
                    File dir = new File(getCacheDir(), "camera");
                    if (!dir.exists()) dir.mkdirs();
                    File[] old = dir.listFiles();
                    if (old != null) for (File f : old) f.delete();
                    File out = new File(dir, "shot_" + System.currentTimeMillis() + ".jpg");
                    // 自实现 SimpleFileProvider：content:// 直接映射 cache/camera/<文件名>
                    Uri uri = Uri.parse("content://com.xiaozhangben.app.fileprovider/" + out.getName());
                    Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
                    intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
                    int grant = Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    intent.addFlags(grant);
                    // 显式授权给所有能处理拍照的相机应用（部分机型隐式授权不生效）
                    java.util.List<android.content.pm.ResolveInfo> ris =
                        getPackageManager().queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
                    for (android.content.pm.ResolveInfo ri : ris) {
                        grantUriPermission(ri.activityInfo.packageName, uri, grant);
                    }
                    startActivityForResult(intent, TAKE_PHOTO);
                } catch (Exception e) {
                    notifyJs("App._onFileError(\"无法打开相机\");");
                }
            });
        }

        // ===== 端侧离线 AI：模型释放 =====
        @JavascriptInterface
        public void ensureOfflineModel() {
            ensureModelInternal();
        }

        @JavascriptInterface
        public String offlineModelStatus() {
            File out = new File(getFilesDir(), "offline-ai/model.gguf");
            if (out.exists() && out.length() > 0) return "ready";
            return modelStatus;
        }

        // ===== 存量数据迁移：file:// 源 → 本地 http 源 =====
        @JavascriptInterface
        public void legacyExport(String json) {
            boolean ok = false;
            try {
                File f = new File(getFilesDir(), "legacy_data.json");
                writeFileSafe(f, json == null ? "{}".getBytes("UTF-8") : json.getBytes("UTF-8"));
                ok = f.length() > 2 || "{}".equals(json);
            } catch (Exception ignored) {}
            if (!ok) return; // 写入失败则留在旧源，下次启动重试，避免数据丢失
            prefs.edit().putBoolean("migrated", true).apply();
            runOnUiThread(() -> {
                WebView wv = webView;
                if (wv != null) wv.loadUrl(APP_URL);
            });
        }

        @JavascriptInterface
        public String legacyImport() {
            try {
                File f = new File(getFilesDir(), "legacy_data.json");
                if (!f.exists() || f.length() == 0) return "";
                return new String(readFileBytes(f), "UTF-8");
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void legacyImportDone() {
            try {
                new File(getFilesDir(), "legacy_data.json").delete();
            } catch (Exception ignored) {}
        }
    }
}
