package io.nyx.app.hook;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * Turns "Song — Artist" into streaming links.
 *
 * We do not scrape Spotify: we hand the query to whichever music app is
 * installed through a normal search intent, so nothing needs a token and nothing
 * breaks when a provider changes its URL scheme.
 */
final class MusicLinks {

    /** Last track title seen by the hook; also used by the console's media page. */
    static volatile String lastTrack;

    private MusicLinks() {}

    static void open(Context context, String track) {
        if (context == null || track == null || track.trim().isEmpty()) return;
        String query = track.trim();
        // 1) Spotify search, 2) any music app, 3) YouTube, 4) browser.
        if (tryOpen(context, "com.spotify.music",
                "https://open.spotify.com/search/" + Uri.encode(query))) return;
        if (tryOpen(context, null, "https://music.youtube.com/search?q=" + Uri.encode(query))) return;
        tryOpen(context, null, "https://www.youtube.com/results?search_query=" + Uri.encode(query));
    }

    private static boolean tryOpen(Context context, String pkg, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (pkg != null) intent.setPackage(pkg);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
