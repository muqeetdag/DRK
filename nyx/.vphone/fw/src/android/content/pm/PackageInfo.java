package android.content.pm;

/** Installed package facts. */
public class PackageInfo {
    public String packageName;
    public int versionCode;
    public String versionName;
    public ApplicationInfo applicationInfo;
    public long firstInstallTime = System.currentTimeMillis();
    public long lastUpdateTime = System.currentTimeMillis();
}
