package android.util;

import android.test.Env;

/** DisplayMetrics backed by the virtual phone configuration. */
public class DisplayMetrics {
    public float density = Env.densityDpi / 160f;
    public int densityDpi = Env.densityDpi;
    public int widthPixels = Env.widthPx;
    public int heightPixels = Env.heightPx;
    public float scaledDensity = density;
    public float xdpi = densityDpi;
    public float ydpi = densityDpi;

    public void setTo(DisplayMetrics other) {
        density = other.density;
        densityDpi = other.densityDpi;
        widthPixels = other.widthPixels;
        heightPixels = other.heightPixels;
        scaledDensity = other.scaledDensity;
    }

    public void setToDefaults() {}

    public static int DENSITY_DEVICE = Env.densityDpi;
}
