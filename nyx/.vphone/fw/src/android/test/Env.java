package android.test;

/** Test-environment knobs, deliberately inside android.test to keep them out of app code. */
public final class Env {
    public static int densityDpi = 420;
    public static int widthPx = 1080;
    public static int heightPx = 2400;
    public static String resRoot = "";
    public static String packageName = "io.nyx.app";
    public static int versionCode = 110;
    public static String versionName = "1.1.0";
    private Env() {}
}
