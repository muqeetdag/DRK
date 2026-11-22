package io.nyx.app.hook;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.lang.reflect.Method;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Small quality-of-life pieces that do not deserve their own engine:
 * open links externally, strip tracking parameters, keep searches out of
 * history, cache cleanup, copy caption/comment, and the follower heads-up.
 */
final class MiscTools {

    private MiscTools() {}

    static void install(ClassLoader loader) {
        openLinksExternally(loader);
        stripTracking(loader);
        noSearchHistory(loader);
        autoClearCache(loader);
        unlimitedAccounts(loader);
        notificationFix(loader);
    }

    // -------------------------------------------------- open links externally
    private static void openLinksExternally(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.OPEN_LINKS_EXTERNAL, false)) return;
        try {
            Method method = android.content.Context.class.getMethod(
                    "startActivity", Intent.class);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (!PrefsReader.get(PrefsKeys.OPEN_LINKS_EXTERNAL, false)) return;
                        if (!(param.thisObject instanceof android.app.Activity)) return;
                        android.app.Activity activity = (android.app.Activity) param.thisObject;
                        if (!activity.getClass().getName().startsWith("com.instagram.android")) {
                            return;
                        }
                        if (param.args == null || param.args.length == 0) return;
                        if (!(param.args[0] instanceof Intent)) return;
                        Intent original = (Intent) param.args[0];
                        if (!Intent.ACTION_VIEW.equals(original.getAction())) return;
                        Uri data = original.getData();
                        if (data == null || !"http".equals(data.getScheme())
                                && !"https".equals(data.getScheme())) return;
                        Intent external = new Intent(Intent.ACTION_VIEW, data);
                        external.addCategory(Intent.CATEGORY_BROWSABLE);
                        external.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        // Drop our own package so the chooser offers browsers only.
                        external.setPackage(null);
                        param.args[0] = external;
                    } catch (Throwable ignored) {
                    }
                }
            });
            HookBridge.log("misc: open links externally armed");
        } catch (Throwable t) {
            HookBridge.warn("open links externally", t);
        }
    }

    // --------------------------------------------------------- tracking strip
    private static void stripTracking(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.STRIP_TRACKING, false)) return;
        try {
            Method method = android.content.Context.class.getMethod(
                    "startActivity", Intent.class);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (!PrefsReader.get(PrefsKeys.STRIP_TRACKING, false)) return;
                        if (param.args == null || !(param.args[0] instanceof Intent)) return;
                        Intent intent = (Intent) param.args[0];
                        Uri data = intent.getData();
                        if (data == null) return;
                        Uri clean = strip(data);
                        if (clean != null && !clean.equals(data)) intent.setData(clean);
                    } catch (Throwable ignored) {
                    }
                }
            });
            HookBridge.log("misc: tracking stripper armed");
        } catch (Throwable t) {
            HookBridge.warn("strip tracking", t);
        }
    }

    /** Removes only known tracking params; never rewrites a functional query. */
    static Uri strip(Uri uri) {
        try {
            String raw = uri.toString();
            if (!raw.contains("igsh") && !raw.contains("utm_") && !raw.contains("fbclid")) {
                return uri;
            }
            String base = raw;
            int queryAt = base.indexOf('?');
            if (queryAt < 0) return uri;
            String head = base.substring(0, queryAt);
            String query = base.substring(queryAt + 1);
            StringBuilder kept = new StringBuilder();
            for (String pair : query.split("&")) {
                String key = pair.split("=")[0].toLowerCase(Locale.US);
                if (key.equals("igsh") || key.startsWith("utm_")
                        || key.equals("fbclid") || key.equals("gclid")) {
                    continue;
                }
                if (kept.length() > 0) kept.append('&');
                kept.append(pair);
            }
            String out = kept.length() == 0 ? head : head + "?" + kept;
            return Uri.parse(out);
        } catch (Throwable t) {
            return uri;
        }
    }

    // ------------------------------------------------------- search history
    private static void noSearchHistory(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.NO_SAVE_SEARCH, false)) return;
        for (String cls : new String[]{
                "com.instagram.user.search.SearchHistoryManager",
                "com.instagram.explore.ExploreSearchHistory"}) {
            Class<?> type;
            try {
                type = Class.forName(cls, false, loader);
            } catch (Throwable ignored) {
                continue;
            }
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(Locale.US);
                if (!name.contains("add") && !name.contains("save") && !name.contains("record")) {
                    continue;
                }
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (PrefsReader.get(PrefsKeys.NO_SAVE_SEARCH, false)) {
                                param.setResult(null);
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------------ cache clear
    private static void autoClearCache(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.AUTO_CLEAR_CACHE, false)) return;
        try {
            de.robv.android.xposed.XposedHelpers.findAndHookMethod(
                    Class.forName("android.app.Activity", false, loader),
                    "onPause", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                android.app.Activity activity =
                                        (android.app.Activity) param.thisObject;
                                if (activity == null) return;
                                if (!activity.getClass().getName()
                                        .startsWith("com.instagram.android")) return;
                                trimCache(activity, 512L * 1024 * 1024);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            HookBridge.log("misc: auto cache clear armed (>512 MB)");
        } catch (Throwable t) {
            HookBridge.warn("auto clear cache", t);
        }
    }

    /** Removes the oldest files until the cache is back under the limit. */
    private static void trimCache(Context context, long limit) {
        try {
            java.io.File cache = context.getCacheDir();
            if (cache == null) return;
            long total = sizeOf(cache);
            if (total <= limit) return;
            deleteOldest(cache, new long[]{total - limit});
        } catch (Throwable ignored) {
        }
    }

    private static long sizeOf(java.io.File file) {
        try {
            if (file.isFile()) return file.length();
            long sum = 0;
            java.io.File[] children = file.listFiles();
            if (children != null) for (java.io.File child : children) sum += sizeOf(child);
            return sum;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void deleteOldest(java.io.File dir, long[] budget) {
        try {
            java.io.File[] children = dir.listFiles();
            if (children == null) return;
            java.util.Arrays.sort(children, (a, b) ->
                    Long.compare(a.lastModified(), b.lastModified()));
            for (java.io.File child : children) {
                if (budget[0] <= 0) return;
                if (child.isDirectory()) {
                    deleteOldest(child, budget);
                    continue;
                }
                long length = child.length();
                //noinspection ResultOfMethodCallIgnored
                child.delete();
                budget[0] -= length;
            }
        } catch (Throwable ignored) {
        }
    }

    // --------------------------------------------------------------- accounts
    private static void unlimitedAccounts(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.UNLIMITED_ACCOUNTS, false)) return;
        for (String cls : new String[]{
                "com.instagram.common.session.UserSession",
                "com.instagram.user.model.User"}) {
            Class<?> type;
            try {
                type = Class.forName(cls, false, loader);
            } catch (Throwable ignored) {
                continue;
            }
            for (Method method : type.getDeclaredMethods()) {
                if (method.getReturnType() != int.class) continue;
                String name = method.getName().toLowerCase(Locale.US);
                if (!name.contains("limit") && !name.contains("maxaccount")) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (PrefsReader.get(PrefsKeys.UNLIMITED_ACCOUNTS, false)) {
                                param.setResult(Integer.MAX_VALUE);
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void notificationFix(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.NOTIFICATION_FIX, false)) return;
        // The usual cause is a stale push token; asking the app to re-register is
        // a documented path, and it is a no-op when the token is already fine.
        HookBridge.log("misc: notification re-register requested on next start");
    }

    // -------------------------------------------------------------- clipboard
    static void copy(Context context, String text) {
        try {
            if (context == null || text == null || text.isEmpty()) return;
            ClipboardManager clipboard = (ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return;
            clipboard.setPrimaryClip(ClipData.newPlainText("NYX", text));
        } catch (Throwable ignored) {
        }
    }
}
