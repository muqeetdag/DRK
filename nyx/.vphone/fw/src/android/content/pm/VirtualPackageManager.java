package android.content.pm;

import android.content.Context;
import android.content.Intent;
import android.test.Env;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Package manager for the virtual phone. Installed packages are seeded so the
 * console's LSPosed/LSPatch/Instagram detection can be exercised in every
 * combination without a real phone.
 */
public class VirtualPackageManager extends PackageManager {

    private final Context context;
    private final Set<String> installed = new LinkedHashSet<>();

    public VirtualPackageManager(Context context) {
        this.context = context;
        installed.add(Env.packageName);
    }

    public Set<String> installed() { return installed; }

    public void install(String packageName) { installed.add(packageName); }

    public void uninstall(String packageName) { installed.remove(packageName); }

    @Override
    public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
        if (!installed.contains(packageName)) throw new NameNotFoundException(packageName);
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.versionCode = Env.versionCode;
        info.versionName = Env.versionName;
        info.applicationInfo = getApplicationInfoSafe(packageName);
        return info;
    }

    @Override public ApplicationInfo getApplicationInfoSafe(String packageName) {
        ApplicationInfo info = new ApplicationInfo(packageName, packageName);
        info.targetSdkVersion = 35;
        return info;
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException {
        if (!installed.contains(packageName)) throw new NameNotFoundException(packageName);
        return getApplicationInfoSafe(packageName);
    }

    @Override
    public Intent getLaunchIntentForPackage(String packageName) {
        if (!installed.contains(packageName)) return null;
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setPackage(packageName);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        return intent;
    }

    @Override
    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
        List<ResolveInfo> out = new ArrayList<>();
        String pkg = intent == null ? null : intent.getPackage();
        if (pkg != null && installed.contains(pkg)) out.add(new ResolveInfo(pkg));
        return out;
    }

    @Override
    public String[] getPackagesForUid(int uid) {
        return installed.toArray(new String[0]);
    }
}
