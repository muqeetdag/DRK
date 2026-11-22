package io.nyx.app.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Crash-safe access to the module's own settings.
 *
 * Every read is wrapped: Android can throw when the prefs file is being
 * rewritten by another process (the hooked Instagram reads a mirror, the
 * console writes it), and a thrown exception inside the console is exactly
 * what "app crashes on open" looks like. Nothing here may propagate.
 */
public final class SafePrefs {

    public static final int MODE_ROOT = 1;
    public static final int MODE_NON_ROOT = 2;

    public static final String INSTALL_MODE = "install_mode";
    public static final String ONBOARDED = "onboarded";

    private SafePrefs() {}

    public static SharedPreferences sp(Context ctx) {
        return Prefs.open(ctx);
    }

    public static boolean bool(Context ctx, String key, boolean fallback) {
        try {
            return sp(ctx).getBoolean(key, fallback);
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static String str(Context ctx, String key, String fallback) {
        try {
            String value = sp(ctx).getString(key, fallback);
            return value == null ? fallback : value;
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static int num(Context ctx, String key, int fallback) {
        try {
            return sp(ctx).getInt(key, fallback);
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static void set(Context ctx, String key, boolean value) {
        try {
            sp(ctx).edit().putBoolean(key, value).commit();
            Prefs.writeMirror(ctx);
        } catch (Throwable ignored) {
        }
    }

    public static void set(Context ctx, String key, int value) {
        try {
            sp(ctx).edit().putInt(key, value).commit();
            Prefs.writeMirror(ctx);
        } catch (Throwable ignored) {
        }
    }

    public static void set(Context ctx, String key, String value) {
        try {
            sp(ctx).edit().putString(key, value).commit();
            Prefs.writeMirror(ctx);
        } catch (Throwable ignored) {
        }
    }

    public static int installMode(Context ctx) {
        int mode = num(ctx, INSTALL_MODE, 0);
        return mode == MODE_ROOT || mode == MODE_NON_ROOT ? mode : 0;
    }

    public static boolean onboarded(Context ctx) {
        return bool(ctx, ONBOARDED, false) && installMode(ctx) != 0;
    }

    /**
     * Wipes only keys this build owns, so a corrupted write can be recovered
     * without dropping the install-mode choice.
     */
    public static void repair(Context ctx) {
        try {
            int mode = installMode(ctx);
            SharedPreferences.Editor editor = sp(ctx).edit();
            for (String key : Prefs.FEATURE_KEYS) editor.remove(key);
            editor.remove(Prefs.THEME);
            editor.remove(Prefs.ACCENT);
            editor.remove(Prefs.SECONDARY);
            editor.remove(Prefs.FOLDER);
            editor.putBoolean("seeded", false);
            editor.commit();
            Prefs.seedIfEmpty(ctx);
            if (mode != 0) set(ctx, INSTALL_MODE, mode);
            Prefs.writeMirror(ctx);
        } catch (Throwable ignored) {
        }
    }
}
