package io.nyx.app.hook;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XSharedPreferences;

/**
 * Reads the companion app's settings from inside Instagram.
 *
 * Three sources, most-direct first:
 *  1. XSharedPreferences — the sanctioned LSPosed path.
 *  2. A plain XML copy of the prefs that the console writes to app storage
 *     (always readable), parsed with a tiny tag scanner.
 *  3. Build-time defaults compiled into the hook itself.
 *
 * Nothing here needs root file access, so it also works under LSPatch, and a
 * change made in the console is visible within ~300 ms.
 */
public final class PrefsReader {

    private static final String MIRROR = "nyx_prefs_mirror.xml";

    private static XSharedPreferences xsp;
    private static boolean xspFailed = false;
    private static Map<String, Object> mirror = new HashMap<>();
    private static long mirrorAt = 0L;
    private static String filesDir;
    private static long xspAt = 0L;
    private static long xspRetryAt = 0L;
    private static final Map<String, Object> CACHE = new HashMap<>();

    private PrefsReader() {}

    static long lastTouch = 0L;

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
        if (xspFailed && now - lastTouch < 300) return fromMirror(key);
        lastTouch = now;

        if (!xspFailed) {
            try {
                if (xsp == null) xsp = new XSharedPreferences("io.nyx.app", "nyx_prefs");
                if (now - xspAt > 400) {
                    xsp.reload();
                    xspAt = now;
                }
                if (xsp.hasFileChanged() || xsp.contains(key)) {
                    Object read = readXsp(key);
                    if (read != null) return read;
                }
            } catch (Throwable t) {
                xspFailed = true;
            }
        }
        return fromMirror(key);
    }

    private static Object readXsp(String key) {
        try {
            if (xsp.contains(key)) {
                // getAll() avoids guessing the type per key
                Object value = xsp.getAll().get(key);
                if (value != null) return value;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object fromMirror(String key) {
        long now = System.currentTimeMillis();
        if (now - mirrorAt > 300 || mirror.isEmpty()) {{
            mirrorAt = now;
            mirror = readMirror();
        }}
        return mirror.get(key);
    }

    private static Map<String, Object> readMirror() {
        Map<String, Object> out = new HashMap<>();
        try {
            File file = mirrorFile();
            if (file == null || !file.exists()) return out;
            StringBuilder raw = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) raw.append(line).append('\n');
            }
            // <boolean name="key" value="true" /> / <string name="key">v</string>
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<(boolean|int|string|long)\\s+name=\\\"([^\\\"]+)\\\"(?:\\s+value=\\\"([^\\\"]*)\\\")?[^>]*>(?:([^<]*))?")
                    .matcher(raw);
            while (m.find()) {
                String type = m.group(1);
                String name = m.group(2);
                String attr = m.group(3);
                String body = m.group(4);
                String value = attr != null ? attr : (body == null ? "" : body);
                if ("boolean".equals(type)) out.put(name, Boolean.parseBoolean(value));
                else if ("int".equals(type) || "long".equals(type)) {
                    try {
                        out.put(name, Integer.parseInt(value.trim()));
                    } catch (Throwable ignored) {
                    }
                } else out.put(name, value);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** The console writes its mirror inside the module's own storage. */
    public static void setFilesDir(String dir) {
        filesDir = dir;
    }

    private static File mirrorFile() {
        if (filesDir == null) return null;
        return new File(filesDir, MIRROR);
    }

    public static void bindContext(Context ctx) {
        try {
            if (ctx != null) setFilesDir(ctx.getFilesDir().getAbsolutePath());
        } catch (Throwable ignored) {
        }
    }
}
