package android.content.res;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resource table for the virtual phone.
 *
 * Entries are registered from aapt2's dump of the real APK, so the IDs and
 * values here are byte-identical to what the device would read. A missing
 * entry throws NotFoundException exactly like Android does — that is how a
 * "crash on open" caused by a bad resource reference gets caught in the
 * terminal instead of on the phone.
 */
public class Resources {

    public static final class Res {
        public int id;
        public String type;
        public String name;
        public Object value;          // Integer (colour), String (string), String[] (array)
        public File file;             // drawable xml / png path on disk
        public String parent;
        public final Map<String, String> styleItems = new LinkedHashMap<>();
        public String[] arrayItems;
    }

    private final Map<Integer, Res> byId = new HashMap<>();
    private final Map<String, Res> byName = new HashMap<>();
    private final DisplayMetrics metrics = new DisplayMetrics();
    private final Theme theme = new Theme();
    private Context context;
    private File resDir;

    public void attachContext(Context context) { this.context = context; }

    public Context context() { return context; }

    public void setResDir(File dir) { resDir = dir; }

    public File resDir() { return resDir; }

    public DisplayMetrics getDisplayMetrics() { return metrics; }

    public Configuration getConfiguration() { return new Configuration(); }

    public Theme getTheme() { return theme; }

    public Res put(String type, int id, String name, Object value, File file, String parent) {
        Res res = new Res();
        res.id = id;
        res.type = type;
        res.name = name;
        res.value = value;
        res.file = file;
        res.parent = parent;
        byId.put(id, res);
        byName.put(type + "/" + name, res);
        return res;
    }

    public Res find(String type, String name) { return byName.get(type + "/" + name); }

    public Res byId(int id) { return byId.get(id); }

    /** All registered entries, for the coverage report. */
    public Map<String, Res> entries() { return byName; }

    // ------------------------------------------------------------------ access
    public String getString(int resId) {
        Res res = require(resId, "string");
        return String.valueOf(res.value);
    }

    public CharSequence getText(int resId) { return getString(resId); }

    public int getColor(int resId) {
        Res res = require(resId, "color");
        if (res.value instanceof Integer) return (Integer) res.value;
        throw new NotFoundException("resource 0x" + Integer.toHexString(resId) + " is not a colour");
    }

    public String[] getStringArray(int resId) {
        Res res = byId.get(resId);
        if (res == null) throw new NotFoundException("array ID #0x" + Integer.toHexString(resId));
        if (res.arrayItems != null) return res.arrayItems;
        if (res.value instanceof String[]) return (String[]) res.value;
        return new String[0];
    }

    public CharSequence[] getTextArray(int resId) { return getStringArray(resId); }

    public Drawable getDrawable(int resId) { return getDrawable(resId, null); }

    public Drawable getDrawable(int resId, Theme unused) {
        Res res = byId.get(resId);
        if (res == null) throw new NotFoundException("resource ID #0x" + Integer.toHexString(resId));
        if (res.file != null && res.file.exists()) {
            if (res.file.getName().endsWith(".xml")) {
                return android.graphics.drawable.DrawableFactory.fromXml(res.file);
            }
            android.graphics.Bitmap bitmap = android.graphics.Bitmaps.load(res.file.getAbsolutePath());
            if (bitmap != null) return new android.graphics.drawable.BitmapDrawable(bitmap);
        }
        if (res.value instanceof Integer) {
            return new android.graphics.drawable.ColorDrawable((Integer) res.value);
        }
        throw new NotFoundException("resource ID #0x" + Integer.toHexString(resId)
                + " (" + res.type + "/" + res.name + ") has no drawable content");
    }

    public float getDimension(int resId) { return 0f; }

    public int getDimensionPixelSize(int resId) { return 0; }

    public int getDimensionPixelOffset(int resId) { return 0; }

    public int getInteger(int resId) {
        Res res = byId.get(resId);
        return res != null && res.value instanceof Integer ? (Integer) res.value : 0;
    }

    public boolean getBoolean(int resId) {
        Res res = byId.get(resId);
        return res != null && res.value instanceof Boolean && (Boolean) res.value;
    }

    public String getResourceName(int resId) {
        Res res = byId.get(resId);
        return res == null ? null : "io.nyx.app:" + res.type + "/" + res.name;
    }

    public String getResourceEntryName(int resId) {
        Res res = byId.get(resId);
        return res == null ? null : res.name;
    }

    public int getIdentifier(String name, String type, String pkg) {
        Res res = byName.get(type + "/" + name);
        return res == null ? 0 : res.id;
    }

    private Res require(int resId, String type) {
        Res res = byId.get(resId);
        if (res == null) {
            throw new NotFoundException(type + " resource ID #0x" + Integer.toHexString(resId));
        }
        return res;
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    /** Style/theme container. Attribute lookups fall back to defaults. */
    public class Theme {
        private Res style;

        public void applyStyle(int resId, boolean force) {
            Res found = byId.get(resId);
            if (found != null) style = found;
        }

        public int getColor(int attr, int defValue) { return defValue; }

        public void setToDefault() { style = null; }

        public void setTo(Theme other) { style = other.style; }

        public Res style() { return style; }
    }

    /** Minimal Configuration. */
    public static class Configuration {
        public int orientation = 1;
        public int screenWidthDp = 411;
        public int screenHeightDp = 891;
        public int densityDpi = android.test.Env.densityDpi;
        public int uiMode = 0x21;
        public java.util.Locale locale = java.util.Locale.US;
    }
}
