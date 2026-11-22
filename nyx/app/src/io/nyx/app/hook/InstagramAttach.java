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
 * Instagram attach layer — deliberately conservative.
 *
 * Crash-safety rules learned the hard way:
 *  • never hook methods called thousands of times per second
 *    (a global View#onAttachedToWindow hook makes Instagram crawl and ANR),
 *  • never walk the whole view tree on the UI thread,
 *  • do all host work on a single throttled message, not per view.
 *
 * So we hook only Activity lifecycle (a handful of calls per screen), do one
 * depth-limited scan after the screen settles, and wrap every entry point.
 */
final class InstagramAttach {

    private static final String PACKAGE = "com.instagram.android";

    /** Only these host screens get decorated. */
    private static final String[] TABBAR_KEYWORDS = {
            "tabbar", "bottomnav", "tabhost", "mainnavigation", "bottombar"
    };
    private static final String[] FEED_KEYWORDS = {
            "mediaactionsview", "rowfeedmediaaction", "mediafeedback", "feedactions"
    };
    private static final String[] REELS_KEYWORDS = {"clipsvieweraction", "clipsviewer"};
    private static final String[] STORY_KEYWORDS = {"reelviewer", "storyviewer", "reelheader"};

    private static final Map<String, FloatingDock> DOCKS = new WeakHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean started = false;
    private static long lastDecorate = 0L;

    private final ClassLoader loader;

    InstagramAttach(ClassLoader loader) {
        this.loader = loader;
    }

    void start() {
        if (started) return;
        started = true;
        hookActivity();
        armFeatureEngines();
        HookBridge.log("attach layer armed (activity-scoped)");
    }

    /**
     * Installs every feature engine. Each one is independent and wrapped, so a
     * failed install costs that single feature - never the app. Order matters
     * only for readability; nothing here depends on anything else having armed.
     */
    private void armFeatureEngines() {
        HookBridge.safeRun("ghost mode", () -> GhostMode.install(loader));
        HookBridge.safeRun("feed cleaner", () -> FeedCleaner.install(loader));
        HookBridge.safeRun("media tools", () -> MediaTools.install(loader));
        HookBridge.safeRun("app lock", () -> AppLock.install(loader));
        HookBridge.safeRun("message tools", () -> MessageTools.install(loader));
        HookBridge.safeRun("playback tools", () -> PlaybackTools.install(loader));
        HookBridge.safeRun("story tools", () -> StoryTools.install(loader));
        HookBridge.safeRun("misc tools", () -> MiscTools.install(loader));
        HookBridge.log("feature engines armed (failures so far: "
                + HookBridge.failures() + ")");
    }

    // ------------------------------------------------------- activity layer
    private void hookActivity() {
        HookBridge.safeRun("hook Activity#onCreate", () -> XposedHelpers.findAndHookMethod(
                Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        if (!isHost(activity)) return;
                        MAIN.postDelayed(() -> decorate(activity), 900);
                    }
                }));

        HookBridge.safeRun("hook Activity#onResume", () -> XposedHelpers.findAndHookMethod(
                Activity.class, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        if (!isHost(activity)) return;
                        MAIN.postDelayed(() -> decorate(activity), 700);
                    }
                }));

        HookBridge.safeRun("hook Activity#onDestroy", () -> XposedHelpers.findAndHookMethod(
                Activity.class, "onDestroy", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        if (!isHost(activity)) return;
                        FloatingDock dock = DOCKS.remove(key(activity));
                        if (dock != null) dock.destroy();
                    }
                }));
    }

    private boolean isHost(Activity activity) {
        try {
            return activity != null && activity.getClass().getName().startsWith(PACKAGE);
        } catch (Throwable t) {
            return false;
        }
    }

    private String key(Activity activity) {
        return activity.getClass().getName();
    }

    private void decorate(Activity activity) {
        if (!HookBridge.alive()) return;
        long now = System.currentTimeMillis();
        if (now - lastDecorate < 450) return; // debounce rapid resume/create storms
        lastDecorate = now;
        HookBridge.safeRun("decorate", () -> {
            if (activity.isFinishing()) return;
            View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            ViewGroup root = (ViewGroup) decor;
            ThemesMirror.Palette theme = ThemesMirror.resolve(activity);

            String hostScreen = screenOf(root);

            FloatingDock dock = DOCKS.get(key(activity));
            boolean wantDock = PrefsReader.get("floating_nav", true) && hostScreen != null;
            if (wantDock) {
                if (dock == null) {
                    dock = new FloatingDock(activity, theme);
                    DOCKS.put(key(activity), dock);
                }
                dock.attach(root);
                dock.bindScrollRoot(findScrollRoot(root));
            } else if (dock != null) {
                dock.destroy();
                DOCKS.remove(key(activity));
            }

            if (PrefsReader.get("glass_header", true)) {
                MediaKeeperButton.glassHeader(activity, root, theme);
            } else {
                View sheen = root.findViewWithTag(Ids.TAG_HEADER_SHEEN);
                if (sheen != null) root.removeView(sheen);
            }

            if (hostScreen != null && PrefsReader.get("long_press_save", true)) {
                LongPressSave.bind(activity, root);
            }

            if (hostScreen != null && PrefsReader.get("show_keeper_button", true)) {
                injectKeeper(activity, root, hostScreen, theme);
            }

            if (PrefsReader.get("debug_hud", false)) {
                DebugHud.attach(activity);
            }
        });
    }

    /** Which of the screens we know is on top. Null means "leave it alone". */
    private String screenOf(View root) {
        String tabs = findClassName(root, TABBAR_KEYWORDS, 0);
        if (tabs != null) return "tabs";
        if (findClassName(root, REELS_KEYWORDS, 0) != null) return "reels";
        if (findClassName(root, STORY_KEYWORDS, 0) != null) return "story";
        if (findClassName(root, FEED_KEYWORDS, 0) != null) return "feed";
        return null;
    }

    /** Depth- and budget-limited search — cheap enough for the UI thread once. */
    private String findClassName(View view, String[] keywords, int depth) {
        if (view == null || depth > 14) return null;
        String name;
        try {
            name = view.getClass().getName().toLowerCase();
        } catch (Throwable t) {
            return null;
        }
        for (String keyword : keywords) if (name.contains(keyword)) return name;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = Math.min(group.getChildCount(), 24);
            for (int i = 0; i < count; i++) {
                String found = findClassName(group.getChildAt(i), keywords, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private View findScrollRoot(View root) {
        try {
            String name = findClassName(root, new String[]{"recyclerview", "clipsviewer"}, 0);
            if (name == null) return null;
            return findFirst(root, name);
        } catch (Throwable t) {
            return null;
        }
    }

    private View findFirst(View view, String className) {
        if (view == null) return null;
        if (view.getClass().getName().equalsIgnoreCase(className)
                || view.getClass().getName().toLowerCase().contains(className)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findFirst(group.getChildAt(i), className);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void injectKeeper(Activity activity, ViewGroup root, String screen, ThemesMirror.Palette theme) {
        HookBridge.safeRun("inject keeper", () -> {
            String kind;
            String prefKey;
            if ("reels".equals(screen)) {
                kind = "Reel";
                prefKey = "reels_downloader";
            } else if ("story".equals(screen)) {
                kind = "Story";
                prefKey = "stories_downloader";
            } else if ("feed".equals(screen)) {
                kind = "Post";
                prefKey = "feed_downloader";
            } else {
                return;
            }
            if (!PrefsReader.get(prefKey, true)) return;
            LongPressSave.installKeeper(activity, root, kind, theme,
                    PrefsReader.get("download_folder", "NYX"));
        });
    }
}
