package io.nyx.app.hook;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Long-press any image-like view to save it. Deliberately narrow: only
 * ImageViews that already resolve to a media URL are touched, so host
 * long-press behaviour on buttons, labels and reels is left intact.
 */
final class LongPressSave {

    private static final Map<View, Boolean> BOUND = new WeakHashMap<>();
    private static final int MAX_NODES = 1600;

    private LongPressSave() {}

    static void bind(Activity activity, ViewGroup root) {
        HookBridge.safeRun("long press save", () -> walk(activity, root, 0, new int[]{0}));
    }

    private static void walk(Activity activity, View view, int depth, int[] budget) {
        if (view == null || depth > 30 || budget[0] > MAX_NODES) return;
        budget[0]++;
        if (view instanceof ImageView && !BOUND.containsKey(view)) {
            String hint = view.getContentDescription() == null ? "" : view.getContentDescription().toString();
            boolean mediaHint = hint.contains("https://") || view.getTag() instanceof String;
            if (mediaHint) {
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
            for (int i = 0; i < group.getChildCount(); i++) {
                walk(activity, group.getChildAt(i), depth + 1, budget);
            }
        }
    }
}
