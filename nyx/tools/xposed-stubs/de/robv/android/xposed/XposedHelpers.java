package de.robv.android.xposed;

/** Compile-time stub of the Xposed API (provided at runtime by LSPosed). */
public final class XposedHelpers {

    /** Real API: overwrite a static field by reflection. */
    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        throw new UnsupportedOperationException("stub");
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        throw new UnsupportedOperationException("stub");
    }

    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader,
                                                          String methodName, Object... parameterTypesAndCallback) {
        return new XC_MethodHook.Unhook();
    }
    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName,
                                                          Object... parameterTypesAndCallback) {
        return new XC_MethodHook.Unhook();
    }
    public static Class<?> findClass(String className, ClassLoader classLoader) { return null; }
    public static Object getObjectField(Object obj, String fieldName) { return null; }
    public static Object callMethod(Object obj, String methodName, Object... args) { return null; }
    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) { return null; }
    public static Object newInstance(Class<?> clazz, Object... args) { return null; }
    public static void setObjectField(Object obj, String fieldName, Object value) {}
}
