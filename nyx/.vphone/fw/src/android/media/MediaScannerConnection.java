package android.media;

import android.content.Context;

/** Media scanner no-op that records the scan request. */
public class MediaScannerConnection {

    public interface MediaScannerConnectionClient {
        void onMediaScannerConnected();
        void onScanCompleted(String path, android.net.Uri uri);
    }

    public static void scanFile(Context context, String[] paths, String[] mimeTypes,
                                MediaScannerConnectionClient callback) {
        if (paths == null) return;
        for (String path : paths) android.test.Services.LOG.add("scan: " + path);
        if (callback != null && paths.length > 0) {
            callback.onScanCompleted(paths[0], android.net.Uri.parse("file://" + paths[0]));
        }
    }
}
