package android.os;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bundle with the put/get surface the app uses. */
public class Bundle {
    private final Map<String, Object> map = new LinkedHashMap<>();

    public Bundle() {}

    public Bundle(Bundle other) { if (other != null) map.putAll(other.map); }

    public boolean containsKey(String key) { return map.containsKey(key); }

    public void putString(String key, String value) { map.put(key, value); }
    public void putInt(String key, int value) { map.put(key, value); }
    public void putLong(String key, long value) { map.put(key, value); }
    public void putBoolean(String key, boolean value) { map.put(key, value); }
    public void putCharSequence(String key, CharSequence value) { map.put(key, value); }
    public void putStringArray(String key, String[] value) { map.put(key, value); }
    public void putParcelable(String key, Object value) { map.put(key, value); }
    public void putAll(Bundle other) { if (other != null) map.putAll(other.map); }
    public void remove(String key) { map.remove(key); }
    public void clear() { map.clear(); }
    public int size() { return map.size(); }
    public boolean isEmpty() { return map.isEmpty(); }
    public java.util.Set<String> keySet() { return map.keySet(); }

    public String getString(String key) { return map.get(key) instanceof String ? (String) map.get(key) : null; }
    public String getString(String key, String def) { String v = getString(key); return v == null ? def : v; }
    public int getInt(String key) { return getInt(key, 0); }
    public int getInt(String key, int def) { return map.get(key) instanceof Integer ? (Integer) map.get(key) : def; }
    public long getLong(String key) { return getLong(key, 0L); }
    public long getLong(String key, long def) { return map.get(key) instanceof Long ? (Long) map.get(key) : def; }
    public boolean getBoolean(String key) { return getBoolean(key, false); }
    public boolean getBoolean(String key, boolean def) { return map.get(key) instanceof Boolean ? (Boolean) map.get(key) : def; }
    public CharSequence getCharSequence(String key) { Object v = map.get(key); return v instanceof CharSequence ? (CharSequence) v : null; }
    public String[] getStringArray(String key) { return map.get(key) instanceof String[] ? (String[]) map.get(key) : null; }
    public CharSequence[] getCharSequenceArray(String key) { return null; }
}
