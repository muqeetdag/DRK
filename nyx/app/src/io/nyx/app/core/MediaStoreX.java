package io.nyx.app.core;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Saves a completed download into the public gallery / Downloads folder. */
public final class MediaStoreX {

    private MediaStoreX() {}

    public static File folder(Context ctx) {
        String name = folderName(ctx);
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), name);
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return dir;
    }

    public static Uri publish(Context ctx, File source, String mime, String displayName) {
        return publish(ctx, source, mime, displayName, folderName(ctx));
    }

    public static Uri publish(Context ctx, File source, String mime, String displayName,
                              String folder) {
        if (source == null || !source.exists() || source.length() == 0) return null;
        boolean video = mime != null && mime.startsWith("video");
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
                values.put(MediaStore.MediaColumns.MIME_TYPE,
                        mime == null ? (video ? "video/mp4" : "image/jpeg") : mime);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES)
                                + "/" + safe(folder));
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri collection = video
                        ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                Uri target = ctx.getContentResolver().insert(collection, values);
                if (target == null) return null;
                try (OutputStream out = ctx.getContentResolver().openOutputStream(target);
                     InputStream in = new FileInputStream(source)) {
                    if (out == null) return null;
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    out.flush();
                }
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                ctx.getContentResolver().update(target, values, null, null);
                return target;
            }
        } catch (Throwable ignored) {
            // fall through to public Downloads
        }
        try {
            File dir = folder(ctx, folder);
            File dest = new File(dir, displayName);
            int suffix = 1;
            while (dest.exists()) {
                dest = new File(dir, displayName.replaceFirst("(\\.[A-Za-z0-9]+)$", "-" + suffix + "$1"));
                suffix++;
            }
            copy(source, dest);
            MediaScannerConnection.scanFile(ctx, new String[]{dest.getAbsolutePath()},
                    new String[]{mime}, null);
            return Uri.fromFile(dest);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String folderName(Context ctx) {
        String name;
        try {
            name = Prefs.open(ctx).getString(Prefs.FOLDER, GenSettings.FOLDER);
        } catch (Throwable t) {
            name = GenSettings.FOLDER;
        }
        return safe(name);
    }

    public static File folder(Context ctx, String name) {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), safe(name));
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return dir;
    }

    public static String safe(String name) {
        if (name == null) return "NYX";
        String clean = name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        return clean.isEmpty() ? "NYX" : clean;
    }

    public static boolean copy(File src, File dst) {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static String extensionFor(String mime, String url) {
        if (mime != null) {
            if (mime.contains("video")) return "mp4";
            if (mime.contains("png")) return "png";
            if (mime.contains("webp")) return "webp";
            if (mime.contains("gif")) return "gif";
            if (mime.contains("image")) return "jpg";
        }
        if (url != null) {
            String clean = url.split("\\?")[0].toLowerCase();
            for (String ext : new String[]{".mp4", ".jpg", ".jpeg", ".webp", ".png", ".gif"}) {
                if (clean.endsWith(ext)) return ext.substring(1).replace("jpeg", "jpg");
            }
            if (url.contains(".mp4")) return "mp4";
        }
        return "jpg";
    }

    public static long totalBytes(File dir) {
        long total = 0;
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return 0;
        for (File f : files) if (f.isFile()) total += f.length();
        return total;
    }

    public static int fileCount(File dir) {
        File[] files = dir == null ? null : dir.listFiles();
        return files == null ? 0 : files.length;
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb);
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0);
    }
}
