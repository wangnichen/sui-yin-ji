package com.xiaozhangben.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 极简 FileProvider：把应用缓存 camera/ 下的照片以 content:// 暴露给系统相机写入。
 * 框架 android.jar 里没有 FileProvider（那是 androidx 的），本项目无第三方依赖，故自实现。
 * 仅支持文件读写与基础 query，权限由 manifest 的 exported=false + grantUriPermissions 授予。
 */
public class SimpleFileProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    private File fileFor(Uri uri) {
        String name = uri == null ? null : uri.getLastPathSegment();
        if (name == null || name.contains("..") || name.contains("/")) return null;
        return new File(new File(getContext().getCacheDir(), "camera"), name);
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        File f = fileFor(uri);
        if (f != null && f.exists()) c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(uri);
        if (f == null) throw new FileNotFoundException("bad uri");
        if (!f.exists() && !f.getParentFile().exists()) f.getParentFile().mkdirs();
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (Exception e) {
                throw new FileNotFoundException(e.getMessage());
            }
        }
        int flags = (mode != null && mode.contains("w"))
            ? ParcelFileDescriptor.MODE_READ_WRITE
            : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(f, flags);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        File f = fileFor(uri);
        return (f != null && f.exists() && f.delete()) ? 1 : 0;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
