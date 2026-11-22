package android.content.pm;

/** Application flags + identity. */
public class ApplicationInfo {
    public static final int FLAG_DEBUGGABLE = 0x2;
    public static final int FLAG_SYSTEM = 0x1;
    public static final int FLAG_EXTERNAL_STORAGE = 0x40000;

    public String packageName;
    public String name;
    public int flags;
    public int uid = 10000;
    public String sourceDir;
    public String dataDir;
    public String processName;
    public int targetSdkVersion = 35;
    public int minSdkVersion = 26;
    public boolean enabled = true;

    public ApplicationInfo() {}

    public ApplicationInfo(String packageName, String name) {
        this.packageName = packageName;
        this.name = name;
    }
}
