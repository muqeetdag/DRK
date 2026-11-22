package io.nyx.app.hook;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Tiny diagnostics pill: hook state, palette, resolved URL snippet. */
final class DebugHud {

    private static TextView label;
    private static String lastUrl = "";

    private DebugHud() {}

    static void attach(Activity activity) {
        HookBridge.safeRun("debug hud", () -> {
            View decor = activity.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            ViewGroup root = (ViewGroup) decor;
            if (label == null) {
                float density = activity.getResources().getDisplayMetrics().density;
                label = new TextView(activity);
                label.setTextSize(8.5f);
                label.setPadding((int) (8 * density), (int) (4 * density),
                        (int) (8 * density), (int) (4 * density));
                label.setGravity(Gravity.CENTER_VERTICAL);
                label.setAlpha(0.85f);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.topMargin = (int) (42 * density);
                lp.rightMargin = (int) (10 * density);
                label.setLayoutParams(lp);
            }
            if (label.getParent() != null) ((ViewGroup) label.getParent()).removeView(label);
            ThemesMirror.Palette theme = ThemesMirror.resolve(activity);
            label.setTextColor(theme.secondary);
            label.setBackgroundColor(ThemesMirror.alpha(theme.bg, 210));
            label.setText(describe(theme));
            root.addView(label);
        });
    }

    private static String describe(ThemesMirror.Palette theme) {
        int armed = 0;
        String[] keys = {"floating_nav", "glass_header", "reels_downloader",
                "stories_downloader", "feed_downloader", "long_press_save"};
        for (String key : keys) if (PrefsReader.get(key, false)) armed++;
        String tail = lastUrl.isEmpty() ? "no media yet"
                : lastUrl.substring(Math.max(0, lastUrl.length() - 34));
        return "NYX ▸ " + theme.id + " · " + armed + " armed · fails " + HookBridge.failures()
                + "\n" + tail;
    }

    static void noteUrl(String url) {
        if (url != null && !url.isEmpty()) {
            lastUrl = url;
            if (label != null) label.post(() -> {
                try {
                    label.setText(label.getText().toString().split("\n")[0] + "\n"
                            + lastUrl.substring(Math.max(0, lastUrl.length() - 34)));
                } catch (Throwable ignored) {
                }
            });
        }
    }

    static void detach() {
        try {
            if (label != null && label.getParent() instanceof ViewGroup) {
                ((ViewGroup) label.getParent()).removeView(label);
            }
        } catch (Throwable ignored) {
        }
    }
}
