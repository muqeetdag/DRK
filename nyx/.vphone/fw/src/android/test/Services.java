package android.test;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Ambient platform services for the virtual phone: activity stack, toasts,
 * dialogs, clipboard and vibrator, so app calls have visible effects.
 */
public final class Services {

    public static final List<Activity> STACK = new ArrayList<>();
    public static final List<String> LOG = new ArrayList<>();
    public static final List<String> TOASTS = new ArrayList<>();
    public static final List<String> STARTED = new ArrayList<>();
    public static final List<Dialog> DIALOGS = new ArrayList<>();
    public static String clipboard = "";
    public static final List<Long> VIBRATIONS = new ArrayList<>();
    public static boolean moveTaskToBackCalled;
    public static boolean KILL_REQUESTED;
    public static boolean lastActivityWasLaunchedWithNewTask;

    private Services() {}

    // -------------------------------------------------------------- activities
    public static void push(Activity activity) { STACK.add(activity); }

    public static void finish(Activity activity) {
        int index = STACK.indexOf(activity);
        if (index >= 0) STACK.remove(index);
    }

    /** HOME: pauses + stops the activity without destroying it. */
    public static void homePressed() {
        Activity top = top();
        if (top != null) {
            top.dispatchPause();
            top.dispatchStop();
        }
        LOG.add("home");
    }

    /** Brings the paused task back to the foreground. */
    public static void resumeTop() {
        Activity top = top();
        if (top != null) {
            top.dispatchStart();
            top.dispatchResume();
        }
        LOG.add("resumed");
    }

    public static void moveTaskToBack(Activity activity) {
        moveTaskToBackCalled = true;
        finish(activity);
    }

    public static Activity top() {
        return STACK.isEmpty() ? null : STACK.get(STACK.size() - 1);
    }

    public static void startActivity(Context from, Intent intent) {
        if (intent == null) throw new NullPointerException("intent == null");
        String target = null;
        if (intent.getComponent() != null) {
            target = intent.getComponent().getClassName();
        } else if (intent.getData() != null) {
            target = "url:" + intent.getData();
        } else if (intent.getPackage() != null) {
            target = "pkg:" + intent.getPackage();
        }
        STARTED.add("activity:" + target);
        lastActivityWasLaunchedWithNewTask =
                (intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0;
        if (target != null && target.startsWith("io.nyx.app")) {
            Launcher.launch(target);
        }
    }

    // ------------------------------------------------------------------ toasts
    public static void toast(Toast toast, String text) {
        TOASTS.add(text);
        LOG.add("toast: " + text);
    }

    // ----------------------------------------------------------------- dialogs
    public static void showDialog(Dialog dialog) {
        DIALOGS.add(dialog);
    }

    public static void dismissDialog(Dialog dialog) {
        DIALOGS.remove(dialog);
    }

    public static Dialog visibleDialog() {
        return DIALOGS.isEmpty() ? null : DIALOGS.get(DIALOGS.size() - 1);
    }

    // --------------------------------------------------------------- services
    public static Object get(Context context, String name) {
        switch (name) {
            case Context.VIBRATOR_SERVICE:
                return new android.os.Vibrator();
            case Context.CLIPBOARD_SERVICE:
                return new android.content.ClipboardManager();
            case Context.WINDOW_SERVICE:
                return new android.view.WindowManager();
            case Context.NOTIFICATION_SERVICE:
                return new android.app.NotificationManager();
            default:
                return null;
        }
    }

    public static void clearDialogsOnly() { DIALOGS.clear(); }

    public static void clear() {
        STACK.clear();
        DIALOGS.clear();
        TOASTS.clear();
        STARTED.clear();
        LOG.clear();
        VIBRATIONS.clear();
        clipboard = "";
        moveTaskToBackCalled = false;
    }
}
