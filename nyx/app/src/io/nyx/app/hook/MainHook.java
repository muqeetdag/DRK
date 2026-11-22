package io.nyx.app.hook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Entry point declared in assets/xposed_init.
 *
 * Only Instagram is touched. The loader callback must return fast — heavy work
 * happens on the main handler inside InstagramAttach.
 */
public class MainHook implements IXposedHookLoadPackage {

    static final String TARGET = "com.instagram.android";

    /**
     * The module APK lives in /data/app like any other, so the mirrored prefs
     * are at a stable, world-readable path inside it. Resolve it once via the
     * hook's own class loader rather than assuming a data dir.
     */
    private static void bindPrefsPath() {
        try {
            ClassLoader loader = MainHook.class.getClassLoader();
            if (loader == null) return;
            java.util.Enumeration<java.net.URL> roots = loader.getResources("assets/xposed_init");
            while (roots.hasMoreElements()) {
                String url = roots.nextElement().toString();
                int base = url.indexOf("base.apk");
                if (base > 0) {
                    String apkPath = url.substring("jar:file:".length(), base + "base.apk".length());
                    java.io.File files = new java.io.File(
                            new java.io.File(apkPath).getParentFile().getParentFile(), "files");
                    PrefsReader.setFilesDir(files.getAbsolutePath());
                    HookBridge.log("prefs mirror dir → " + files);
                    return;
                }
            }
        } catch (Throwable t) {
            HookBridge.warn("prefs path", t);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) return;
        if (!TARGET.equals(lpparam.packageName)) return;

        HookBridge.safeRun("main hook init", () -> {
            HookBridge.log("loaded into " + lpparam.packageName
                    + " (" + lpparam.processName + ", first=" + lpparam.isFirstApplication + ")");
            bindPrefsPath();
            new InstagramAttach(lpparam.classLoader).start();
        });
    }
}
