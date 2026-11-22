package android.util;

import java.util.HashMap;
import java.util.Map;

/** Minimal SparseArray. */
public class SparseArray<E> {
    private final Map<Integer, E> map = new HashMap<>();
    public void put(int key, E value) { map.put(key, value); }
    public E get(int key) { return map.get(key); }
    public E get(int key, E defaultValue) { return map.containsKey(key) ? map.get(key) : defaultValue; }
    public void remove(int key) { map.remove(key); }
    public void delete(int key) { map.remove(key); }
    public int size() { return map.size(); }
    public int keyAt(int index) { return map.keySet().iterator().next(); }
    public E valueAt(int index) { return map.values().iterator().next(); }
    public void clear() { map.clear(); }
}
