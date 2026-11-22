package io.nyx.app.core;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Tiny tick used by the dock and buttons; respects the haptics toggle. */
public final class Haptics {
    private Haptics() {}

    public static void tick(Context ctx) {
        if (!Prefs.get(Prefs.open(ctx), Prefs.HAPTIC, true)) return;
        try {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                //noinspection deprecation
                v.vibrate(12);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void heavy(Context ctx) {
        try {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(28, 180));
            } else {
                //noinspection deprecation
                v.vibrate(28);
            }
        } catch (Throwable ignored) {
        }
    }
}
