package android.content;

import android.net.Uri;
import android.os.Bundle;

import java.util.LinkedHashMap;
import java.util.Map;

/** Intent with extras, actions and component. */
public class Intent {

    public static final String ACTION_MAIN = "android.intent.action.MAIN";
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public static final String ACTION_SEND = "android.intent.action.SEND";
    public static final String ACTION_SENDTO = "android.intent.action.SENDTO";
    public static final String ACTION_DIAL = "android.intent.action.DIAL";
    public static final String ACTION_EDIT = "android.intent.action.EDIT";
    public static final String ACTION_PICK = "android.intent.action.PICK";
    public static final String CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER";
    public static final String CATEGORY_DEFAULT = "android.intent.category.DEFAULT";
    public static final String CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE";
    public static final String EXTRA_TEXT = "android.intent.extra.TEXT";
    public static final String EXTRA_SUBJECT = "android.intent.extra.SUBJECT";
    public static final String EXTRA_STREAM = "android.intent.extra.STREAM";
    public static final String EXTRA_TITLE = "android.intent.extra.TITLE";
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    public static final int FLAG_ACTIVITY_CLEAR_TOP = 0x04000000;
    public static final int FLAG_ACTIVITY_SINGLE_TOP = 0x20000000;

    private String action;
    private Uri data;
    private ComponentName component;
    private String packageName;
    private final Map<String, Object> extras = new LinkedHashMap<>();
    private final java.util.Set<String> categories = new java.util.LinkedHashSet<>();
    private int flags;
    private int type;

    public Intent() {}

    public Intent(String action) { this.action = action; }

    public Intent(String action, Uri uri) {
        this.action = action;
        this.data = uri;
    }

    public Intent(Context packageContext, Class<?> cls) {
        setClass(packageContext, cls);
    }

    public Intent(Intent other) {
        this.action = other.action;
        this.data = other.data;
        this.component = other.component;
        this.packageName = other.packageName;
        this.extras.putAll(other.extras);
        this.categories.addAll(other.categories);
        this.flags = other.flags;
    }

    public String getAction() { return action; }

    public Intent setAction(String action) { this.action = action; return this; }

    public Uri getData() { return data; }

    public Intent setData(Uri data) { this.data = data; return this; }

    public String getType() { return null; }

    public Intent setType(String type) { return this; }

    public ComponentName getComponent() { return component; }

    public Intent setComponent(ComponentName component) { this.component = component; return this; }

    public Intent setClass(Context context, Class<?> cls) {
        this.component = new ComponentName(context.getPackageName(), cls.getName());
        return this;
    }

    public Intent setClassName(String packageName, String className) {
        this.component = new ComponentName(packageName, className);
        return this;
    }

    public String getPackage() { return packageName; }

    public Intent setPackage(String packageName) { this.packageName = packageName; return this; }

    public Intent addCategory(String category) { categories.add(category); return this; }

    public java.util.Set<String> getCategories() { return categories; }

    public boolean hasCategory(String category) { return categories.contains(category); }

    public int getFlags() { return flags; }

    public Intent setFlags(int flags) { this.flags = flags; return this; }

    public Intent addFlags(int flags) { this.flags |= flags; return this; }

    public Intent putExtra(String name, String value) { extras.put(name, value); return this; }

    public Intent putExtra(String name, int value) { extras.put(name, value); return this; }

    public Intent putExtra(String name, long value) { extras.put(name, value); return this; }

    public Intent putExtra(String name, boolean value) { extras.put(name, value); return this; }

    public Intent putExtra(String name, CharSequence value) { extras.put(name, value); return this; }

    public Intent putExtra(String name, String[] value) { extras.put(name, value); return this; }

    public Intent putExtras(Bundle bundle) { return this; }

    public Intent putExtra(String name, Bundle value) { extras.put(name, value); return this; }

    public String getStringExtra(String name) {
        Object v = extras.get(name);
        return v instanceof String ? (String) v : null;
    }

    public int getIntExtra(String name, int def) {
        Object v = extras.get(name);
        return v instanceof Integer ? (Integer) v : def;
    }

    public long getLongExtra(String name, long def) {
        Object v = extras.get(name);
        return v instanceof Long ? (Long) v : def;
    }

    public boolean getBooleanExtra(String name, boolean def) {
        Object v = extras.get(name);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    public CharSequence getCharSequenceExtra(String name) {
        Object v = extras.get(name);
        return v instanceof CharSequence ? (CharSequence) v : null;
    }

    public Bundle getExtras() { return null; }

    public boolean hasExtra(String name) { return extras.containsKey(name); }

    public void removeExtra(String name) { extras.remove(name); }

    public static Intent createChooser(Intent target, CharSequence title) {
        return target == null ? new Intent(ACTION_SEND) : target;
    }

    public static Intent makeMainActivity(ComponentName component) {
        return new Intent(ACTION_MAIN).setComponent(component);
    }

    @Override public String toString() {
        return "Intent{" + action + (data != null ? " " + data : "") + "}";
    }
}
