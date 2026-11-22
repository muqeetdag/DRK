package io.nyx.app.core;

import io.nyx.app.core.SafeUi;

import android.os.Build;
import android.widget.TextView;

/**
 * API-level and OEM-safe wrappers around the few view calls that are newer than
 * our minSdk (Android 5.0 / API 21) or that throw on some OEM ROMs.
 *
 * Every method is a no-op instead of a crash. Cosmetic differences on very old
 * phones are acceptable; a dead app is not.
 */
public final class SafeUi {

    /** Window#setStatusBarColor / setNavigationBarColor: API 21+, but some OEMs throw. */
    public static void statusBar(android.view.Window window, int color) {
        if (window == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 21) window.setStatusBarColor(color);
        } catch (Throwable ignored) {
        }
        try {
            if (Build.VERSION.SDK_INT >= 21) window.setNavigationBarColor(color);
        } catch (Throwable ignored) {
        }
    }

    /** TextView#setLetterSpacing: API 21+, but a few 5.x builds throw on it. */
    public static void letterSpacing(TextView view, float value) {
        if (view == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 21) SafeUi.letterSpacing(view, value);
        } catch (Throwable ignored) {
        }
    }

    /** Typeface.create(String,int): API 21+. Fall back to the default family. */
    public static void typeface(TextView view, String family, int style) {
        if (view == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 21 && family != null) {
                SafeUi.typeface(view, family, style);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        } catch (Throwable ignored) {
        }
    }

    /** Paint#setTypeface with a named family: used by the splash canvas. */
    public static void typeface(android.graphics.Paint paint, String family, int style) {
        if (paint == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 21 && family != null) {
                paint.setTypeface(android.graphics.Typeface.create(family, style));
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        } catch (Throwable ignored) {
        }
    }

    /** View#setElevation: API 21+, and a shadow is never worth a crash. */
    public static void elevation(android.view.View view, float value) {
        if (view == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 21) SafeUi.elevation(view, value);
        } catch (Throwable ignored) {
        }
    }

    /** Window#addFlags / clearFlags: harmless on every level, guarded for OEMs. */
    public static void windowFlags(android.view.Window window, int add, int clear) {
        if (window == null) return;
        try {
            if (add != 0) window.addFlags(add);
            if (clear != 0) window.clearFlags(clear);
        } catch (Throwable ignored) {
        }
    }

    private SafeUi() {}
}
