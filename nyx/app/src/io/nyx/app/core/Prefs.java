package io.nyx.app.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Single write path for every toggle. Both the companion UI and the
 * Instagram hook read the same {@link SharedPreferences} file, so LSPosed's
 * XSharedPreferences sees the exact same values. Every read is defensive
 * because a hook must never crash a host app.
 */
public final class Prefs {
    public static final String FILE = "nyx_prefs";

    public static final String FLOATING_NAV = "floating_nav";
    public static final String REELS = "reels_downloader";
    public static final String STORIES = "stories_downloader";
    public static final String FEED = "feed_downloader";
    public static final String LONG_PRESS = "long_press_save";
    public static final String DEBUG_HUD = "debug_hud";
    public static final String KEEPER_BUTTON = "show_keeper_button";
    public static final String ZEN = "zen_scroll";
    public static final String GLASS = "glass_header";
    public static final String HAPTIC = "haptic";
    public static final String THEME = "theme_id";
    public static final String ACCENT = "accent_color";
    public static final String SECONDARY = "secondary_color";
    public static final String FOLDER = "download_folder";
    public static final String INSTALL_ALERTS = "install_alerts";

    public static final List<String> FEATURE_KEYS = Arrays.asList(
            FLOATING_NAV, REELS, STORIES, FEED, LONG_PRESS,
            ZEN, GLASS, HAPTIC, DEBUG_HUD, KEEPER_BUTTON);

    private Prefs() {}

    /**
     * LSPosed (API 93+) allows MODE_WORLD_READABLE for hooked module apps and
     * relocates the prefs into a path XSharedPreferences reads. Outside a
     * manager (or before the module is enabled) that mode throws, so it falls
     * back to private prefs — the hook then uses its build-time defaults.
     */
    public static SharedPreferences open(Context ctx) {
        try {
            //noinspection deprecation
            return ctx.getSharedPreferences(FILE, Context.MODE_WORLD_READABLE);
        } catch (Throwable t) {
            return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        }
    }

    public static void seedIfEmpty(Context ctx) {
        SharedPreferences sp = open(ctx);
        if (sp.getBoolean("seeded", false)) return;
        SharedPreferences.Editor e = sp.edit();
        e.putBoolean("seeded", true);
        e.putBoolean(FLOATING_NAV, GenSettings.FLOATING);
        e.putBoolean(REELS, GenSettings.REELS);
        e.putBoolean(STORIES, GenSettings.STORIES);
        e.putBoolean(FEED, GenSettings.FEED);
        e.putBoolean(LONG_PRESS, true);
        e.putBoolean(DEBUG_HUD, false);
        e.putBoolean(KEEPER_BUTTON, true);
        e.putBoolean(ZEN, false);
        e.putBoolean(GLASS, true);
        e.putBoolean(HAPTIC, true);
        e.putBoolean(INSTALL_ALERTS, true);
        e.putInt(THEME, 0);
        e.putInt(ACCENT, 0);
        e.putInt(SECONDARY, 0);
        e.putString(FOLDER, GenSettings.FOLDER);
        e.commit();
        writeMirror(ctx);
    }

    /** Apply + mirror so the Instagram hook sees the change immediately. */
    public static void commit(Context ctx, String key, boolean value) {
        open(ctx).edit().putBoolean(key, value).commit();
        writeMirror(ctx);
    }

    public static void commit(Context ctx, String key, int value) {
        open(ctx).edit().putInt(key, value).commit();
        writeMirror(ctx);
    }

    public static void commit(Context ctx, String key, String value) {
        open(ctx).edit().putString(key, value).commit();
        writeMirror(ctx);
    }

    /** Mirror file the Instagram hook can always read (no prefs permissions). */
    public static File mirrorFile(Context ctx) {
        return new File(ctx.getFilesDir(), "nyx_prefs_mirror.xml");
    }

    public static void writeMirror(Context ctx) {
        try {
            SharedPreferences sp = open(ctx);
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n");
            for (java.util.Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
                Object value = entry.getValue();
                String name = entry.getKey();
                if (value instanceof Boolean) {
                    xml.append("  <boolean name=\"").append(name).append("\" value=\"")
                            .append(value).append("\" />\n");
                } else if (value instanceof Integer) {
                    xml.append("  <int name=\"").append(name).append("\" value=\"")
                            .append(value).append("\" />\n");
                } else if (value instanceof Long) {
                    xml.append("  <long name=\"").append(name).append("\" value=\"")
                            .append(value).append("\" />\n");
                } else if (value instanceof String) {
                    xml.append("  <string name=\"").append(name).append("\">")
                            .append(escape(value.toString())).append("</string>\n");
                }
            }
            xml.append("</map>\n");
            File file = mirrorFile(ctx);
            File temp = new File(file.getAbsolutePath() + ".tmp");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(temp)) {
                out.write(xml.toString().getBytes("UTF-8"));
            }
            if (!temp.renameTo(file)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                //noinspection ResultOfMethodCallIgnored
                temp.renameTo(file);
            }
            //noinspection ResultOfMethodCallIgnored
            file.setReadable(true, false);
        } catch (Throwable ignored) {
        }
    }

    private static String escape(String raw) {
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    public static boolean get(SharedPreferences sp, String key, boolean fallback) {
        try {
            return sp.getBoolean(key, fallback);
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static int count(SharedPreferences sp) {
        int n = 0;
        for (String key : FEATURE_KEYS) if (get(sp, key, false)) n++;
        return n;
    }
}
