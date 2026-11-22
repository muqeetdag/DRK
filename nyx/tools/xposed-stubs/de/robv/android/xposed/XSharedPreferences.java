package de.robv.android.xposed;

import java.io.File;
import java.util.Map;

/** Compile-time stub of the Xposed API (provided at runtime by LSPosed). */
public class XSharedPreferences {
    public XSharedPreferences(String packageName, String prefFileName) {}
    public XSharedPreferences(String packageName) {}
    public XSharedPreferences(File prefFile) {}
    public void reload() {}
    public boolean getBoolean(String key, boolean defValue) { return defValue; }
    public String getString(String key, String defValue) { return defValue; }
    public int getInt(String key, int defValue) { return defValue; }
    public long getLong(String key, long defValue) { return defValue; }
    public float getFloat(String key, float defValue) { return defValue; }
    public boolean contains(String key) { return false; }
    public Map<String, ?> getAll() { return java.util.Collections.emptyMap(); }
    public boolean hasFileChanged() { return false; }
    public boolean makeWorldReadable() { return false; }
    public File getFile() { return new File("/data/misc/xposed/prefs.xml"); }
}
