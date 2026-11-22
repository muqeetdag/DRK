package io.nyx.app.core;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Minimal on-device crash log so a user can report the exact failure instead of
 * "it crashed". Written to files/crash-last.txt and readable from the console.
 */
public final class CrashLog {

    private static final String NAME = "crash-last.txt";

    private CrashLog() {}

    public static File file(Context ctx) {
        return new File(ctx.getFilesDir(), NAME);
    }

    public static void write(Context ctx, Throwable error) {
        try {
            StringWriter buffer = new StringWriter();
            buffer.append("when: ")
                    .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
                    .append('\n');
            if (error != null) error.printStackTrace(new PrintWriter(buffer));
            try (FileOutputStream out = new FileOutputStream(file(ctx))) {
                out.write(buffer.toString().getBytes("UTF-8"));
            }
            file(ctx).setReadable(true, false);
        } catch (Throwable ignored) {
        }
    }

    public static String read(Context ctx) {
        try {
            File file = file(ctx);
            if (!file.exists()) return "";
            byte[] data = new byte[(int) file.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                int read = in.read(data);
                if (read <= 0) return "";
            }
            String text = new String(data, "UTF-8");
            return text.length() > 4000 ? text.substring(0, 4000) : text;
        } catch (Throwable t) {
            return "";
        }
    }

    public static void clear(Context ctx) {
        try {
            //noinspection ResultOfMethodCallIgnored
            file(ctx).delete();
        } catch (Throwable ignored) {
        }
    }
}
