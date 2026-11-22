package android.content.pm;

/** Intent resolution result. */
public class ResolveInfo {
    public ActivityInfo activityInfo;
    public String resolvePackageName;
    public int priority;

    public ResolveInfo() {}

    public ResolveInfo(String packageName) {
        this.resolvePackageName = packageName;
        this.activityInfo = new ActivityInfo();
        this.activityInfo.packageName = packageName;
    }

    public static class ActivityInfo {
        public String packageName;
        public String name;
        public String targetActivity;
    }
}
