package io.nyx.app.hook;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Two media affordances, both bounded:
 *   • long-press an image that already carries a media URL,
 *   • one keeper button per media action row (found by class name).
 *
 * Hard limits: depth 18, 220 nodes, and only ImageViews/known containers are
 * touched, so the scan finishes in a few milliseconds and never rewrites the
 * host's own long-press handlers on buttons or text.
 */
final class LongPressSave {

    private static final Map<View, Boolean> BOUND = new WeakHashMap<>();
    private static final int MAX_DEPTH = 18;
    private static final int MAX_NODES = 220;

    private LongPressSave() {}

    static void bind(Activity activity, ViewGroup root) {
        HookBridge.safeRun("long press save", () -> walk(activity, root, 0, new int[]{0}));
    }

    private static void walk(Activity activity, View view, int depth, int[] budget) {
        if (view == null || depth > MAX_DEPTH || budget[0] > MAX_NODES) return;
        budget[0]++;
        if (view instanceof ImageView && !BOUND.containsKey(view)) {
            String hint = view.getContentDescription() == null
                    ? "" : view.getContentDescription().toString();
            if (hint.contains("https://")) {
                BOUND.put(view, Boolean.TRUE);
                view.setOnLongClickListener(v -> {
                    if (!PrefsReader.get("long_press_save", true)) return false;
                    String url = UrlResolver.resolve(v);
                    if (url == null) return false;
                    DebugHud.noteUrl(url);
                    MediaKeeperButton.capture(activity, "Media", v, url);
                    return true;
                });
            }
        }
        if (view instanceof ViewGroup && !(view instanceof TextView)) {
            ViewGroup group = (ViewGroup) view;
            int count = Math.min(group.getChildCount(), 40);
            for (int i = 0; i < count; i++) {
                walk(activity, group.getChildAt(i), depth + 1, budget);
            }
        }
    }

    /** Finds the first media-action container and asks it to host the button. */
    static void installKeeper(Activity activity, ViewGroup root, String kind,
                              ThemesMirror.Palette theme, String folder) {
        HookBridge.safeRun("install keeper", () -> {
            ViewGroup host = findActionRow(root, 0);
            if (host == null) return;
            if (host.findViewWithTag(Ids.TAG_KEEPER) != null) return;
            new MediaKeeperButton(activity, theme, folder).inject(host, kind);
        });
    }

    private static ViewGroup findActionRow(View view, int depth) {
        if (view == null || depth > 16) return null;
        String name = view.getClass().getName().toLowerCase();
        boolean looksLikeActions = name.contains("mediaactionsview")
                || name.contains("rowfeedmediaaction")
                || name.contains("mediafeedback")
                || name.contains("clipsvieweraction")
                || name.contains("feedactions");
        if (looksLikeActions && view instanceof ViewGroup) return (ViewGroup) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = Math.min(group.getChildCount(), 30);
            for (int i = 0; i < count; i++) {
                ViewGroup found = findActionRow(group.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }
}
