package android.content;

import android.util.Xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Real SharedPreferences backed by an XML file in the app's data dir — the same
 * format Android writes, so the prefs mirror the hook reads parses identically.
 */
public class SharedPreferencesImpl implements SharedPreferences {

    private final File file;
    private final Map<String, Object> map = new LinkedHashMap<>();
    private final List<OnSharedPreferenceChangeListener> listeners = new ArrayList<>();
    private int mode;

    public SharedPreferencesImpl(File file, int mode) {
        this.file = file;
        this.mode = mode;
        load();
    }

    public File getFile() { return file; }

    public int getMode() { return mode; }

    private synchronized void load() {
        map.clear();
        if (file == null || !file.exists()) return;
        try {
            android.util.Xml xml = new android.util.Xml();
            xml.parse(file);
            for (Map.Entry<String, Object> e : xml.values.entrySet()) map.put(e.getKey(), e.getValue());
        } catch (Throwable ignored) {
        }
    }

    public void reload() { load(); }

    @Override public synchronized Map<String, ?> getAll() { return new LinkedHashMap<>(map); }

    @Override public synchronized String getString(String key, String defValue) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : defValue;
    }

    @Override public synchronized Set<String> getStringSet(String key, Set<String> defValues) {
        Object value = map.get(key);
        if (value instanceof Set) {
            //noinspection unchecked
            return (Set<String>) value;
        }
        return defValues;
    }

    @Override public synchronized int getInt(String key, int defValue) {
        Object value = map.get(key);
        return value instanceof Integer ? (Integer) value : defValue;
    }

    @Override public synchronized long getLong(String key, long defValue) {
        Object value = map.get(key);
        return value instanceof Long ? (Long) value : defValue;
    }

    @Override public synchronized float getFloat(String key, float defValue) {
        Object value = map.get(key);
        return value instanceof Float ? (Float) value : defValue;
    }

    @Override public synchronized boolean getBoolean(String key, boolean defValue) {
        Object value = map.get(key);
        return value instanceof Boolean ? (Boolean) value : defValue;
    }

    @Override public synchronized boolean contains(String key) { return map.containsKey(key); }

    @Override public Editor edit() { return new EditorImpl(); }

    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        listeners.add(l);
    }

    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        listeners.remove(l);
    }

    private synchronized void commitToDisk() {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n");
            for (Map.Entry<String, Object> e : map.entrySet()) {
                Object v = e.getValue();
                String name = escape(e.getKey());
                if (v instanceof Boolean) {
                    xml.append("    <boolean name=\"").append(name).append("\" value=\"")
                            .append(v).append("\" />\n");
                } else if (v instanceof Integer) {
                    xml.append("    <int name=\"").append(name).append("\" value=\"")
                            .append(v).append("\" />\n");
                } else if (v instanceof Long) {
                    xml.append("    <long name=\"").append(name).append("\" value=\"")
                            .append(v).append("\" />\n");
                } else if (v instanceof Float) {
                    xml.append("    <float name=\"").append(name).append("\" value=\"")
                            .append(v).append("\" />\n");
                } else if (v instanceof String) {
                    xml.append("    <string name=\"").append(name).append("\">")
                            .append(escape((String) v)).append("</string>\n");
                }
            }
            xml.append("</map>\n");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(xml.toString().getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        }
    }

    private static String escape(String raw) {
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private final class EditorImpl implements Editor {
        private final Map<String, Object> changes = new LinkedHashMap<>();
        private boolean clear;

        @Override public Editor putString(String key, String value) { changes.put(key, value); return this; }

        @Override public Editor putStringSet(String key, Set<String> values) { changes.put(key, values); return this; }

        @Override public Editor putInt(String key, int value) { changes.put(key, value); return this; }

        @Override public Editor putLong(String key, long value) { changes.put(key, value); return this; }

        @Override public Editor putFloat(String key, float value) { changes.put(key, value); return this; }

        @Override public Editor putBoolean(String key, boolean value) { changes.put(key, value); return this; }

        @Override public Editor remove(String key) { changes.put(key, REMOVED); return this; }

        @Override public Editor clear() { clear = true; return this; }

        @Override public boolean commit() {
            synchronized (SharedPreferencesImpl.this) {
                if (clear) map.clear();
                List<String> changed = new ArrayList<>();
                for (Map.Entry<String, Object> e : changes.entrySet()) {
                    if (e.getValue() == REMOVED) map.remove(e.getKey());
                    else {
                        map.put(e.getKey(), e.getValue());
                        changed.add(e.getKey());
                    }
                }
                commitToDisk();
                for (String key : changed) {
                    for (OnSharedPreferenceChangeListener l : new ArrayList<>(listeners)) {
                        l.onSharedPreferenceChanged(SharedPreferencesImpl.this, key);
                    }
                }
            }
            return true;
        }

        @Override public void apply() { commit(); }
    }

    private static final Object REMOVED = new Object();
}
