package de.robv.android.xposed;

/** Compile-time stub of the Xposed API (provided at runtime by LSPosed). */
public final class XposedBridge {
    /**
     * Real XposedBridge API: hook an already-resolved Method. Present here so the
     * module compiles against the same surface it runs against.
     */
    public static de.robv.android.xposed.XC_MethodHook.Unhook hookMethod(
            java.lang.reflect.Member hookMethod, XC_MethodHook callback) {
        return null;
    }

    public static void log(String text) {}
    public static void log(Throwable t) {}
    public static void log(String text, Throwable t) {}
}
