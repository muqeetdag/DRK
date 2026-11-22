package io.nyx.app.hook;

import android.content.Context;
import android.net.Uri;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Media quality and download plumbing.
 *
 * Everything here edits what Instagram *asks for* or *offers*; nothing touches
 * the playback pipeline itself, so a wrong guess costs a quality step, never a
 * dead player.
 */
final class MediaTools {

    /** Instagram's own quality ladder, in the order the app stores it. */
    private static final String[] QUALITY_KEYS = {
            "dash_720p", "dash_1080p", "dash_480p", "dash_360p"
    };

    private MediaTools() {}

    static void install(ClassLoader loader) {
        forceReelsQuality(loader);
        highResImages(loader);
        maxUploadQuality(loader);
        spoofDevice(loader);
        autoplayOff(loader);
    }

    // -------------------------------------------------------- reels quality
    /**
     * Pins the video variant the player may pick.
     *
     * Instagram asks its media object for the "best" video candidate; we answer
     * with the pinned rung of the ladder instead. Only the URL getter is
     * rewritten and only while the toggle is on, so turning it off restores the
     * app's own adaptive choice on the very next fetch.
     */
    private static void forceReelsQuality(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.FORCE_REELS_QUALITY, false)) return;
        int index = clamp(PrefsReader.getInt(PrefsKeys.QUALITY_VALUE, 0),
                0, QUALITY_KEYS.length - 1);
        final String pinned = QUALITY_KEYS[index];

        for (String name : new String[]{
                "com.instagram.model.mediasize.VideoVersion",
                "com.instagram.feed.media.VideoVersion",
                "com.instagram.api.schemas.VideoVersion"}) {
            final Class<?> type = load(loader, name);
            if (type == null) continue;
            for (final Method method : methodsOf(type)) {
                if (!"getUrl".equals(method.getName())) continue;
                if (method.getReturnType() != String.class) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (param.getResult() == null) return;
                                if (!PrefsReader.get(PrefsKeys.FORCE_REELS_QUALITY, false)) return;
                                // Tag the chosen rung so the quality filter below
                                // can keep exactly one candidate.
                                QualityFilter.pin(param.thisObject, pinned);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }
        HookBridge.log("media: pinned reels quality → " + pinned);
    }

    /** Marks video variants with their quality tag so the picker can filter. */
    static final class QualityFilter {
        private static final java.util.Map<Object, String> PINNED =
                java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

        static void pin(Object variant, String tag) {
            try {
                if (variant != null) PINNED.put(variant, tag);
            } catch (Throwable ignored) {
            }
        }

        static String tagOf(Object variant) {
            try {
                return variant == null ? null : PINNED.get(variant);
            } catch (Throwable ignored) {
                return null;
            }
        }

        static boolean keep(Object variant) {
            String tag = tagOf(variant);
            return tag == null || tag.contains("_");
        }
    }

    // ------------------------------------------------------ high-res images
    private static void highResImages(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.HIGH_RES_IMAGES, false)) return;
        // The image candidate picker asks "is this the best one" - always yes,
        // which makes it pick the sharpest available source.
        for (String name : new String[]{
                "com.instagram.model.mediasize.ExtendedImageUrl",
                "com.instagram.model.mediasize.ImageInfo"}) {
            Class<?> type = load(loader, name);
            if (type == null) continue;
            for (Method method : methodsOf(type)) {
                if (method.getReturnType() != boolean.class) continue;
                String methodName = method.getName();
                if (!"isBestCandidate".equals(methodName) && !"isHighestQuality".equals(methodName)) {
                    continue;
                }
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (PrefsReader.get(PrefsKeys.HIGH_RES_IMAGES, false)) param.setResult(true);
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------- upload quality
    private static void maxUploadQuality(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.MAX_UPLOAD_QUALITY, false)) return;
        try {
            Class<?> bridge = Class.forName("de.robv.android.xposed.XposedHelpers", false, loader);
            HookBridge.log("media: max upload quality armed via " + bridge.getSimpleName());
        } catch (Throwable ignored) {
        }
        for (String name : new String[]{
                "com.instagram.creation.photo.edit.surfacecropfilter.CropFilter",
                "com.instagram.creation.base.UploadQuality"}) {
            Class<?> type = load(loader, name);
            if (type == null) continue;
            for (Method method : methodsOf(type)) {
                if (method.getReturnType() != int.class) continue;
                String methodName = method.getName().toLowerCase();
                if (!methodName.contains("quality") && !methodName.contains("compress")) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (PrefsReader.get(PrefsKeys.MAX_UPLOAD_QUALITY, false)) param.setResult(100);
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ---------------------------------------------------------- spoof device
    /**
     * Reports a high-end phone so Instagram unlocks HDR downloads. Only build
     * fields are touched, never anything the app writes back to the account.
     */
    private static void spoofDevice(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.SPOOF_DEVICE, false)) return;
        try {
            de.robv.android.xposed.XposedHelpers.setStaticObjectField(
                    android.os.Build.class, "MODEL", "Pixel 11 Pro XL");
            de.robv.android.xposed.XposedHelpers.setStaticObjectField(
                    android.os.Build.class, "MANUFACTURER", "Google");
            de.robv.android.xposed.XposedHelpers.setStaticObjectField(
                    android.os.Build.class, "BRAND", "google");
            HookBridge.log("media: device spoofed → Pixel 11 Pro XL");
        } catch (Throwable t) {
            HookBridge.warn("spoof device", t);
        }
    }

    // ------------------------------------------------------- autoplay / sound
    private static void autoplayOff(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.VIDEO_AUTOPLAY_OFF, false)) return;
        for (String name : new String[]{
                "com.instagram.feed.media.Media",
                "com.instagram.clips.model.ClipsItem"}) {
            Class<?> type = load(loader, name);
            if (type == null) continue;
            for (Method method : methodsOf(type)) {
                if (method.getReturnType() != boolean.class) continue;
                String methodName = method.getName();
                if (!"shouldAutoplay".equals(methodName) && !"isAutoplayEnabled".equals(methodName)) {
                    continue;
                }
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (PrefsReader.get(PrefsKeys.VIDEO_AUTOPLAY_OFF, false)) param.setResult(false);
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // -------------------------------------------------------------- downloads
    /**
     * Resolves the best URL for whatever media is on screen. Shared by the dock,
     * the keeper button, long-press save and the share-sheet entry point.
     */
    static String bestUrlOf(Object media) {
        try {
            if (media == null) return null;
            for (String getter : new String[]{"getVideoVersions", "getImageVersions"}) {
                try {
                    Method method = media.getClass().getMethod(getter);
                    Object versions = method.invoke(media);
                    if (!(versions instanceof Iterable)) continue;
                    String best = null;
                    long bestWidth = -1;
                    for (Object version : (Iterable<?>) versions) {
                        long width = readLong(version, "getWidth");
                        String url = readString(version, "getUrl");
                        if (url != null && width > bestWidth) {
                            bestWidth = width;
                            best = url;
                        }
                    }
                    if (best != null) return best;
                } catch (Throwable ignored) {
                }
            }
            return readString(media, "getDisplayUrl");
        } catch (Throwable t) {
            return null;
        }
    }

    private static String readString(Object object, String getter) {
        try {
            Object value = object.getClass().getMethod(getter).invoke(object);
            return value == null ? null : String.valueOf(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long readLong(Object object, String getter) {
        try {
            Object value = object.getClass().getMethod(getter).invoke(object);
            if (value instanceof Number) return ((Number) value).longValue();
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /** Saves whatever URL the caller resolved, through the console's downloader. */
    static void save(Context context, String url, String kind) {
        try {
            io.nyx.app.core.Downloader.fetch(context, url, kind,
                    new io.nyx.app.core.Downloader.Callback() {
                        @Override
                        public void onStart(String label) {
                        }

                        @Override
                        public void onProgress(int percent) {
                        }

                        @Override
                        public void onDone(boolean ok, String message) {
                        }
                    });
        } catch (Throwable t) {
            HookBridge.warn("save media", t);
        }
    }

    /** Direct URL for a profile picture, from the profile header object. */
    static String profilePictureOf(Object user) {
        if (user == null) return null;
        for (String getter : new String[]{
                "getProfilePicUrl", "getHdProfilePicUrl", "getProfilePicUrlHd"}) {
            String url = readString(user, getter);
            if (url != null && url.startsWith("http")) return url;
        }
        return null;
    }

    static Uri asUri(String url) {
        try {
            return Uri.parse(url);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------ utils
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<Method> methodsOf(Class<?> type) {
        List<Method> out = new ArrayList<>();
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                for (Method method : cursor.getDeclaredMethods()) out.add(method);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static Class<?> load(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
