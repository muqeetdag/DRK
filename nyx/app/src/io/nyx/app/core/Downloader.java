package io.nyx.app.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Direct HTTP media fetch. Instagram CDN URLs are signed and short-lived, so
 * they must be pulled immediately on the same session — Android's
 * DownloadManager is too slow to start and re-downloads often 403. This pulls
 * the stream straight to a temp file, then publishes to MediaStore.
 */
public final class Downloader {

    public interface Callback {
        void onStart(String label);
        void onProgress(int percent);
        void onDone(boolean ok, String message);
    }

    public static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0 Mobile Safari/537.36";

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    private Downloader() {}

    public static void fetch(Context ctx, String url, String kind, Callback cb) {
        fetch(ctx, url, kind, MediaStoreX.folderName(ctx), cb);
    }

    public static void fetch(Context ctx, String url, String kind, String folder, Callback cb) {
        final Context app = ctx.getApplicationContext();
        POOL.execute(() -> {
            String label = kind + " " + SEQ.getAndIncrement();
            post(() -> {
                if (cb != null) cb.onStart(label);
            });
            boolean ok = false;
            String message;
            HttpURLConnection conn = null;
            File temp = null;
            try {
                URL parsed = new URL(url);
                String scheme = parsed.getProtocol();
                if (!"https".equalsIgnoreCase(scheme)) {
                    throw new IllegalStateException("refusing non-TLS media URL");
                }
                conn = (HttpURLConnection) parsed.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setRequestProperty("Referer", "https://www.instagram.com/");
                conn.setRequestProperty("Accept", "*/*");
                int code = conn.getResponseCode();
                if (code / 100 != 2) {
                    throw new IllegalStateException("HTTP " + code);
                }
                String mime = conn.getContentType();
                if (mime != null) mime = mime.split(";")[0].trim();
                long total = conn.getContentLengthLong();

                File dir = new File(app.getCacheDir(), "nyx-incoming");
                if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                String ext = MediaStoreX.extensionFor(mime, url);
                temp = new File(dir, "nyx-" + System.currentTimeMillis() + "." + ext);

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[64 * 1024];
                    long done = 0;
                    int read;
                    int lastPct = -1;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        done += read;
                        if (total > 0) {
                            int pct = (int) Math.min(99, done * 100 / total);
                            if (pct != lastPct) {
                                lastPct = pct;
                                final int fpct = pct;
                                post(() -> {
                                    if (cb != null) cb.onProgress(fpct);
                                });
                            }
                        }
                    }
                    out.flush();
                }

                String stamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                        .format(new java.util.Date());
                String display = "NYX_" + kind.replace(' ', '_') + "_" + stamp + "." + ext;
                android.net.Uri saved = MediaStoreX.publish(app, temp,
                        mime == null ? (ext.equals("mp4") ? "video/mp4" : "image/jpeg") : mime,
                        display, folder);
                ok = saved != null;
                message = ok ? "Saved " + display : "Save failed";
            } catch (Throwable t) {
                message = "Failed: " + shorten(t.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
                if (temp != null && temp.exists()) //noinspection ResultOfMethodCallIgnored
                    temp.delete();
            }
            final boolean fok = ok;
            final String fmsg = message;
            post(() -> {
                if (cb != null) cb.onDone(fok, fmsg);
            });
        });
    }

    public static void fetchQuiet(Context ctx, String url, String kind, Callback cb) {
        fetch(ctx, url, kind, cb);
    }

    /** Media downloads go straight into the gallery via MediaStore. */
    public static void copyToDownloads(Context ctx, File file, String mime) {
        if (file == null || !file.exists()) return;
        MediaStoreX.publish(ctx, file, mime, file.getName());
    }

    public static void toast(Context ctx, String text) {
        try {
            Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    public static String shorten(String message) {
        if (message == null) return "unknown error";
        return message.length() > 90 ? message.substring(0, 90) + "…" : message;
    }

    private static void post(Runnable r) {
        MAIN.post(r);
    }

}
