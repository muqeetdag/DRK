package android.content;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Looper;

import java.io.File;

/** Application/Activity context: resources, prefs, files, services. */
public class Context {

    public static final int MODE_PRIVATE = 0x0000;
    @Deprecated public static final int MODE_WORLD_READABLE = 0x0001;
    @Deprecated public static final int MODE_WORLD_WRITEABLE = 0x0002;
    public static final int MODE_APPEND = 0x8000;
    public static final int MODE_MULTI_PROCESS = 0x0004;

    public static final String VIBRATOR_SERVICE = "vibrator";
    public static final String CLIPBOARD_SERVICE = "clipboard";
    public static final String WINDOW_SERVICE = "window";
    public static final String LAYOUT_INFLATER_SERVICE = "layout_inflater";
    public static final String CONNECTIVITY_SERVICE = "connectivity";
    public static final String NOTIFICATION_SERVICE = "notification";
    public static final String MEDIA_STORE = "media";

    private Resources resources;
    private PackageManager packageManager;
    private File filesDir;
    private File cacheDir;
    private File externalDir;
    private android.content.SharedPreferences prefs;

    private boolean attached;

    public final void attach(Resources resources, File filesDir, File cacheDir, File externalDir,
                             SharedPreferences prefs) {
        this.externalDir = externalDir;
        this.attached = true;
        this.resources = resources;
        this.filesDir = filesDir;
        this.cacheDir = cacheDir;
        this.prefs = prefs;
        this.packageManager = new android.content.pm.VirtualPackageManager(this);
        this.resources.attachContext(this);
    }

    public Resources getResources() { return resources; }

    public PackageManager getPackageManager() { return packageManager; }

    public SharedPreferences getSharedPreferences(String name, int mode) {
        return prefs;
    }

    public File getFilesDir() { return filesDir; }

    public File getCacheDir() { return cacheDir; }

    public File getExternalFilesDir(String type) { return externalDir; }

    public File getExternalCacheDir() { return new File(externalDir, "cache"); }

    public String getPackageName() { return android.test.Env.packageName; }

    public String getOpPackageName() { return getPackageName(); }

    public android.content.pm.ApplicationInfo getApplicationInfo() {
        return ((android.content.pm.VirtualPackageManager) getPackageManager())
                .getApplicationInfoSafe(getPackageName());
    }

    public Context getApplicationContext() { return this; }

    public android.content.res.AssetManager getAssets() { return new android.content.res.AssetManager(); }

    public ClassLoader getClassLoader() { return Context.class.getClassLoader(); }

    public Object getSystemService(String name) {
        return android.test.Services.get(this, name);
    }

    public Looper getMainLooper() { return Looper.getMainLooper(); }

    public ContentResolver getContentResolver() { return new ContentResolver(this); }

    public String getString(int resId) { return resources.getString(resId); }

    public String getString(int resId, Object... formatArgs) {
        return String.format(resources.getString(resId), formatArgs);
    }

    public CharSequence getText(int resId) { return resources.getText(resId); }

    public int getColor(int resId) { return resources.getColor(resId); }

    public android.graphics.drawable.Drawable getDrawable(int resId) {
        return resources.getDrawable(resId);
    }

    public String[] getStringArray(int resId) { return resources.getStringArray(resId); }

    public void startActivity(Intent intent) {
        android.test.Services.startActivity(this, intent);
    }

    public void startActivityForResult(Intent intent, int requestCode) {
        startActivity(intent);
    }

    public void sendBroadcast(Intent intent) {}

    public void sendOrderedBroadcast(Intent intent, String receiverPermission) {}

    public Intent registerReceiver(Object receiver, android.content.IntentFilter filter) { return null; }

    public void unregisterReceiver(Object receiver) {}

    public int checkSelfPermission(String permission) { return PackageManager.PERMISSION_GRANTED; }

    public int checkCallingOrSelfPermission(String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    public boolean isRestricted() { return false; }

    public Context createConfigurationContext(Object config) { return this; }

    public Context createDeviceProtectedStorageContext() { return this; }

    public void setTheme(int resId) {}

    public android.content.res.Resources.Theme getTheme() { return resources.getTheme(); }

    public File getDir(String name, int mode) {
        File dir = new File(filesDir, name);
        dir.mkdirs();
        return dir;
    }

    public File getDatabasePath(String name) { return new File(filesDir, name); }

    public java.io.FileOutputStream openFileOutput(String name, int mode) throws java.io.FileNotFoundException {
        return new java.io.FileOutputStream(new File(filesDir, name));
    }

    public java.io.FileInputStream openFileInput(String name) throws java.io.FileNotFoundException {
        return new java.io.FileInputStream(new File(filesDir, name));
    }

    public boolean deleteFile(String name) { return new File(filesDir, name).delete(); }

    public String[] fileList() {
        String[] list = filesDir.list();
        return list == null ? new String[0] : list;
    }

    public android.os.Handler getMainHandler() { return new android.os.Handler(); }

    public Intent getPackageManagerIntent(String pkg) { return null; }
}
