package io.nyx.app.hook;

import io.nyx.app.core.SafeUi;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Floating dock: a detached translucent pill rendered above the host activity.
 *
 *  • tabs   → drive whichever ViewPager-ish widget the host uses
 *  • CORE   → save the media currently on screen (or offer share-sheet fallback)
 *  • badge  → open the companion console
 *
 * Nothing here depends on obfuscated class names; navigation is discovered at
 * runtime and simply no-ops when the host layout is not recognised.
 */
final class FloatingDock implements View.OnClickListener {

    private static final String[] GLYPHS = {"⌂", "⌕", "＋", "▶", "◍"};
    private static final String[] LABELS = {"HOME", "DISCOVER", "SAVE", "REELS", "YOU"};

    private final Activity activity;
    private final ThemesMirror.Palette theme;
    private final float density;
    private FrameLayout overlay;
    private LinearLayout items;
    private int current = 0;
    private int lastScrollY = 0;
    private boolean hiddenByZen = false;

    FloatingDock(Activity activity, ThemesMirror.Palette theme) {
        this.activity = activity;
        this.theme = theme;
        this.density = activity.getResources().getDisplayMetrics().density;
    }

    void attach(ViewGroup decor) {
        try {
            if (overlay != null && overlay.getParent() != null) {
                refresh();
                return;
            }
            if (decor.findViewWithTag(Ids.TAG_DOCK) != null) return;
            overlay = new FrameLayout(activity);
            overlay.setTag(Ids.TAG_DOCK);
            SafeUi.elevation(overlay, dp(10));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
            lp.gravity = Gravity.BOTTOM;
            lp.bottomMargin = dp(16);
            lp.leftMargin = dp(10);
            lp.rightMargin = dp(10);
            overlay.addView(buildPill(), new FrameLayout.LayoutParams(-1, -2));
            decor.addView(overlay, lp);
            overlay.setAlpha(0f);
            overlay.setTranslationY(dp(24));
            overlay.animate().alpha(1f).translationY(0f).setDuration(280).start();
            HookBridge.log("dock attached");
        } catch (Throwable t) {
            HookBridge.warn("dock attach", t);
        }
    }

    private void refresh() {
        HookBridge.safeRun("dock refresh", () -> {
            if (overlay.getAlpha() < 0.5f) {
                overlay.animate().alpha(1f).translationY(0f).setDuration(160).start();
            }
        });
    }

    private View buildPill() {
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setGravity(Gravity.CENTER_VERTICAL);
        shell.setPadding(dp(8), dp(7), dp(8), dp(7));
        shell.setBackground(new DockBackground(theme, dp(30)));

        items = new LinearLayout(activity);
        items.setOrientation(LinearLayout.HORIZONTAL);
        items.setGravity(Gravity.CENTER_VERTICAL);
        shell.addView(items, new LinearLayout.LayoutParams(0, -2, 1f));

        for (int i = 0; i < LABELS.length; i++) {
            boolean central = i == 2;
            TextView item = new TextView(activity);
            item.setGravity(Gravity.CENTER);
            item.setText(central ? GLYPHS[i] : GLYPHS[i] + "\n" + LABELS[i]);
            item.setTextSize(central ? 21f : 8.5f);
            item.setTextColor(central ? theme.bg : (i == current ? theme.accent : theme.muted));
            item.setLineSpacing(dp(2), 1f);
            SafeUi.typeface(item, "sans-serif-medium", Typeface.NORMAL);
            item.setTag(i);
            item.setOnClickListener(this);
            if (central) {
                GradientDrawable core = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{theme.accent, theme.secondary});
                core.setShape(GradientDrawable.OVAL);
                item.setBackground(new RippleDrawable(
                        ColorStateList.valueOf(ThemesMirror.alpha(theme.bg, 70)), core, null));
                items.addView(item, new LinearLayout.LayoutParams(dp(52), dp(52)));
            } else {
                item.setBackground(null);
                items.addView(item, new LinearLayout.LayoutParams(0, dp(44), 1f));
            }
        }

        TextView badge = new TextView(activity);
        badge.setText("●");
        badge.setTextSize(9f);
        badge.setTextColor(theme.secondary);
        badge.setGravity(Gravity.CENTER);
        badge.setAlpha(0.85f);
        badge.setPadding(dp(4), 0, dp(4), 0);
        badge.setOnClickListener(v -> openConsole());
        shell.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        return shell;
    }

    @Override
    public void onClick(View v) {
        Object tag = v.getTag();
        if (!(tag instanceof Integer)) return;
        int index = (Integer) tag;
        if (index == 2) {
            saveCurrent(v);
            return;
        }
        current = index;
        paintItems();
        navigateTab(index);
    }

    private void paintItems() {
        if (items == null) return;
        for (int i = 0; i < items.getChildCount(); i++) {
            View child = items.getChildAt(i);
            Object tag = child.getTag();
            if (!(tag instanceof Integer) || (Integer) tag == 2) continue;
            TextView label = (TextView) child;
            label.setTextColor(((Integer) tag) == current ? theme.accent : theme.muted);
        }
    }

    /** Central action: grab the media on screen, else open the share sheet. */
    private void saveCurrent(View core) {
        core.animate().scaleX(0.86f).scaleY(0.86f).setDuration(90)
                .withEndAction(() -> core.animate().scaleX(1f).scaleY(1f).setDuration(140).start())
                .start();
        View decor = activity.getWindow().getDecorView();
        String url = UrlResolver.resolve(decor);
        if (url != null) {
            DebugHud.noteUrl(url);
            MediaKeeperButton.capture(activity, "Media", core, url);
            return;
        }
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("*/*");
            Uri fallback = Uri.parse("https://www.instagram.com/");
            share.putExtra(Intent.EXTRA_TEXT, fallback.toString());
            activity.startActivity(Intent.createChooser(share, "Save media"));
        } catch (Throwable t) {
            MediaKeeperButton.toast(activity, "Nothing to save on this screen");
        }
    }

    private void openConsole() {
        try {
            Intent intent = activity.getPackageManager().getLaunchIntentForPackage("io.nyx.app");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            }
        } catch (Throwable t) {
            HookBridge.warn("open console", t);
        }
    }

    /** Locate a pager on screen and set it to the requested tab index. */
    private void navigateTab(int index) {
        HookBridge.safeRun("dock navigate", () -> {
            ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            View pager = findPager(decor, 0);
            if (pager == null) {
                HookBridge.log("no pager found for tab " + index);
                return;
            }
            for (String method : new String[]{"setCurrentItem", "setCurrentItemInternal",
                    "setCurrentTab"}) {
                try {
                    for (Method candidate : pager.getClass().getMethods()) {
                        if (candidate.getName().equals(method)
                                && candidate.getParameterTypes().length >= 1
                                && candidate.getParameterTypes()[0] == int.class) {
                            candidate.setAccessible(true);
                            candidate.invoke(pager, Math.min(index, 4));
                            HookBridge.log("tab → " + index + " via " + method);
                            return;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private View findPager(View view, int depth) {
        if (view == null || depth > 30) return null;
        String name = view.getClass().getName().toLowerCase();
        boolean pagerish = name.contains("viewpager") || name.contains("pager")
                || name.contains("tabhost");
        if (pagerish) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findPager(group.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    void onScroll(int y) {
        int delta = y - lastScrollY;
        lastScrollY = y;
        if (overlay == null) return;
        if (!PrefsReader.get("zen_scroll", false)) {
            if (hiddenByZen) {
                hiddenByZen = false;
                overlay.animate().translationY(0f).alpha(1f).setDuration(180).start();
            }
            return;
        }
        if (delta > dp(14) && !hiddenByZen) {
            hiddenByZen = true;
            overlay.animate().translationY(dp(110)).alpha(0.3f).setDuration(180).start();
        } else if (delta < -dp(6) && hiddenByZen) {
            hiddenByZen = false;
            overlay.animate().translationY(0f).alpha(1f).setDuration(200).start();
        }
    }

    void destroy() {
        try {
            scrollRoot = null;
            if (overlay != null) {
                if (overlay.getParent() instanceof ViewGroup) {
                    ((ViewGroup) overlay.getParent()).removeView(overlay);
                }
                overlay.removeCallbacks(null);
                overlay.animate().cancel();
                overlay.setOnScrollChangeListener(null);
            }
        } catch (Throwable ignored) {
        }
        overlay = null;
        items = null;
    }

    /**
     * Zen scroll without hooking View#onScrollChanged globally: listen on the
     * one scroll container we already found. No listener, no cost.
     */
    private View scrollRoot;

    void bindScrollRoot(View view) {
        HookBridge.safeRun("bind scroll", () -> {
            if (view == scrollRoot) return;
            if (scrollRoot != null && android.os.Build.VERSION.SDK_INT >= 23) {
                scrollRoot.setOnScrollChangeListener(null);
            }
            scrollRoot = view;
            if (scrollRoot != null && android.os.Build.VERSION.SDK_INT >= 23) {
                scrollRoot.setOnScrollChangeListener(
                        (v, x, y, oldX, oldY) -> onScroll(y));
            }
        });
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    /** Translucent fill, hairline stroke and a slow travelling accent bloom. */
    private static final class DockBackground extends GradientDrawable {
        private final ThemesMirror.Palette palette;
        private final Paint bloom = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int radius;
        private final long start = System.currentTimeMillis();

        DockBackground(ThemesMirror.Palette palette, int radius) {
            super();
            this.palette = palette;
            this.radius = radius;
            setShape(GradientDrawable.RECTANGLE);
            setCornerRadius(radius);
            setColor(ThemesMirror.alpha(palette.surface, 236));
            setCornerRadius(Math.max(1, radius));
            setStroke(Math.max(1, Math.max(1, radius) / 26), palette.line);
        }

        private long lastFrame = 0L;

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            if (!palette.glow) return;
            // ~30 fps cap: a background drawable must never drive a 60 fps loop
            long now = System.currentTimeMillis();
            if (now - lastFrame < 33L) return;
            lastFrame = now;
            float w = getBounds().width();
            float h = getBounds().height();
            if (w <= 0f || h <= 0f) return; // never build a zero-radius gradient
            float phase = (now - start) % 6000L / 6000f;
            float cx = w * (0.12f + 0.76f * phase);
            bloom.setShader(new RadialGradient(cx, h * 0.55f, Math.max(w, h) * 0.42f,
                    new int[]{ThemesMirror.alpha(palette.accent, 44), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(0, 0, w, h, radius, radius, bloom);
            if (isVisible()) invalidateSelf();
        }
    }
}
