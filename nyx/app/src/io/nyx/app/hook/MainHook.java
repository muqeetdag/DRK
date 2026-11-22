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
     * Points the reader at the module's own data dir. The APK lives in
     * /data/app/~~…/&lt;pkg&gt;-…/base.apk, and walking up from there lands in
     * /data/app — not the data dir. /data/user/0/&lt;pkg&gt; is the stable answer
     * on every Android version from 5.0 onward.
     */
    private static void bindPrefsPath() {
        try {
            PrefsReader.bindDataDir();
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
