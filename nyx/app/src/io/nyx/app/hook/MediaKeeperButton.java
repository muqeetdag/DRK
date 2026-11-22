package io.nyx.app.hook;

import io.nyx.app.core.SafeUi;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.nyx.app.R;
import io.nyx.app.core.Downloader;

/**
 * The media keeper button. Inserted beside the like / share actions of the
 * host screen. The media URL is resolved from the live view tree, then pulled
 * immediately (signed CDN URLs expire).
 */
final class MediaKeeperButton {

    private final Activity activity;
    private final ThemesMirror.Palette theme;
    private final String folder;

    MediaKeeperButton(Activity activity, ThemesMirror.Palette theme, String folder) {
        this.activity = activity;
        this.theme = theme;
        this.folder = folder;
    }

    void inject(final ViewGroup host, final String kind) {
        HookBridge.safeRun("keeper inject", () -> {
            if (host.findViewWithTag(Ids.TAG_KEEPER) != null) return;
            Context ctx = host.getContext();
            float density = ctx.getResources().getDisplayMetrics().density;
            int size = (int) (46 * density);
            ImageView button = new ImageView(ctx);
            button.setTag(Ids.TAG_KEEPER);
            button.setImageResource(R.drawable.ic_download);
            button.setColorFilter(theme.text);
            button.setPadding(size / 4, size / 4, size / 4, size / 4);
            button.setContentDescription("Save " + kind);

            GradientDrawable bubble = new GradientDrawable();
            bubble.setShape(GradientDrawable.OVAL);
            bubble.setColor(ThemesMirror.alpha(theme.surfaceAlt, 215));
            bubble.setStroke((int) density, theme.line);
            button.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(ThemesMirror.alpha(theme.accent, 70)), bubble, null));
            SafeUi.elevation(button, size * 0.12f);

            ViewGroup.LayoutParams lp;
            if (host instanceof LinearLayout) {
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
                int margins = size / 8;
                if (((LinearLayout) host).getOrientation() == LinearLayout.HORIZONTAL) {
                    p.leftMargin = margins;
                } else {
                    p.topMargin = margins;
                }
                lp = p;
            } else {
                lp = new ViewGroup.LayoutParams(size, size);
            }
            button.setLayoutParams(lp);
            button.setOnClickListener(v -> capture(kind, v, sourceUrl));
            host.addView(button);
            HookBridge.log("keeper injected (" + kind + ") into " + host.getClass().getName());
        });
    }

    private String sourceUrl = null;

    void remember(String url) {
        if (!TextUtils.isEmpty(url)) sourceUrl = url;
    }

    static void capture(Activity activity, String kind, View source, String remembered) {
        try {
            String url = remembered;
            if (TextUtils.isEmpty(url)) url = UrlResolver.resolve(source.getRootView());
            if (TextUtils.isEmpty(url)) {
                toast(activity, "This " + kind + " has no resolvable media URL yet");
                return;
            }
            String folder = PrefsReader.get("download_folder", "NYX");
            Downloader.fetch(activity, url, kind, folder, new Downloader.Callback() {
                @Override
                public void onStart(String label) {
                    toast(activity, "Saving " + kind + "…");
                }

                @Override
                public void onProgress(int percent) {
                }

                @Override
                public void onDone(boolean ok, String message) {
                    toast(activity, message);
                }
            });
        } catch (Throwable t) {
            HookBridge.warn("keeper capture", t);
        }
    }

    private void capture(String kind, View source, String remembered) {
        source.animate().scaleX(0.82f).scaleY(0.82f).setDuration(90)
                .withEndAction(() -> source.animate().scaleX(1f).scaleY(1f)
                        .setDuration(140).start()).start();
        capture(activity, kind, source, remembered);
    }

    static void toast(Activity activity, String message) {
        try {
            android.widget.Toast toast = android.widget.Toast.makeText(activity, message,
                    android.widget.Toast.LENGTH_SHORT);
            View view = toast.getView();
            if (view != null) {
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(28 * activity.getResources().getDisplayMetrics().density);
                bg.setColor(0xF2111320);
                bg.setStroke(2, ThemesMirror.resolve(activity).line);
                view.setBackground(bg);
                view.setPadding(28, 18, 28, 18);
            }
            toast.show();
        } catch (Throwable ignored) {
        }
    }

    /** Slim themed sheen at the top of a screen; purely visual, always safe. */
    static void glassHeader(Activity activity, ViewGroup root, ThemesMirror.Palette theme) {
        HookBridge.safeRun("glass header", () -> {
            if (root.findViewWithTag(Ids.TAG_HEADER_SHEEN) != null) return;
            float density = activity.getResources().getDisplayMetrics().density;
            LinearLayout bar = new LinearLayout(activity);
            bar.setTag(Ids.TAG_HEADER_SHEEN);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{ThemesMirror.alpha(theme.bg, 210), ThemesMirror.alpha(theme.bg, 80),
                            0x00000000});
            bar.setBackground(g);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = new TextView(activity);
            label.setText(io.nyx.app.core.Brand.NAME.toUpperCase());
            label.setTextSize(8f);
            SafeUi.letterSpacing(label, 0.24f);
            label.setTextColor(ThemesMirror.alpha(theme.text, 140));
            label.setPadding((int) (14 * density), 0, 0, 0);
            bar.addView(label);
            root.addView(bar, new ViewGroup.LayoutParams(-1, (int) (20 * density)));
        });
    }
}
