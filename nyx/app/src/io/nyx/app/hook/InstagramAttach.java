package io.nyx.app.hook;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Instagram attach layer.
 *
 * Hooks framework seams Instagram cannot obfuscate:
 *   • Activity#onCreate / #onResume  → dock, sheen, long-press, HUD
 *   • View#onAttachedToWindow        → media keeper buttons in action rows
 *   • View#onScrollChanged           → Zen auto-hide
 *
 * All hooks are guarded; a failure is logged and the feature degrades instead
 * of taking the host down. Media-only buttons are removed again if the user
 * switches them off, so toggles stay honest.
 */
final class InstagramAttach {

    private static final String PACKAGE = "com.instagram.android";

    private static final String[] TABBAR_KEYWORDS = {
            "tabbar", "bottomnav", "tabhost", "mainnavigation", "bottombar", "tablayout"
    };
    private static final String[] REELS_KEYWORDS = {
            "clipsvieweraction", "clipstable", "reelsaction", "clipsinteraction", "reelsviewer",
            "clipsviewer"
    };
    private static final String[] STORY_KEYWORDS = {
            "reelviewer", "storyviewer", "reelheader", "storyheader", "stories"
    };
    private static final String[] FEED_KEYWORDS = {
            "mediaactionsview", "rowfeedmediaaction", "mediafeedback", "feedactions", "carousel"
    };
    private static final String[] SCROLL_KEYWORDS = {
            "recyclerview", "clipsviewer", "feed", "scrollview"
    };

    private static final Map<View, Boolean> TOUCHED = new WeakHashMap<>();
    private static final Map<String, FloatingDock> DOCKS = new WeakHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean started = false;

    private final ClassLoader loader;

    InstagramAttach(ClassLoader loader) {
        this.loader = loader;
    }

    void start() {
        if (started) return;
        started = true;
        hookActivity();
        hookViewAttach();
        hookScroll();
        HookBridge.log("attach layer armed for " + PACKAGE);
    }

    // ------------------------------------------------------- activity layer
    private void hookActivity() {
        HookBridge.safeRun("hook Activity#onCreate", () -> XposedHelpers.findAndHookMethod(
                Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        if (activity == null) return;
                        if (!activity.getClass().getName().startsWith(PACKAGE)) return;
                        MAIN.postDelayed(() -> decorate(activity), 340);
                    }
                }));

        HookBridge.safeRun("hook Activity#onResume", () -> XposedHelpers.findAndHookMethod(
                Activity.class, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        if (activity == null) return;
                        if (!activity.getClass().getName().startsWith(PACKAGE)) return;
                        MAIN.postDelayed(() -> decorate(activity), 260);
                    }
                }));

        HookBridge.safeRun("hook Activity#onDestroy", () -> XposedHelpers.findAndHookMethod(
                Activity.class, "onDestroy", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        if (activity == null) return;
                        if (!activity.getClass().getName().startsWith(PACKAGE)) return;
                        FloatingDock dock = DOCKS.remove(activity.getClass().getName());
                        if (dock != null) dock.destroy();
                    }
                }));
    }

    private void decorate(Activity activity) {
        HookBridge.safeRun("decorate(" + activity.getClass().getSimpleName() + ")", () -> {
            if (activity.isFinishing()) return;
            View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            ViewGroup root = (ViewGroup) decor;
            ThemesMirror.Palette theme = ThemesMirror.resolve(activity);

            FloatingDock dock = DOCKS.get(activity.getClass().getName());
            boolean wantDock = PrefsReader.get("floating_nav", true);
            if (wantDock) {
                if (dock == null) {
                    dock = new FloatingDock(activity, theme);
                    DOCKS.put(activity.getClass().getName(), dock);
                }
                dock.attach(root);
            } else if (dock != null) {
                dock.destroy();
                DOCKS.remove(activity.getClass().getName());
            }

            if (PrefsReader.get("glass_header", true)) {
                MediaKeeperButton.glassHeader(activity, root, theme);
            } else {
                View sheen = root.findViewWithTag(Ids.TAG_HEADER_SHEEN);
                if (sheen != null) root.removeView(sheen);
            }

            if (PrefsReader.get("long_press_save", true)) {
                LongPressSave.bind(activity, root);
            }

            if (PrefsReader.get("debug_hud", false)) {
                DebugHud.attach(activity);
            }
        });
    }

    // ----------------------------------------------------------- view layer
    private void hookViewAttach() {
        HookBridge.safeRun("hook View#onAttachedToWindow", () -> XposedHelpers.findAndHookMethod(
                View.class, "onAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!HookBridge.alive()) return;
                        View view = (View) param.thisObject;
                        if (!(view instanceof ViewGroup)) return;
                        String className = view.getClass().getName().toLowerCase();
                        boolean feed = matches(className, FEED_KEYWORDS);
                        boolean reels = matches(className, REELS_KEYWORDS);
                        boolean story = matches(className, STORY_KEYWORDS);
                        boolean tabbar = matches(className, TABBAR_KEYWORDS);
                        if (!feed && !reels && !story && !tabbar) return;
                        ViewGroup group = (ViewGroup) view;
                        if (TOUCHED.containsKey(group)) return;
                        TOUCHED.put(group, Boolean.TRUE);
                        MAIN.postDelayed(() -> injectKeeper(group, feed, reels, story), 400);
                    }
                }));
    }

    private void injectKeeper(ViewGroup group, boolean feed, boolean reels, boolean story) {
        HookBridge.safeRun("inject keeper", () -> {
            if (group.getChildCount() == 0) return;
            android.content.Context ctx = group.getContext();
            if (!(ctx instanceof Activity)) return;
            Activity activity = (Activity) ctx;

            String kind;
            String prefKey;
            if (reels) {
                kind = "Reel";
                prefKey = "reels_downloader";
            } else if (story) {
                kind = "Story";
                prefKey = "stories_downloader";
            } else if (feed) {
                kind = "Post";
                prefKey = "feed_downloader";
            } else {
                return;
            }
            View existing = group.findViewWithTag(Ids.TAG_KEEPER);
            boolean want = PrefsReader.get(prefKey, true);
            if (!want) {
                if (existing != null) group.removeView(existing);
                return;
            }
            if (existing != null) return;
            ThemesMirror.Palette theme = ThemesMirror.resolve(activity);
            new MediaKeeperButton(activity, theme,
                    PrefsReader.get("download_folder", "NYX")).inject(group, kind);
        });
    }

    // --------------------------------------------------------- scroll layer
    private void hookScroll() {
        HookBridge.safeRun("hook View#onScrollChanged", () -> XposedHelpers.findAndHookMethod(
                View.class, "onScrollChanged", int.class, int.class, int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!HookBridge.alive()) return;
                        if (!PrefsReader.get("zen_scroll", false)) return;
                        View view = (View) param.thisObject;
                        if (view == null) return;
                        String name = view.getClass().getName().toLowerCase();
                        if (!matches(name, SCROLL_KEYWORDS)) return;
                        int y = (Integer) param.args[1];
                        for (FloatingDock dock : DOCKS.values()) dock.onScroll(y);
                    }
                }));
    }

    private static boolean matches(String className, String[] keywords) {
        for (String keyword : keywords) if (className.contains(keyword)) return true;
        return false;
    }
}
