package io.nyx.app.hook;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XSharedPreferences;

/**
 * Reads the companion app's settings from inside Instagram.
 *
 * Sources, most-direct first:
 *  1. XSharedPreferences — the sanctioned LSPosed path. LSPosed relocates the
 *     module's prefs to /data/misc/…/prefs/<pkg>/ when the module declares
 *     xposedsharedprefs (we do) or xposedminversion > 92 (we are 93), and
 *     XSharedPreferences resolves that path itself.
 *  2. The prefs XML written by the console, read directly from the module's
 *     data dir. The hook runs inside Instagram's uid, so this only works when
 *     the file was made world-readable — LSPosed permits MODE_WORLD_READABLE
 *     for a module that set xposedsharedprefs, which is exactly why the console
 *     opens its prefs that way.
 *  3. The mirror file in the module's files/ dir (owner-only on modern Android,
 *     kept as a last resort / for LSPatch where the fork runs the module in its
 *     own app process).
 *  4. Build-time defaults compiled into the hook itself.
 *
 * A failed XSharedPreferences construction is NOT treated as permanent: the
 * manager may still be starting up (boot race), and latching that forever was
 * why toggles appeared to do nothing until Instagram was force-stopped.
 */
public final class PrefsReader {

    private static final String MIRROR = "nyx_prefs_mirror.xml";
    private static final String PREFS_FILE = "nyx_prefs";

    /** Package id of this build; rewritten for the LSPatch variant by configure.py. */
    private static final String MODULE_PKG = "io.nyx.app__PKG_SUFFIX__";

    private static XSharedPreferences xsp;
    private static String xspPkg;
    private static boolean xspBroken = false;
    private static long xspRetryAt = 0L;
    private static Map<String, Object> mirror = new HashMap<>();
    private static long mirrorAt = 0L;
    private static String filesDir;
    private static String dataDir;
    private static long xspAt = 0L;
    private static long lastTouch = 0L;
    private static final Map<String, Object> CACHE = new HashMap<>();

    private PrefsReader() {}

    public static boolean get(String key, boolean fallback) {
        Object value = value(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return fallback;
    }

    public static String get(String key, String fallback) {
        Object value = value(key);
        return value instanceof String ? (String) value : fallback;
    }

    public static int getInt(String key, int fallback) {
        Object value = value(key);
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }

    private static Object value(String key) {
        long now = System.currentTimeMillis();
        lastTouch = now;

        Object fromXsp = fromXSharedPreferences(key, now);
        if (fromXsp != null) return fromXsp;

        Object fromPrefsXml = fromPrefsXml(key, now);
        if (fromPrefsXml != null) return fromPrefsXml;

        return fromMirror(key);
    }

    /** Path 1 — the sanctioned LSPosed route. */
    private static Object fromXSharedPreferences(String key, long now) {
        if (xspBroken && now < xspRetryAt) return null;
        try {
            if (xsp == null || !MODULE_PKG.equals(xspPkg)) {
                xsp = new XSharedPreferences(MODULE_PKG, PREFS_FILE);
                xspPkg = MODULE_PKG;
                xspBroken = false;
                HookBridge.log("XSharedPreferences → " + MODULE_PKG + "/" + PREFS_FILE);
            }
            if (now - xspAt > 400) {
                xsp.reload();
                xspAt = now;
            }
            Map<String, ?> all = xsp.getAll();
            if (all != null && !all.isEmpty()) {
                Object hit = all.get(key);
                if (hit != null) return hit;
            }
        } catch (Throwable t) {
            // Retry in a bit instead of latching: the manager may not have
            // relocated the prefs yet during boot.
            xspBroken = true;
            xspRetryAt = now + 5000L;
            HookBridge.warn("XSharedPreferences", t);
        }
        return null;
    }

    /** Path 2 — the console's own prefs XML, read straight off disk. */
    private static Object fromPrefsXml(String key, long now) {
        long cached = prefsXmlAt;
        if (now - cached < 500) {
            Object hit = prefsXml.get(key);
            if (hit != null) return hit;
            return null;
        }
        prefsXmlAt = now;
        prefsXml = readPrefsXml();
        return prefsXml.get(key);
    }

    private static Map<String, Object> prefsXml = new HashMap<>();
    private static long prefsXmlAt = 0L;

    private static Map<String, Object> readPrefsXml() {
        Map<String, Object> out = new HashMap<>();
        String dir = dataDir;
        if (dir == null) return out;
        File file = new File(new File(dir, "shared_prefs"), PREFS_FILE + ".xml");
        if (!file.exists() || !file.canRead()) return out;
        return parsePrefsXml(file, out);
    }

    /** Path 3 — the mirror written by the console into files/. */
    private static Object fromMirror(String key) {
        long now = System.currentTimeMillis();
        if (now - mirrorAt > 300 || mirror.isEmpty()) {
            mirrorAt = now;
            mirror = readMirror();
        }
        return mirror.get(key);
    }

    private static Map<String, Object> readMirror() {
        File file = mirrorFile();
        if (file == null || !file.exists() || !file.canRead()) return new HashMap<>();
        return parsePrefsXml(file, new HashMap<>());
    }

    /**
     * Parses an Android prefs XML document (&lt;map&gt; of boolean/int/long/float/
     * string entries). One parser for both the prefs file and the mirror.
     */
    private static Map<String, Object> parsePrefsXml(File file, Map<String, Object> out) {
        try {
            StringBuilder raw = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) raw.append(line).append('\n');
            }
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<(boolean|int|long|float|string)\\s+name=\"([^\"]+)\""
                            + "(?:\\s+value=\"([^\"]*)\")?[^>]*>(?:([^<]*))?")
                    .matcher(raw);
            while (m.find()) {
                String type = m.group(1);
                String name = m.group(2);
                String attr = m.group(3);
                String body = m.group(4);
                String value = attr != null ? attr : (body == null ? "" : body);
                if ("boolean".equals(type)) {
                    out.put(name, Boolean.parseBoolean(value));
                } else if ("int".equals(type)) {
                    try {
                        out.put(name, Integer.parseInt(value.trim()));
                    } catch (Throwable ignored) {
                    }
                } else if ("long".equals(type)) {
                    try {
                        out.put(name, (int) Long.parseLong(value.trim()));
                    } catch (Throwable ignored) {
                    }
                } else if ("float".equals(type)) {
                    try {
                        out.put(name, Float.parseFloat(value.trim()));
                    } catch (Throwable ignored) {
                    }
                } else {
                    out.put(name, value);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** The console writes its mirror inside the module's own storage. */
    public static void setFilesDir(String dir) {
        filesDir = dir;
    }

    /**
     * Resolve the module's data dir without guessing the /data/app layout.
     *
     * The old code took `…/io.nyx.app-xyz/base.apk` and walked up one directory
     * too far, producing /data/app/files instead of /data/user/0/io.nyx.app/files,
     * so every prefs read silently fell through to the compiled-in defaults —
     * that is "the toggles do nothing" bug. /data/user/0/&lt;pkg&gt; is stable on
     * every Android version we support (21+) and needs no root to construct.
     */
    static void bindDataDir() {
        try {
            dataDir = "/data/user/0/" + MODULE_PKG;
            filesDir = dataDir + "/files";
            HookBridge.log("prefs dir → " + dataDir + " (files readable="
                    + new File(filesDir).canRead() + ")");
        } catch (Throwable t) {
            HookBridge.warn("prefs dir", t);
        }
    }

    /** Absolute path of the module's files/ dir, or null before it is resolved. */
    public static String filesDir() {
        return filesDir;
    }

    /** Raw parsed values, for features that need to enumerate keys. */
    public static java.util.Map<String, Object> snapshot() {
        Map<String, Object> merged = new HashMap<>();
        try {
            merged.putAll(readPrefsXml());
            merged.putAll(readMirror());
        } catch (Throwable ignored) {
        }
        return merged;
    }

    private static File mirrorFile() {
        if (filesDir == null) return null;
        return new File(filesDir, MIRROR);
    }

    public static void bindContext(Context ctx) {
        try {
            if (ctx != null) {
                dataDir = ctx.getApplicationInfo().dataDir;
                filesDir = ctx.getFilesDir().getAbsolutePath();
            }
        } catch (Throwable ignored) {
        }
    }
}
