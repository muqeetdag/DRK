package io.nyx.app.hook;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Chat-side tools: keep unsent messages, keep edit history, and expose the
 * media inside a message so it can be downloaded.
 *
 * All three work on the *model* layer, not the view layer, so nothing here can
 * disturb scrolling or the chat UI. Text is written to the module's own storage
 * through the hook's file path, which works for both LSPosed and LSPatch.
 */
final class MessageTools {

    private static final String UNSENT = "nyx_unsent.log";
    private static final String EDITS = "nyx_edits.log";
    private static boolean armed = false;

    private MessageTools() {}

    static void install(ClassLoader loader) {
        if (armed) return;
        armed = true;
        keepUnsent(loader);
        keepEdits(loader);
        voiceAndStickers(loader);
    }

    // -------------------------------------------------------- unsent messages
    /**
     * An "unsend" removes a message locally. We copy the payload before the
     * removal runs, so the text survives in our log instead of vanishing.
     */
    private static void keepUnsent(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.KEEP_UNSENT, false)) return;
        for (String cls : new String[]{
                "com.instagram.direct.model.DirectItem",
                "com.instagram.direct.messaging.payload.DirectMessagePayload"}) {
            Class<?> type = load(loader, cls);
            if (type == null) continue;
            for (Method method : methodsOf(type)) {
                String name = method.getName().toLowerCase();
                if (!name.contains("unsend") && !name.contains("remove")
                        && !name.contains("delete")) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!PrefsReader.get(PrefsKeys.KEEP_UNSENT, false)) return;
                                if (param.thisObject == null) return;
                                append(UNSENT, describe(param.thisObject));
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }
        HookBridge.log("messages: keep unsent armed");
    }

    // ----------------------------------------------------------- edit history
    private static void keepEdits(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.EDIT_HISTORY, false)) return;
        for (String cls : new String[]{
                "com.instagram.direct.model.DirectItem",
                "com.instagram.direct.messaging.payload.DirectMessagePayload"}) {
            Class<?> type = load(loader, cls);
            if (type == null) continue;
            for (Method method : methodsOf(type)) {
                String name = method.getName();
                if (!"setText".equals(name) && !"setMessage".equals(name)) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!PrefsReader.get(PrefsKeys.EDIT_HISTORY, false)) return;
                                if (param.args == null || param.args.length == 0) return;
                                String next = String.valueOf(param.args[0]);
                                String previous = readText(param.thisObject);
                                if (previous == null || previous.equals(next)) return;
                                append(EDITS, describe(param.thisObject) + " :: " + previous);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }
        HookBridge.log("messages: edit history armed");
    }

    // ------------------------------------------------- voice + sticker saving
    private static void voiceAndStickers(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.VOICE_DOWNLOAD, false)
                && !PrefsReader.get(PrefsKeys.STICKER_DOWNLOAD, false)) return;
        for (String cls : new String[]{
                "com.instagram.direct.model.DirectItem",
                "com.instagram.direct.model.DirectAudioMessage"}) {
            Class<?> type = load(loader, cls);
            if (type == null) continue;
            for (Method method : methodsOf(type)) {
                String name = method.getName().toLowerCase();
                if (!name.contains("geturl") && !name.contains("getmediaurl")
                        && !name.contains("getaudiourl")) continue;
                if (method.getReturnType() != String.class) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object url = param.getResult();
                                if (url instanceof String && ((String) url).startsWith("http")) {
                                    lastMediaUrl = (String) url;
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }
        HookBridge.log("messages: voice/sticker URL capture armed");
    }

    /** The most recent DM media URL, offered to the dock as "save this". */
    static volatile String lastMediaUrl;

    // ------------------------------------------------------------------ utils
    private static String readText(Object item) {
        for (String getter : new String[]{"getText", "getMessage", "getDisplayText"}) {
            try {
                Object value = item.getClass().getMethod(getter).invoke(item);
                if (value != null) return String.valueOf(value);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** Human-readable one-liner: who, what, when - never a raw object dump. */
    private static String describe(Object item) {
        String text = readText(item);
        String sender = null;
        for (String getter : new String[]{"getUserId", "getSenderId", "getUsername"}) {
            try {
                Object value = item.getClass().getMethod(getter).invoke(item);
                if (value != null) {
                    sender = String.valueOf(value);
                    break;
                }
            } catch (Throwable ignored) {
            }
        }
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        return when + " | " + (sender == null ? "?" : sender) + " | "
                + (text == null ? "[media]" : text);
    }

    private static synchronized void append(String name, String line) {
        try {
            File dir = logDir();
            if (dir == null) return;
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(new File(dir, name), true)) {
                out.write((line + "\n").getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        }
    }

    /** Reads a log back for the console. */
    static String read(String name, int max) {
        try {
            File dir = logDir();
            if (dir == null) return "";
            File file = new File(dir, name);
            if (!file.exists()) return "";
            byte[] data = new byte[(int) Math.min(file.length(), max)];
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                int read = in.read(data);
                if (read <= 0) return "";
                return new String(data, 0, read, "UTF-8");
            }
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static File logDir() {
        String base = PrefsReader.filesDir();
        return base == null ? null : new File(base);
    }

    static final String UNSENT_LOG = UNSENT;
    static final String EDITS_LOG = EDITS;

    private static List<Method> methodsOf(Class<?> type) {
        List<Method> out = new ArrayList<>();
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                for (Method method : cursor.getDeclaredMethods()) out.add(method);
            } catch (Throwable ignored) {
            }
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
