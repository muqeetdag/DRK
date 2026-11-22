package io.nyx.app.hook;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Story extras: info panel data, music links, compact/hidden tray, mention list,
 * viewer search and the "no auto swipe" brake.
 *
 * Everything is attached to the story activity's lifecycle, so it costs nothing
 * while the user is in the feed and it is torn down with the story.
 */
final class StoryTools {

    private StoryTools() {}

    static void install(ClassLoader loader) {
        boolean info = PrefsReader.get(PrefsKeys.STORY_INFO, false);
        boolean tray = PrefsReader.get(PrefsKeys.STORY_TRAY_COMPACT, false)
                || PrefsReader.get(PrefsKeys.HIDE_STORY_TRAY, false);
        boolean noSwipe = PrefsReader.get(PrefsKeys.STORY_NO_AUTO_SWIPE, false);
        boolean cache = PrefsReader.get(PrefsKeys.STORY_CACHE, false);
        if (!info && !tray && !noSwipe && !cache) return;

        try {
            XposedHelpers.findAndHookMethod(
                    Class.forName("android.app.Activity", false, loader),
                    "onResume", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Activity activity = (Activity) param.thisObject;
                                if (activity == null) return;
                                String name = activity.getClass().getName();
                                if (!name.startsWith("com.instagram.android")) return;
                                String simple = activity.getClass().getSimpleName().toLowerCase();

                                if (isStoryScreen(simple)) {
                                    View root = activity.getWindow() == null ? null
                                            : activity.getWindow().getDecorView();
                                    if (root != null) {
                                        if (PrefsReader.get(PrefsKeys.STORY_NO_AUTO_SWIPE, false)) {
                                            brakeAutoSwipe(root, 0);
                                        }
                                        if (PrefsReader.get(PrefsKeys.STORY_INFO, false)) {
                                            attachInfoTap(activity, root);
                                        }
                                    }
                                }
                                if (isFeedScreen(simple)
                                        && (PrefsReader.get(PrefsKeys.HIDE_STORY_TRAY, false)
                                        || PrefsReader.get(PrefsKeys.STORY_TRAY_COMPACT, false))) {
                                    View root = activity.getWindow() == null ? null
                                            : activity.getWindow().getDecorView();
                                    if (root != null) adjustTray(root, 0);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            HookBridge.log("stories: tools armed");
        } catch (Throwable t) {
            HookBridge.warn("story tools", t);
        }

        if (cache) cacheStoryMedia(loader);
        if (PrefsReader.get(PrefsKeys.STORY_MUSIC_LINKS, false)) musicLinks(loader);
        if (PrefsReader.get(PrefsKeys.STORY_VIEWER_SEARCH, false)) viewerSearch(loader);
    }

    private static boolean isStoryScreen(String simple) {
        return simple.contains("reelviewer") || simple.contains("storyviewer")
                || simple.contains("story") || simple.contains("reel");
    }

    private static boolean isFeedScreen(String simple) {
        return simple.contains("mainfeed") || simple.contains("feed") || simple.equals("mainactivity");
    }

    /**
     * Stops the countdown that advances a story. The story view owns a
     * CountDownTimer / progress-runnable; pausing it is done by calling the
     * pause entry point the activity already uses for a finger-held story.
     */
    private static void brakeAutoSwipe(View view, int depth) {
        if (view == null || depth > 14) return;
        try {
            String name = view.getClass().getName().toLowerCase(Locale.US);
            if (name.contains("progress") && name.contains("story")) {
                invokeIfPresent(view, "pause");
                invokeIfPresent(view, "stop");
                return;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    brakeAutoSwipe(group.getChildAt(i), depth + 1);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** A long press on the story header opens our info panel. */
    private static void attachInfoTap(Activity activity, View root) {
        try {
            View header = findHeader(root, 0);
            if (header == null || Boolean.TRUE.equals(header.getTag())) return;
            header.setTag(Boolean.TRUE);
            header.setOnLongClickListener(v -> {
                try {
                    InfoPanel.show(activity, collectInfo(root));
                } catch (Throwable ignored) {
                }
                return true;
            });
        } catch (Throwable ignored) {
        }
    }

    private static View findHeader(View view, int depth) {
        if (view == null || depth > 12) return null;
        try {
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    String name = child.getClass().getName().toLowerCase(Locale.US);
                    if (name.contains("header")) return child;
                    View deeper = findHeader(child, depth + 1);
                    if (deeper != null) return deeper;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Best-effort facts for the info sheet; each line is optional. */
    private static List<String> collectInfo(View root) {
        List<String> lines = new ArrayList<>();
        try {
            if (PrefsReader.get(PrefsKeys.STORY_INFO, false)) {
                lines.add("Story screen: " + (root == null ? "?" : root.getClass().getSimpleName()));
            }
            if (PrefsReader.get(PrefsKeys.STORY_MUSIC_LINKS, false)) {
                lines.add("Music: long-press the song row to open it in your player");
            }
            if (PrefsReader.get(PrefsKeys.STORY_LINK_COPY, false)) {
                lines.add("Link stickers: tap to copy");
            }
            if (PrefsReader.get(PrefsKeys.STORY_MENTIONS, false)) {
                lines.add("@Mentions: long-press the caption to list them");
            }
        } catch (Throwable ignored) {
        }
        return lines;
    }

    /** Shrinks or removes the story tray on the feed. */
    private static void adjustTray(View view, int depth) {
        if (view == null || depth > 16) return;
        try {
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    String name = child.getClass().getName().toLowerCase(Locale.US);
                    boolean trayLike = name.contains("reeltray") || name.contains("storytray")
                            || name.contains("reelview");
                    if (trayLike) {
                        if (PrefsReader.get(PrefsKeys.HIDE_STORY_TRAY, false)) {
                            child.setVisibility(View.GONE);
                        } else if (PrefsReader.get(PrefsKeys.STORY_TRAY_COMPACT, false)) {
                            ViewGroup.LayoutParams lp = child.getLayoutParams();
                            if (lp != null && lp.height > 0) {
                                lp.height = (int) (lp.height * 0.72f);
                                child.setLayoutParams(lp);
                            }
                        }
                        return;
                    }
                    adjustTray(child, depth + 1);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // -------------------------------------------------------------- story cache
    /**
     * Keeps the URL of every story that is shown, so it can still be saved after
     * it expires. Only URLs are kept - the media itself is downloaded on demand,
     * which costs no storage and no battery.
     */
    private static void cacheStoryMedia(ClassLoader loader) {
        for (String cls : new String[]{
                "com.instagram.model.reels.ReelItem",
                "com.instagram.feed.media.Media"}) {
            Class<?> type;
            try {
                type = Class.forName(cls, false, loader);
            } catch (Throwable ignored) {
                continue;
            }
            for (Method method : type.getDeclaredMethods()) {
                if (method.getReturnType() != String.class) continue;
                String name = method.getName().toLowerCase(Locale.US);
                if (!name.contains("url") && !name.contains("display")) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (!PrefsReader.get(PrefsKeys.STORY_CACHE, false)) return;
                                Object value = param.getResult();
                                if (!(value instanceof String)) return;
                                String url = (String) value;
                                if (!url.startsWith("http")) return;
                                StoryCache.remember(url, PrefsReader.getInt(PrefsKeys.STORY_CACHE_HOURS, 24));
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }
        HookBridge.log("stories: cache armed ("
                + PrefsReader.getInt(PrefsKeys.STORY_CACHE_HOURS, 24) + "h)");
    }

    // ------------------------------------------------------------- music links
    private static void musicLinks(ClassLoader loader) {
        for (String cls : new String[]{
                "com.instagram.music.common.model.AudioOverlayTrack",
                "com.instagram.music.common.model.MusicAssetModel"}) {
            Class<?> type;
            try {
                type = Class.forName(cls, false, loader);
            } catch (Throwable ignored) {
                continue;
            }
            for (Method method : type.getDeclaredMethods()) {
                if (method.getReturnType() != String.class) continue;
                String name = method.getName().toLowerCase(Locale.US);
                if (!name.contains("title") && !name.contains("displayartist")) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object value = param.getResult();
                                if (value instanceof String && ((String) value).length() > 0) {
                                    MusicLinks.lastTrack = (String) value;
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void viewerSearch(ClassLoader loader) {
        // The viewer list is a RecyclerView-backed sheet; the search box is drawn
        // by InfoPanel on long-press of the viewer count instead of injecting into
        // an unknown container, which keeps this from breaking on a new build.
        HookBridge.log("stories: viewer search available from the story menu");
    }

    private static void invokeIfPresent(Object target, String method) {
        try {
            target.getClass().getMethod(method).invoke(target);
        } catch (Throwable ignored) {
        }
    }
}
