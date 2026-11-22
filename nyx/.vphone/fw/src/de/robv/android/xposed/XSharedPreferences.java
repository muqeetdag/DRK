package de.robv.android.xposed;

import java.io.File;
import java.util.Map;

/** Virtual-phone runtime double of the Xposed API. */
public class XSharedPreferences {
    private final File file;

    public XSharedPreferences(String packageName, String prefFileName) {
        this.file = new File("/data/misc/xposed/prefs/" + packageName + "/" + prefFileName + ".xml");
    }
    public XSharedPreferences(String packageName) { this(packageName, packageName + "_preferences"); }
    public XSharedPreferences(File prefFile) { this.file = prefFile; }
    public void reload() {}
    public boolean getBoolean(String key, boolean defValue) { return defValue; }
    public String getString(String key, String defValue) { return defValue; }
    public int getInt(String key, int defValue) { return defValue; }
    public long getLong(String key, long defValue) { return defValue; }
    public float getFloat(String key, float defValue) { return defValue; }
    public boolean contains(String key) { return false; }
    public Map<String, ?> getAll() { return java.util.Collections.emptyMap(); }
    public boolean hasFileChanged() { return false; }
    public boolean makeWorldReadable() { return true; }
    public File getFile() { return file; }
}
