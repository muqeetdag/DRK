package de.robv.android.xposed;

/** Compile-time stub of the Xposed API (provided at runtime by LSPosed). */
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    /** Returned by XposedHelpers.findAndHookMethod. */
    public static final class Unhook {
        public void unhook() {}
    }

    public static final class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public Object getResult() { return null; }
        public void setResult(Object result) {}
        public Throwable getThrowable() { return null; }
        public void setThrowable(Throwable throwable) {}
    }
}
