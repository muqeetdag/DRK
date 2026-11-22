package android.app;

import android.content.Context;

/** Application shell. */
public class Application extends Context {

    private String processName;

    public void onCreate() {}

    public void onTerminate() {}

    public void onLowMemory() {}

    public void onTrimMemory(int level) {}

    public void registerActivityLifecycleCallbacks(Object callbacks) {}

    public void unregisterActivityLifecycleCallbacks(Object callbacks) {}

    public String getProcessName() { return processName == null ? getPackageName() : processName; }
}
