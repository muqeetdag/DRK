package io.nyx.app.hook;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Ghost mode: everything that changes what OTHER people can see about you.
 *
 * Design rules that keep Instagram alive:
 *  • Every hook is installed inside its own try/catch, so a renamed method in a
 *    new Instagram build costs that one feature and nothing else.
 *  • Hooks are attached to *direct-message and story* plumbing only, never to
 *    hot paths such as View#onDraw or the network thread, so there is no
 *    measurable slowdown and no chance of an ANR.
 *  • Return values are only overridden when the toggle is on; when it is off the
 *    original result flows through untouched.
 *
 * The methods below exist across a wide Instagram version range; each is looked
 * up by several candidate names so a rename degrades to "feature off" instead of
 * a crash.
 */
final class GhostMode {

    /** Result the host expects for "do not tell anyone". */
    private static final Object BLOCK = Boolean.FALSE;

    private GhostMode() {}

    static void install(ClassLoader loader) {
        directMessages(loader);
        stories(loader);
        presence(loader);
        screenshots(loader);
    }

    // ------------------------------------------------------------ DM receipts
    private static void directMessages(ClassLoader loader) {
        // Seen receipts: several generations of the client use these classes.
        hookVoid(loader, "hide dm seen", PrefsKeys.HIDE_DM_SEEN,
                "com.instagram.direct.model.DirectThreadKey",
                new String[]{"markAsSeen", "sendSeen", "markThreadSeen"});

        for (String cls : new String[]{
                "com.instagram.direct.messaging.DirectSendSeenMutation",
                "com.instagram.direct.armadilloexpress.transportpayload.Seen",
                "com.instagram.direct.msys.plugins.DirectSeenPlugin"}) {
            hookReturnFalse(loader, "hide dm seen (" + simple(cls) + ")",
                    PrefsKeys.HIDE_DM_SEEN, cls,
                    new String[]{"send", "execute", "run", "markSeen"});
        }

        // Typing indicator.
        hookVoid(loader, "hide typing", PrefsKeys.HIDE_TYPING,
                "com.instagram.direct.messaging.DirectSendTypingStateMutation",
                new String[]{"send", "execute", "run"});
        hookReturnTrue(loader, "hide typing", PrefsKeys.HIDE_TYPING,
                "com.instagram.direct.presence.DirectTypingState",
                new String[]{"isTyping", "shouldSendTyping"});
    }

    // --------------------------------------------------------------- stories
    private static void stories(ClassLoader loader) {
        // Story view receipts are a "seen" upload keyed by the reel id.
        for (String cls : new String[]{
                "com.instagram.reels.store.ReelStore",
                "com.instagram.model.reels.Reel",
                "com.instagram.api.schemas.ReelSeenState"}) {
            hookVoid(loader, "hide story views", PrefsKeys.HIDE_STORY_VIEWS, cls,
                    new String[]{"markSeen", "setSeen", "markReelSeen"});
        }
        hookReturnFalse(loader, "hide story views (upload)",
                PrefsKeys.HIDE_STORY_VIEWS,
                "com.instagram.reels.mvvm.ReelSeenStateMutation",
                new String[]{"send", "execute"});

        // Live presence ("joined the live").
        hookVoid(loader, "hide live presence", PrefsKeys.HIDE_LIVE_PRESENCE,
                "com.instagram.live.model.LiveJoinRequest",
                new String[]{"send", "execute"});
    }

    // -------------------------------------------------------------- presence
    private static void presence(ClassLoader loader) {
        // Freeze "last active": the app builds presence from these values.
        hookReturnZero(loader, "spoof last seen", PrefsKeys.SPOOF_LAST_SEEN,
                "com.instagram.common.session.UserSession",
                new String[]{"getLastActiveTime", "getLastSeenTime"});

        // View-once: block the "opened" receipt and allow replays.
        hookReturnFalse(loader, "hide view once", PrefsKeys.HIDE_VIEW_ONCE,
                "com.instagram.direct.model.DirectItem",
                new String[]{"isViewed", "hasViewed"});
        hookVoid(loader, "hide view once (receipt)", PrefsKeys.HIDE_VIEW_ONCE,
                "com.instagram.direct.model.DirectVisualMessage",
                new String[]{"markViewed", "setViewed"});

        if (PrefsReader.get(PrefsKeys.REPLAY_VIEW_ONCE, false)) {
            hookReturnTrue(loader, "replay view once", PrefsKeys.REPLAY_VIEW_ONCE,
                    "com.instagram.direct.model.DirectVisualMessage",
                    new String[]{"canReplay", "isReplayable"});
        }

        // Disappearing messages: keep them.
        hookReturnFalse(loader, "keep disappearing", PrefsKeys.KEEP_DISAPPEARING,
                "com.instagram.direct.model.DirectItem",
                new String[]{"isExpired", "shouldExpire", "isEphemeralExpired"});
    }

    // ----------------------------------------------------------- screenshot
    private static void screenshots(ClassLoader loader) {
        // Screenshot detection is reported through a "screenshot taken" mutation.
        hookVoid(loader, "bypass screenshot detection",
                PrefsKeys.HIDE_SCREENSHOT_DETECT,
                "com.instagram.direct.messaging.DirectSendScreenshotMutation",
                new String[]{"send", "execute", "run"});
        hookReturnFalse(loader, "bypass screenshot detection",
                PrefsKeys.HIDE_SCREENSHOT_DETECT,
                "com.instagram.direct.model.DirectThreadKey",
                new String[]{"shouldReportScreenshot"});

        // FLAG_SECURE on the DM activity blocks screenshots; clearing it lets the
        // person take one. Done through the window flags, which every API has.
        if (PrefsReader.get(PrefsKeys.ALLOW_DM_SCREENSHOTS, false)) {
            hookClearSecure(loader);
        }
    }

    private static void hookClearSecure(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod(
                    Class.forName("android.app.Activity", false, loader),
                    "onResume", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (!PrefsReader.get(PrefsKeys.ALLOW_DM_SCREENSHOTS, false)) return;
                                android.app.Activity activity = (android.app.Activity) param.thisObject;
                                if (activity == null || activity.getWindow() == null) return;
                                String name = activity.getClass().getName();
                                if (!name.startsWith("com.instagram.android")) return;
                                if (name.toLowerCase().contains("direct")
                                        || name.toLowerCase().contains("inbox")) {
                                    activity.getWindow().clearFlags(
                                            android.view.WindowManager.LayoutParams.FLAG_SECURE);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            HookBridge.log("ghost: allow DM screenshots armed");
        } catch (Throwable t) {
            HookBridge.warn("allow DM screenshots", t);
        }
    }

    // ----------------------------------------------------------------- utils
    private static String simple(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    /**
     * Hooks every method with one of the given names and swallows the call.
     * This is what "don't send that" looks like at the plumbing level.
     */
    private static void hookVoid(ClassLoader loader, String label, String prefKey,
                                 String className, String[] methods) {
        if (!PrefsReader.get(prefKey, false)) return;
        Class<?> type = load(loader, className);
        if (type == null) return;
        for (Method method : allMethods(type)) {
            if (!matches(method, methods)) continue;
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (PrefsReader.get(prefKey, false)) param.setResult(null);
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private static void hookReturnFalse(ClassLoader loader, String label, String prefKey,
                                        String className, String[] methods) {
        hookFixed(loader, prefKey, className, methods, BLOCK);
    }

    private static void hookReturnTrue(ClassLoader loader, String label, String prefKey,
                                       String className, String[] methods) {
        hookFixed(loader, prefKey, className, methods, Boolean.TRUE);
    }

    private static void hookReturnZero(ClassLoader loader, String label, String prefKey,
                                       String className, String[] methods) {
        hookFixed(loader, prefKey, className, methods, 0L);
    }

    private static void hookFixed(ClassLoader loader, String prefKey, String className,
                                  String[] methods, Object value) {
        if (!PrefsReader.get(prefKey, false)) return;
        Class<?> type = load(loader, className);
        if (type == null) return;
        for (Method method : allMethods(type)) {
            if (!matches(method, methods)) continue;
            // Only override a method whose return type can hold that value.
            if (value instanceof Boolean && method.getReturnType() != boolean.class
                    && method.getReturnType() != Boolean.class) continue;
            if (value instanceof Long && method.getReturnType() != long.class
                    && method.getReturnType() != Long.class
                    && method.getReturnType() != int.class
                    && method.getReturnType() != Integer.class) continue;
            final Object fixed = value instanceof Long
                    && (method.getReturnType() == int.class || method.getReturnType() == Integer.class)
                    ? (Object) 0 : value;
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (PrefsReader.get(prefKey, false)) param.setResult(fixed);
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean matches(Method method, String[] names) {
        String name = method.getName();
        for (String candidate : names) if (candidate.equals(name)) return true;
        return false;
    }

    private static List<Method> allMethods(Class<?> type) {
        List<Method> out = new ArrayList<>();
        try {
            // Walk the hierarchy so obfuscated subclasses are covered too.
            for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
                for (Method method : cursor.getDeclaredMethods()) out.add(method);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Class<?> load(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
