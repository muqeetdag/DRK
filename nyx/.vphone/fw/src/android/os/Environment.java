package android.os;

import java.io.File;

/** Storage locations for the virtual phone (sandboxed to the workspace). */
public class Environment {

    public static final String DIRECTORY_DOWNLOADS = "Download";
    public static final String DIRECTORY_PICTURES = "Pictures";
    public static final String DIRECTORY_MOVIES = "Movies";
    public static final String DIRECTORY_DCIM = "DCIM";
    public static final String DIRECTORY_DOCUMENTS = "Documents";
    public static final String DIRECTORY_MUSIC = "Music";
    public static final String MEDIA_MOUNTED = "mounted";

    private static File root;

    public static void setRoot(File dir) { root = dir; }

    public static File getExternalStorageDirectory() { return storage(); }

    public static File getExternalStoragePublicDirectory(String type) {
        File dir = new File(storage(), type == null ? "" : type);
        dir.mkdirs();
        return dir;
    }

    public static File getDataDirectory() { return storage(); }

    public static File getDownloadCacheDirectory() { return new File(storage(), "cache"); }

    public static String getExternalStorageState() { return MEDIA_MOUNTED; }

    public static boolean isExternalStorageEmulated() { return true; }

    public static boolean isExternalStorageRemovable() { return false; }

    private static File storage() {
        if (root == null) {
            root = new File(System.getProperty("user.dir"), "vphone-sdcard");
        }
        if (!root.exists()) //noinspection ResultOfMethodCallIgnored
            root.mkdirs();
        return root;
    }
}
