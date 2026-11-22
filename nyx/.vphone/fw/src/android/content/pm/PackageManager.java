package android.content.pm;

import android.content.Intent;

import java.util.List;

/** PackageManager surface used by the app. */
public abstract class PackageManager {

    public static final int PERMISSION_GRANTED = 0;
    public static final int PERMISSION_DENIED = -1;
    public static final int GET_ACTIVITIES = 1;
    public static final int GET_META_DATA = 128;
    public static final int MATCH_DEFAULT_ONLY = 0x00010000;

    public abstract PackageInfo getPackageInfo(String packageName, int flags)
            throws NameNotFoundException;

    public abstract ApplicationInfo getApplicationInfo(String packageName, int flags)
            throws NameNotFoundException;

    public abstract Intent getLaunchIntentForPackage(String packageName);

    public abstract List<ResolveInfo> queryIntentActivities(Intent intent, int flags);

    public abstract String[] getPackagesForUid(int uid);

    public ApplicationInfo getApplicationInfoSafe(String packageName) {
        try {
            return getApplicationInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    public static class NameNotFoundException extends Exception {
        public NameNotFoundException() { super(); }
        public NameNotFoundException(String message) { super(message); }
    }
}
