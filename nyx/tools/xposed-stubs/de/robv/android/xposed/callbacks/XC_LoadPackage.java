package de.robv.android.xposed.callbacks;

import de.robv.android.xposed.XC_MethodHook;

/** Compile-time stub of the Xposed API (provided at runtime by LSPosed). */
public abstract class XC_LoadPackage {
    public static final class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public boolean isFirstApplication;
        public ClassLoader getClassLoader() { return classLoader; }
    }
}
