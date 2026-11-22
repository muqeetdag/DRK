package android.content;

/** (package, class) pair. */
public class ComponentName {
    private final String pkg;
    private final String cls;

    public ComponentName(String pkg, String cls) {
        this.pkg = pkg;
        this.cls = cls;
    }

    public ComponentName(Context context, Class<?> cls) {
        this(context.getPackageName(), cls.getName());
    }

    public String getPackageName() { return pkg; }

    public String getClassName() { return cls; }

    public String flattenToString() { return pkg + "/" + cls; }

    public static ComponentName unflattenFromString(String str) {
        if (str == null) return null;
        int slash = str.indexOf('/');
        return slash < 0 ? null : new ComponentName(str.substring(0, slash), str.substring(slash + 1));
    }

    @Override public boolean equals(Object o) {
        return o instanceof ComponentName && pkg.equals(((ComponentName) o).pkg)
                && cls.equals(((ComponentName) o).cls);
    }

    @Override public int hashCode() { return pkg.hashCode() * 31 + cls.hashCode(); }

    @Override public String toString() { return "ComponentInfo{" + pkg + "/" + cls + "}"; }
}
