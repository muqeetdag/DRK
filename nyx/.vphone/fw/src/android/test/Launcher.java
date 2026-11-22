package android.test;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;

/** Instantiates and drives my activities inside the emulator. */
public final class Launcher {

    public interface Factory { Activity create(); }

    private static final Map<String, Factory> FACTORIES = new HashMap<>();

    private Launcher() {}

    public static void register(String className, Factory factory) {
        FACTORIES.put(className, factory);
    }

    public static void launch(String className) {
        Activity activity = (Activity) Services.top();
        if (activity != null && activity.getClass().getName().equals(className)) return;
        launch(className, new Intent());
    }

    public static boolean has(String className) {
        return FACTORIES.containsKey(className);
    }

    public static void launch(String className, Intent intent) {
        Factory factory = FACTORIES.get(className);
        if (factory == null) {
            Services.LOG.add("launch-miss: " + className);
            return;
        }
        Activity current = Services.top();
        if (current != null) {
            try {
                current.dispatchPause();
                current.dispatchStop();
                current.dispatchDestroy();
            } catch (Throwable t) {
                System.out.println("[launcher] teardown of " + current.getClass().getName()
                        + " threw: " + t);
                t.printStackTrace(System.out);
            }
            Services.STACK.clear();
        }
        Activity activity = factory.create();
        activity.setIntent(intent);
        Services.clearDialogsOnly();
        Services.push(activity);
        try {
            activity.dispatchCreate(null);
        } catch (Throwable t) {
            Services.LOG.add("launch-threw: " + t);
            System.out.println("[launcher] " + className + " threw during onCreate:");
            t.printStackTrace(System.out);
            throw t;
        }
        activity.dispatchStart();
        activity.dispatchResume();
        Services.LOG.add("launched: " + className);
    }
}
