package io.nyx.app.hook;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * De-clutters Instagram: ads, suggestions, Meta AI, upsells, and the ability to
 * switch off whole sections or individual bottom tabs.
 *
 * Two mechanisms, both cheap and both reversible:
 *  1. Boolean plumbing — hook the "should I show this?" methods and answer false.
 *     No view is created at all, so this is the fastest path and never flickers.
 *  2. One throttled view sweep per screen — for the units that are already
 *     inflated before any flag is consulted. It runs at most once per resume,
 *     walks a bounded depth, and only touches views whose text matches a known
 *     label. This is the same budget InstaEclipse uses; hooking onDraw or
 *     per-view attach would ANR.
 */
final class FeedCleaner {

    private static final String[] AD_LABELS = {
            "sponsored", "paid partnership", "advertisement"
    };
    private static final String[] SUGGEST_LABELS = {
            "suggested for you", "suggested posts", "suggested reels",
            "because you follow", "you might like", "discover people",
            "people you may know"
    };
    private static final String[] THREADS_LABELS = {"threads"};
    private static final String[] META_AI_LABELS = {"meta ai", "ask meta ai", "imagine"};
    private static final String[] PLUS_LABELS = {
            "instagram plus", "try plus", "upgrade to plus"
    };
    private static final String[] ONBOARD_LABELS = {
            "turn on notifications", "find friends", "add profile photo",
            "complete your profile", "connect contacts", "allow location",
            "set up on new device"
    };

    private FeedCleaner() {}

    static void install(ClassLoader loader) {
        boolean any = anyToggleOn();
        hookAdFlags(loader);
        hookSuggestionFlags(loader);
        if (any) hookSweep(loader);
        hookTabVisibility(loader);
        hookSectionGates(loader);
    }

    private static boolean anyToggleOn() {
        return PrefsReader.get(PrefsKeys.BLOCK_ADS, false)
                || PrefsReader.get(PrefsKeys.HIDE_SUGGESTIONS, false)
                || PrefsReader.get(PrefsKeys.HIDE_THREADS_SUGGEST, false)
                || PrefsReader.get(PrefsKeys.REMOVE_META_AI, false)
                || PrefsReader.get(PrefsKeys.HIDE_PLUS_UPSELLS, false)
                || PrefsReader.get(PrefsKeys.HIDE_ONBOARDING_NAGS, false)
                || PrefsReader.get(PrefsKeys.HIDE_STORY_TRAY, false);
    }

    // ------------------------------------------------------- boolean plumbing
    private static void hookAdFlags(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.BLOCK_ADS, false)) return;
        String[] classes = {
                "com.instagram.feed.media.Media",
                "com.instagram.api.schemas.MediaDict",
                "com.instagram.feed.media.MediaImpl",
                "com.instagram.model.mediasize.ExtendedImageUrl",
        };
        for (String name : classes) {
            hookFalse(loader, PrefsKeys.BLOCK_ADS, name,
                    new String[]{"isAd", "isSponsored", "isPaidPartnership", "hasAd"});
        }
        // Analytics / telemetry: a global "should log" gate.
        if (PrefsReader.get(PrefsKeys.BLOCK_ANALYTICS, false)) {
            for (String name : new String[]{
                    "com.facebook.analytics2.logger.Analytics2Logger",
                    "com.instagram.analytics.InstagramAnalyticsLogger"}) {
                hookFalse(loader, PrefsKeys.BLOCK_ANALYTICS, name,
                        new String[]{"isEnabled", "shouldLog", "canLog"});
            }
        }
    }

    private static void hookSuggestionFlags(ClassLoader loader) {
        if (PrefsReader.get(PrefsKeys.HIDE_SUGGESTIONS, false)) {
            for (String name : new String[]{
                    "com.instagram.feed.media.Media",
                    "com.instagram.explore.model.ExploreItem"}) {
                hookFalse(loader, PrefsKeys.HIDE_SUGGESTIONS, name,
                        new String[]{"isSuggested", "isSuggestion", "isRecommended"});
            }
        }
        if (PrefsReader.get(PrefsKeys.HIDE_THREADS_SUGGEST, false)) {
            hookFalse(loader, PrefsKeys.HIDE_THREADS_SUGGEST,
                    "com.instagram.feed.intf.FeedItem", new String[]{"isThreads"});
        }
        if (PrefsReader.get(PrefsKeys.REMOVE_META_AI, false)) {
            for (String name : new String[]{
                    "com.instagram.direct.ai.AiAgentManager",
                    "com.instagram.ai.AiSearchEntryPoint"}) {
                hookFalse(loader, PrefsKeys.REMOVE_META_AI, name,
                        new String[]{"isEnabled", "isAvailable", "shouldShow"});
            }
        }
    }

    // ---------------------------------------------------------- section gates
    private static void hookSectionGates(ClassLoader loader) {
        if (PrefsReader.get(PrefsKeys.DISABLE_REELS, false)) {
            // Reels tab + player refuse to start.
            for (String name : new String[]{
                    "com.instagram.clips.intf.ClipsViewerConfig",
                    "com.instagram.clips.model.ClipsItem"}) {
                hookFalse(loader, PrefsKeys.DISABLE_REELS, name,
                        new String[]{"canPlay", "isEnabled"});
            }
        }
        if (PrefsReader.get(PrefsKeys.VIDEO_AUTOPLAY_OFF, false)) {
            hookFalse(loader, PrefsKeys.VIDEO_AUTOPLAY_OFF,
                    "com.instagram.feed.media.Media", new String[]{"shouldAutoplay"});
        }
        if (PrefsReader.get(PrefsKeys.DISABLE_REPOST, false)) {
            hookFalse(loader, PrefsKeys.DISABLE_REPOST,
                    "com.instagram.feed.media.Media", new String[]{"canRepost", "isRepostable"});
        }
        if (PrefsReader.get(PrefsKeys.HIGH_RES_IMAGES, false)) {
            // Ask for the largest candidate instead of the screen-sized one.
            hookTrue(loader, PrefsKeys.HIGH_RES_IMAGES,
                    "com.instagram.model.mediasize.ExtendedImageUrl",
                    new String[]{"isBestCandidate"});
        }
    }

    // --------------------------------------------------------- bottom tab bar
    private static void hookTabVisibility(ClassLoader loader) {
        String[][] tabs = {
                {PrefsKeys.HIDE_TAB_HOME, "home"},
                {PrefsKeys.HIDE_TAB_REELS, "reels"},
                {PrefsKeys.HIDE_TAB_CREATE, "create"},
                {PrefsKeys.HIDE_TAB_DM, "direct"},
                {PrefsKeys.HIDE_TAB_ACTIVITY, "activity"},
                {PrefsKeys.HIDE_TAB_PROFILE, "profile"},
        };
        boolean wanted = false;
        for (String[] tab : tabs) wanted |= PrefsReader.get(tab[0], false);
        if (!wanted) return;

        try {
            XposedHelpers.findAndHookMethod(
                    Class.forName("android.app.Activity", false, loader),
                    "onResume", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                android.app.Activity activity = (android.app.Activity) param.thisObject;
                                if (activity == null || activity.getWindow() == null) return;
                                if (!isHost(activity)) return;
                                View root = activity.getWindow().getDecorView();
                                if (root == null) return;
                                hideTabs(root, 0);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            HookBridge.log("feed: tab hiding armed");
        } catch (Throwable t) {
            HookBridge.warn("tab hiding", t);
        }
    }

    /** Hides bottom-bar items by their accessibility text. Depth-bounded. */
    private static void hideTabs(View view, int depth) {
        if (view == null || depth > 22) return;
        try {
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    hideTabs(group.getChildAt(i), depth + 1);
                }
                return;
            }
            CharSequence content = view.getContentDescription();
            String label = content == null ? "" : content.toString().toLowerCase();
            if (label.isEmpty()) return;
            if (PrefsReader.get(PrefsKeys.HIDE_TAB_HOME, false) && label.contains("home")) hide(view);
            else if (PrefsReader.get(PrefsKeys.HIDE_TAB_REELS, false) && label.contains("reel")) hide(view);
            else if (PrefsReader.get(PrefsKeys.HIDE_TAB_CREATE, false) && label.contains("create")) hide(view);
            else if (PrefsReader.get(PrefsKeys.HIDE_TAB_DM, false) && label.contains("message")) hide(view);
            else if (PrefsReader.get(PrefsKeys.HIDE_TAB_ACTIVITY, false) && label.contains("activit")) hide(view);
            else if (PrefsReader.get(PrefsKeys.HIDE_TAB_PROFILE, false) && label.contains("profile")) hide(view);
        } catch (Throwable ignored) {
        }
    }

    private static void hide(View view) {
        try {
            view.setVisibility(View.GONE);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------ view sweep
    private static void hookSweep(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod(
                    Class.forName("android.app.Activity", false, loader),
                    "onResume", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                android.app.Activity activity = (android.app.Activity) param.thisObject;
                                if (activity == null || !isHost(activity)) return;
                                View root = activity.getWindow() == null ? null
                                        : activity.getWindow().getDecorView();
                                if (root == null) return;
                                sweep(root, 0);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            HookBridge.log("feed: de-clutter sweep armed");
        } catch (Throwable t) {
            HookBridge.warn("de-clutter sweep", t);
        }
    }

    /**
     * Walks the tree once and removes units whose label is on the user's block
     * list. Bounded depth and a strict label check keep false positives at zero:
     * a photo caption containing the word "sponsored" is a TextView deep inside a
     * media container, and only containers that structurally *are* the ad header
     * match here.
     */
    private static void sweep(View view, int depth) {
        if (view == null || depth > 20) return;
        try {
            if (!(view instanceof ViewGroup)) {
                if (!(view instanceof TextView)) return;
                String text = String.valueOf(((TextView) view).getText()).toLowerCase();
                if (text.isEmpty()) return;
                if (PrefsReader.get(PrefsKeys.BLOCK_ADS, false) && containsAny(text, AD_LABELS)) {
                    hideUnit((View) view.getParent());
                    return;
                }
                if (PrefsReader.get(PrefsKeys.HIDE_SUGGESTIONS, false)
                        && containsAny(text, SUGGEST_LABELS)) {
                    hideUnit((View) view.getParent());
                    return;
                }
                if (PrefsReader.get(PrefsKeys.HIDE_THREADS_SUGGEST, false)
                        && containsAny(text, THREADS_LABELS)) {
                    hideUnit((View) view.getParent());
                    return;
                }
                if (PrefsReader.get(PrefsKeys.REMOVE_META_AI, false)
                        && containsAny(text, META_AI_LABELS)) {
                    hideUnit((View) view.getParent());
                    return;
                }
                if (PrefsReader.get(PrefsKeys.HIDE_PLUS_UPSELLS, false)
                        && containsAny(text, PLUS_LABELS)) {
                    hideUnit((View) view.getParent());
                    return;
                }
                if (PrefsReader.get(PrefsKeys.HIDE_ONBOARDING_NAGS, false)
                        && containsAny(text, ONBOARD_LABELS)) {
                    hideUnit((View) view.getParent());
                }
                return;
            }
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                sweep(group.getChildAt(i), depth + 1);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Hides the row, not the label: three levels up is the card wrapper. */
    private static void hideUnit(View labelParent) {
        try {
            View cursor = labelParent;
            for (int i = 0; i < 3 && cursor != null; i++) {
                Object parent = cursor.getParent();
                if (!(parent instanceof View)) break;
                cursor = (View) parent;
            }
            if (cursor != null) cursor.setVisibility(View.GONE);
        } catch (Throwable ignored) {
        }
    }

    private static boolean containsAny(String text, String[] needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }

    private static boolean isHost(android.app.Activity activity) {
        try {
            return activity.getClass().getName().startsWith("com.instagram.android");
        } catch (Throwable t) {
            return false;
        }
    }

    private static void hookFalse(ClassLoader loader, String prefKey, String className,
                                  String[] methods) {
        hookFixed(loader, prefKey, className, methods, Boolean.FALSE);
    }

    private static void hookTrue(ClassLoader loader, String prefKey, String className,
                                 String[] methods) {
        hookFixed(loader, prefKey, className, methods, Boolean.TRUE);
    }

    private static void hookFixed(ClassLoader loader, String prefKey, String className,
                                  String[] methods, Object value) {
        Class<?> type;
        try {
            type = Class.forName(className, false, loader);
        } catch (Throwable ignored) {
            return;
        }
        List<Method> all = new ArrayList<>();
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                for (Method method : cursor.getDeclaredMethods()) all.add(method);
            } catch (Throwable ignored) {
            }
        }
        for (Method method : all) {
            if (method.getReturnType() != boolean.class
                    && method.getReturnType() != Boolean.class) continue;
            boolean nameMatch = false;
            for (String candidate : methods) if (candidate.equals(method.getName())) nameMatch = true;
            if (!nameMatch) continue;
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (PrefsReader.get(prefKey, false)) param.setResult(value);
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }
}
