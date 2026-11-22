package de.robv.android.xposed;

/** Virtual-phone runtime double of the Xposed API (records hooks, never throws). */
public final class XposedBridge {
    /** Hooks recorded by the virtual phone, for the suite's assertions. */
    public static final java.util.List<String> HOOKED = new java.util.ArrayList<String>();

    public static de.robv.android.xposed.XC_MethodHook.Unhook hookMethod(
            java.lang.reflect.Member hookMethod, XC_MethodHook callback) {
        if (hookMethod != null) {
            synchronized (HOOKED) {
                HOOKED.add(hookMethod.getDeclaringClass().getName() + "#" + hookMethod.getName());
            }
        }
        return new XC_MethodHook.Unhook();
    }

    public static void log(String text) {}
    public static void log(Throwable t) {}
    public static void log(String text, Throwable t) {}
}
