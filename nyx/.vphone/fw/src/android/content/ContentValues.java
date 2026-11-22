package android.content;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Key/value map used by ContentResolver inserts. */
public class ContentValues {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public void put(String key, String value) { values.put(key, value); }
    public void put(String key, Integer value) { values.put(key, value); }
    public void put(String key, Long value) { values.put(key, value); }
    public void put(String key, Boolean value) { values.put(key, value); }
    public void put(String key, byte[] value) { values.put(key, value); }
    public Object get(String key) { return values.get(key); }
    public String getAsString(String key) { Object v = values.get(key); return v == null ? null : v.toString(); }
    public Integer getAsInteger(String key) { Object v = values.get(key); return v instanceof Integer ? (Integer) v : null; }
    public void clear() { values.clear(); }
    public void remove(String key) { values.remove(key); }
    public boolean containsKey(String key) { return values.containsKey(key); }
    public Set<String> keySet() { return values.keySet(); }
    public int size() { return values.size(); }
}
