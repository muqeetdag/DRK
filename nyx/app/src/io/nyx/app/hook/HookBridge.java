package io.nyx.app.hook;

import de.robv.android.xposed.XposedBridge;

/**
 * Reflection-safe bridge so a broken hook can never take Instagram down with
 * it. Every entry point swallows Throwable and logs instead.
 */
public final class HookBridge {

    private static final String TAG = "[NYX] ";
    private static int failures = 0;

    private HookBridge() {}

    public static void log(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
        }
    }

    public static void warn(String message, Throwable t) {
        failures++;
        try {
            XposedBridge.log(TAG + message + " :: " + (t == null ? "?" : t.getClass().getSimpleName()
                    + ": " + t.getMessage()));
        } catch (Throwable ignored) {
        }
    }

    public static boolean alive() {
        return failures < 60;
    }

    public static int failures() {
        return failures;
    }

    /** Runs a hook body, returning null if the host shape changed. */
    public static <T> T safe(String label, ThrowingSupplier<T> body) {
        try {
            return body.get();
        } catch (Throwable t) {
            warn(label, t);
            return null;
        }
    }

    public static void safeRun(String label, ThrowingRunnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            warn(label, t);
        }
    }

    /** A class in the host app, by any of several candidate names. */
    public static Class<?> findAny(ClassLoader loader, String... names) {
        for (String name : names) {
            try {
                return Class.forName(name, false, loader);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }

    public interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
