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

    // ---------------------------------------------------------------- ghost
    public static final String GHOST_MASTER = "ghost_master";
    public static final String HIDE_DM_SEEN = "hide_dm_seen";
    public static final String HIDE_TYPING = "hide_typing";
    public static final String HIDE_STORY_VIEWS = "hide_story_views";
    public static final String HIDE_LIVE_PRESENCE = "hide_live_presence";
    public static final String HIDE_SCREENSHOT_DETECT = "hide_screenshot_detect";
    public static final String HIDE_VIEW_ONCE = "hide_view_once";
    public static final String REPLAY_VIEW_ONCE = "replay_view_once";
    public static final String KEEP_DISAPPEARING = "keep_disappearing";
    public static final String SPOOF_LAST_SEEN = "spoof_last_seen";
    public static final String AIRPLANE_MODE = "airplane_mode";
    public static final String GHOST_QUICK_TOGGLE = "ghost_quick_toggle";

    // ------------------------------------------------------------- messages
    public static final String KEEP_UNSENT = "keep_unsent";
    public static final String EDIT_HISTORY = "edit_history";
    public static final String CHAT_EXPORT = "chat_export";
    public static final String VOICE_DOWNLOAD = "voice_download";
    public static final String STICKER_DOWNLOAD = "sticker_download";
    public static final String ALLOW_DM_SCREENSHOTS = "allow_dm_screenshots";
    public static final String HIDE_CHATS = "hide_chats";

    // -------------------------------------------------------------- stories
    public static final String STORY_CACHE = "story_cache";
    public static final String STORY_INFO = "story_info";
    public static final String STORY_MUSIC_LINKS = "story_music_links";
    public static final String STORY_LINK_COPY = "story_link_copy";
    public static final String STORY_TRAY_COMPACT = "story_tray_compact";
    public static final String HIDE_STORY_TRAY = "hide_story_tray";
    public static final String STORY_MENTIONS = "story_mentions";
    public static final String STORY_VIEWER_SEARCH = "story_viewer_search";
    public static final String STORY_NO_AUTO_SWIPE = "story_no_auto_swipe";

    // ---------------------------------------------------------------- reels
    public static final String REELS_SPEED = "reels_speed";
    public static final String HOLD_SLOWMO = "hold_slowmo";
    public static final String ALWAYS_SEEKBAR = "always_seekbar";
    public static final String REELS_AUTOSCROLL = "reels_autoscroll";
    public static final String REELS_SCROLL_LOCK = "reels_scroll_lock";
    public static final String DISABLE_REPOST = "disable_repost";

    // ------------------------------------------------------------ downloads
    public static final String DOWNLOAD_PROFILE_PIC = "download_profile_pic";
    public static final String DOWNLOAD_HIGHLIGHT = "download_highlight";
    public static final String DOWNLOAD_PROFILE_ALL = "download_profile_all";
    public static final String SAVE_VIDEO_FRAME = "save_video_frame";
    public static final String AUDIO_ONLY = "audio_only";
    public static final String COPY_MEDIA_LINK = "copy_media_link";
    public static final String COPY_IMAGE = "copy_image";
    public static final String MEDIA_INFO = "media_info";
    public static final String SHARE_DOWNLOAD = "share_download";
    public static final String VIDEO_AUTOPLAY_OFF = "video_autoplay_off";

    // -------------------------------------------------------------- quality
    public static final String FORCE_REELS_QUALITY = "force_reels_quality";
    public static final String HIGH_RES_IMAGES = "high_res_images";
    public static final String MAX_UPLOAD_QUALITY = "max_upload_quality";
    public static final String SPOOF_DEVICE = "spoof_device";

    // ----------------------------------------------------------- navigation
    public static final String HIDE_TAB_HOME = "hide_tab_home";
    public static final String HIDE_TAB_REELS = "hide_tab_reels";
    public static final String HIDE_TAB_CREATE = "hide_tab_create";
    public static final String HIDE_TAB_DM = "hide_tab_dm";
    public static final String HIDE_TAB_ACTIVITY = "hide_tab_activity";
    public static final String HIDE_TAB_PROFILE = "hide_tab_profile";

    // --------------------------------------------------------------- safety
    public static final String LOCK_APP = "lock_app";
    public static final String LOCK_DM = "lock_dm";
    public static final String BLOCK_ADS = "block_ads";
    public static final String BLOCK_ANALYTICS = "block_analytics";
    public static final String STRIP_TRACKING = "strip_tracking";
    public static final String NO_SAVE_SEARCH = "no_save_search";
    public static final String SPOOF_LOCATION = "spoof_location";

    // ------------------------------------------------------------- feed
    public static final String HIDE_SUGGESTIONS = "hide_suggestions";
    public static final String HIDE_THREADS_SUGGEST = "hide_threads_suggest";
    public static final String REMOVE_META_AI = "remove_meta_ai";
    public static final String HIDE_ONBOARDING_NAGS = "hide_onboarding_nags";
    public static final String HIDE_PLUS_UPSELLS = "hide_plus_upsells";

    // ------------------------------------------------- dist-free / tools
    public static final String DISABLE_STORIES = "disable_stories";
    public static final String DISABLE_FEED = "disable_feed";
    public static final String DISABLE_REELS = "disable_reels";
    public static final String DISABLE_EXPLORE = "disable_explore";
    public static final String DISABLE_COMMENTS = "disable_comments";
    public static final String EXTREME_MODE = "extreme_mode";
    public static final String UNLIMITED_ACCOUNTS = "unlimited_accounts";
    public static final String OPEN_LINKS_EXTERNAL = "open_links_external";
    public static final String NOTIFICATION_FIX = "notification_fix";
    public static final String AUTO_CLEAR_CACHE = "auto_clear_cache";
    public static final String COPY_COMMENT = "copy_comment";
    public static final String COPY_CAPTION = "copy_caption";
    public static final String PHOTO_ZOOM = "photo_zoom";
    public static final String FOLLOWER_TOAST = "follower_toast";
    public static final String DEV_OPTIONS = "dev_options";
    public static final String FEATURE_TOASTS = "feature_toasts";

    // --------------------------------------------------------------- values
    public static final String SPEED_VALUE = "speed_value";
    public static final String QUALITY_VALUE = "quality_value";
    public static final String STORY_CACHE_HOURS = "story_cache_hours";
    public static final String LOCK_CODE = "lock_code";
    public static final String DL_USERNAME_FOLDERS = "dl_username_folders";
    public static final String DL_TIMESTAMP = "dl_timestamp";
    public static final String DL_DATE = "dl_date";

    /**
     * Keys that count as an "armed ritual" on the dashboard. Ordered by area so
     * the console can group them exactly like the in-app menu.
     */
    public static final List<String> FEATURE_KEYS = Arrays.asList(
            FLOATING_NAV, REELS, STORIES, FEED, LONG_PRESS,
            ZEN, GLASS, HAPTIC, DEBUG_HUD, KEEPER_BUTTON,
            HIDE_DM_SEEN, HIDE_TYPING, HIDE_STORY_VIEWS, HIDE_LIVE_PRESENCE,
            HIDE_SCREENSHOT_DETECT, HIDE_VIEW_ONCE, KEEP_DISAPPEARING,
            SPOOF_LAST_SEEN, AIRPLANE_MODE,
            KEEP_UNSENT, EDIT_HISTORY, VOICE_DOWNLOAD, STICKER_DOWNLOAD,
            ALLOW_DM_SCREENSHOTS,
            STORY_CACHE, STORY_INFO, STORY_MUSIC_LINKS, STORY_NO_AUTO_SWIPE,
            REELS_AUTOSCROLL, REELS_SCROLL_LOCK, HOLD_SLOWMO, ALWAYS_SEEKBAR,
            DISABLE_REPOST,
            DOWNLOAD_PROFILE_PIC, DOWNLOAD_HIGHLIGHT, AUDIO_ONLY,
            COPY_MEDIA_LINK, MEDIA_INFO, VIDEO_AUTOPLAY_OFF,
            FORCE_REELS_QUALITY, HIGH_RES_IMAGES, SPOOF_DEVICE,
            BLOCK_ADS, BLOCK_ANALYTICS, STRIP_TRACKING, NO_SAVE_SEARCH,
            HIDE_SUGGESTIONS, HIDE_THREADS_SUGGEST, REMOVE_META_AI,
            HIDE_ONBOARDING_NAGS, HIDE_PLUS_UPSELLS,
            OPEN_LINKS_EXTERNAL, NOTIFICATION_FIX, AUTO_CLEAR_CACHE,
            COPY_COMMENT, COPY_CAPTION, PHOTO_ZOOM);

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
        // Ghost mode ships off (it changes what other people see); downloads and
        // de-cluttering ship on, because those only affect your own phone.
        e.putBoolean(GHOST_MASTER, false);
        e.putBoolean(HIDE_DM_SEEN, false);
        e.putBoolean(HIDE_TYPING, false);
        e.putBoolean(HIDE_STORY_VIEWS, false);
        e.putBoolean(HIDE_LIVE_PRESENCE, false);
        e.putBoolean(HIDE_SCREENSHOT_DETECT, false);
        e.putBoolean(HIDE_VIEW_ONCE, false);
        e.putBoolean(REPLAY_VIEW_ONCE, false);
        e.putBoolean(KEEP_DISAPPEARING, false);
        e.putBoolean(SPOOF_LAST_SEEN, false);
        e.putBoolean(AIRPLANE_MODE, false);
        e.putBoolean(GHOST_QUICK_TOGGLE, true);

        e.putBoolean(KEEP_UNSENT, false);
        e.putBoolean(EDIT_HISTORY, false);
        e.putBoolean(CHAT_EXPORT, true);
        e.putBoolean(VOICE_DOWNLOAD, true);
        e.putBoolean(STICKER_DOWNLOAD, true);
        e.putBoolean(ALLOW_DM_SCREENSHOTS, false);
        e.putBoolean(HIDE_CHATS, false);

        e.putBoolean(STORY_CACHE, false);
        e.putBoolean(STORY_INFO, true);
        e.putBoolean(STORY_MUSIC_LINKS, true);
        e.putBoolean(STORY_LINK_COPY, true);
        e.putBoolean(STORY_TRAY_COMPACT, false);
        e.putBoolean(HIDE_STORY_TRAY, false);
        e.putBoolean(STORY_MENTIONS, true);
        e.putBoolean(STORY_VIEWER_SEARCH, true);
        e.putBoolean(STORY_NO_AUTO_SWIPE, false);

        e.putBoolean(REELS_AUTOSCROLL, false);
        e.putBoolean(REELS_SCROLL_LOCK, false);
        e.putBoolean(HOLD_SLOWMO, false);
        e.putBoolean(ALWAYS_SEEKBAR, false);
        e.putBoolean(DISABLE_REPOST, false);

        e.putBoolean(DOWNLOAD_PROFILE_PIC, true);
        e.putBoolean(DOWNLOAD_HIGHLIGHT, true);
        e.putBoolean(DOWNLOAD_PROFILE_ALL, false);
        e.putBoolean(SAVE_VIDEO_FRAME, false);
        e.putBoolean(AUDIO_ONLY, false);
        e.putBoolean(COPY_MEDIA_LINK, true);
        e.putBoolean(COPY_IMAGE, true);
        e.putBoolean(MEDIA_INFO, true);
        e.putBoolean(SHARE_DOWNLOAD, true);
        e.putBoolean(VIDEO_AUTOPLAY_OFF, false);

        e.putBoolean(FORCE_REELS_QUALITY, false);
        e.putBoolean(HIGH_RES_IMAGES, false);
        e.putBoolean(MAX_UPLOAD_QUALITY, false);
        e.putBoolean(SPOOF_DEVICE, false);

        e.putBoolean(HIDE_TAB_HOME, false);
        e.putBoolean(HIDE_TAB_REELS, false);
        e.putBoolean(HIDE_TAB_CREATE, false);
        e.putBoolean(HIDE_TAB_DM, false);
        e.putBoolean(HIDE_TAB_ACTIVITY, false);
        e.putBoolean(HIDE_TAB_PROFILE, false);

        e.putBoolean(LOCK_APP, false);
        e.putBoolean(LOCK_DM, false);
        e.putBoolean(BLOCK_ADS, true);
        e.putBoolean(BLOCK_ANALYTICS, true);
        e.putBoolean(STRIP_TRACKING, false);
        e.putBoolean(NO_SAVE_SEARCH, false);
        e.putBoolean(SPOOF_LOCATION, false);

        e.putBoolean(HIDE_SUGGESTIONS, false);
        e.putBoolean(HIDE_THREADS_SUGGEST, false);
        e.putBoolean(REMOVE_META_AI, false);
        e.putBoolean(HIDE_ONBOARDING_NAGS, false);
        e.putBoolean(HIDE_PLUS_UPSELLS, false);

        e.putBoolean(DISABLE_STORIES, false);
        e.putBoolean(DISABLE_FEED, false);
        e.putBoolean(DISABLE_REELS, false);
        e.putBoolean(DISABLE_EXPLORE, false);
        e.putBoolean(DISABLE_COMMENTS, false);
        e.putBoolean(EXTREME_MODE, false);
        e.putBoolean(UNLIMITED_ACCOUNTS, false);
        e.putBoolean(OPEN_LINKS_EXTERNAL, false);
        e.putBoolean(NOTIFICATION_FIX, false);
        e.putBoolean(AUTO_CLEAR_CACHE, false);
        e.putBoolean(COPY_COMMENT, true);
        e.putBoolean(COPY_CAPTION, true);
        e.putBoolean(PHOTO_ZOOM, true);
        e.putBoolean(FOLLOWER_TOAST, false);
        e.putBoolean(DEV_OPTIONS, false);
        e.putBoolean(FEATURE_TOASTS, true);

        e.putInt(SPEED_VALUE, 1);
        e.putInt(QUALITY_VALUE, 0);
        e.putInt(STORY_CACHE_HOURS, 24);
        e.putString(LOCK_CODE, "");
        e.putBoolean(DL_USERNAME_FOLDERS, true);
        e.putBoolean(DL_TIMESTAMP, false);
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

    public static String getString(SharedPreferences sp, String key) {
        try {
            String value = sp.getString(key, "");
            return value == null ? "" : value;
        } catch (Throwable t) {
            return "";
        }
    }

    public static int count(SharedPreferences sp) {
        int n = 0;
        for (String key : FEATURE_KEYS) if (get(sp, key, false)) n++;
        return n;
    }
}
