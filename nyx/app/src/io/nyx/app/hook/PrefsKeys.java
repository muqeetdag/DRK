package io.nyx.app.hook;

/**
 * Re-export of the console's pref keys for the hook side.
 *
 * The hook deliberately keeps its own entry point: the values are the same
 * strings the console writes, so a toggle flips instantly, but the hook never
 * touches SharedPreferences directly (it goes through {@link PrefsReader}).
 */
public final class PrefsKeys {
    public static final String FILE = io.nyx.app.core.Prefs.FILE;
    public static final String FLOATING_NAV = io.nyx.app.core.Prefs.FLOATING_NAV;
    public static final String REELS = io.nyx.app.core.Prefs.REELS;
    public static final String STORIES = io.nyx.app.core.Prefs.STORIES;
    public static final String FEED = io.nyx.app.core.Prefs.FEED;
    public static final String LONG_PRESS = io.nyx.app.core.Prefs.LONG_PRESS;
    public static final String DEBUG_HUD = io.nyx.app.core.Prefs.DEBUG_HUD;
    public static final String KEEPER_BUTTON = io.nyx.app.core.Prefs.KEEPER_BUTTON;
    public static final String ZEN = io.nyx.app.core.Prefs.ZEN;
    public static final String GLASS = io.nyx.app.core.Prefs.GLASS;
    public static final String HAPTIC = io.nyx.app.core.Prefs.HAPTIC;
    public static final String THEME = io.nyx.app.core.Prefs.THEME;
    public static final String ACCENT = io.nyx.app.core.Prefs.ACCENT;
    public static final String SECONDARY = io.nyx.app.core.Prefs.SECONDARY;
    public static final String FOLDER = io.nyx.app.core.Prefs.FOLDER;
    public static final String INSTALL_ALERTS = io.nyx.app.core.Prefs.INSTALL_ALERTS;
    public static final String GHOST_MASTER = io.nyx.app.core.Prefs.GHOST_MASTER;
    public static final String HIDE_DM_SEEN = io.nyx.app.core.Prefs.HIDE_DM_SEEN;
    public static final String HIDE_TYPING = io.nyx.app.core.Prefs.HIDE_TYPING;
    public static final String HIDE_STORY_VIEWS = io.nyx.app.core.Prefs.HIDE_STORY_VIEWS;
    public static final String HIDE_LIVE_PRESENCE = io.nyx.app.core.Prefs.HIDE_LIVE_PRESENCE;
    public static final String HIDE_SCREENSHOT_DETECT = io.nyx.app.core.Prefs.HIDE_SCREENSHOT_DETECT;
    public static final String HIDE_VIEW_ONCE = io.nyx.app.core.Prefs.HIDE_VIEW_ONCE;
    public static final String REPLAY_VIEW_ONCE = io.nyx.app.core.Prefs.REPLAY_VIEW_ONCE;
    public static final String KEEP_DISAPPEARING = io.nyx.app.core.Prefs.KEEP_DISAPPEARING;
    public static final String SPOOF_LAST_SEEN = io.nyx.app.core.Prefs.SPOOF_LAST_SEEN;
    public static final String AIRPLANE_MODE = io.nyx.app.core.Prefs.AIRPLANE_MODE;
    public static final String GHOST_QUICK_TOGGLE = io.nyx.app.core.Prefs.GHOST_QUICK_TOGGLE;
    public static final String KEEP_UNSENT = io.nyx.app.core.Prefs.KEEP_UNSENT;
    public static final String EDIT_HISTORY = io.nyx.app.core.Prefs.EDIT_HISTORY;
    public static final String CHAT_EXPORT = io.nyx.app.core.Prefs.CHAT_EXPORT;
    public static final String VOICE_DOWNLOAD = io.nyx.app.core.Prefs.VOICE_DOWNLOAD;
    public static final String STICKER_DOWNLOAD = io.nyx.app.core.Prefs.STICKER_DOWNLOAD;
    public static final String ALLOW_DM_SCREENSHOTS = io.nyx.app.core.Prefs.ALLOW_DM_SCREENSHOTS;
    public static final String HIDE_CHATS = io.nyx.app.core.Prefs.HIDE_CHATS;
    public static final String STORY_CACHE = io.nyx.app.core.Prefs.STORY_CACHE;
    public static final String STORY_INFO = io.nyx.app.core.Prefs.STORY_INFO;
    public static final String STORY_MUSIC_LINKS = io.nyx.app.core.Prefs.STORY_MUSIC_LINKS;
    public static final String STORY_LINK_COPY = io.nyx.app.core.Prefs.STORY_LINK_COPY;
    public static final String STORY_TRAY_COMPACT = io.nyx.app.core.Prefs.STORY_TRAY_COMPACT;
    public static final String HIDE_STORY_TRAY = io.nyx.app.core.Prefs.HIDE_STORY_TRAY;
    public static final String STORY_MENTIONS = io.nyx.app.core.Prefs.STORY_MENTIONS;
    public static final String STORY_VIEWER_SEARCH = io.nyx.app.core.Prefs.STORY_VIEWER_SEARCH;
    public static final String STORY_NO_AUTO_SWIPE = io.nyx.app.core.Prefs.STORY_NO_AUTO_SWIPE;
    public static final String REELS_SPEED = io.nyx.app.core.Prefs.REELS_SPEED;
    public static final String HOLD_SLOWMO = io.nyx.app.core.Prefs.HOLD_SLOWMO;
    public static final String ALWAYS_SEEKBAR = io.nyx.app.core.Prefs.ALWAYS_SEEKBAR;
    public static final String REELS_AUTOSCROLL = io.nyx.app.core.Prefs.REELS_AUTOSCROLL;
    public static final String REELS_SCROLL_LOCK = io.nyx.app.core.Prefs.REELS_SCROLL_LOCK;
    public static final String DISABLE_REPOST = io.nyx.app.core.Prefs.DISABLE_REPOST;
    public static final String DOWNLOAD_PROFILE_PIC = io.nyx.app.core.Prefs.DOWNLOAD_PROFILE_PIC;
    public static final String DOWNLOAD_HIGHLIGHT = io.nyx.app.core.Prefs.DOWNLOAD_HIGHLIGHT;
    public static final String DOWNLOAD_PROFILE_ALL = io.nyx.app.core.Prefs.DOWNLOAD_PROFILE_ALL;
    public static final String SAVE_VIDEO_FRAME = io.nyx.app.core.Prefs.SAVE_VIDEO_FRAME;
    public static final String AUDIO_ONLY = io.nyx.app.core.Prefs.AUDIO_ONLY;
    public static final String COPY_MEDIA_LINK = io.nyx.app.core.Prefs.COPY_MEDIA_LINK;
    public static final String COPY_IMAGE = io.nyx.app.core.Prefs.COPY_IMAGE;
    public static final String MEDIA_INFO = io.nyx.app.core.Prefs.MEDIA_INFO;
    public static final String SHARE_DOWNLOAD = io.nyx.app.core.Prefs.SHARE_DOWNLOAD;
    public static final String VIDEO_AUTOPLAY_OFF = io.nyx.app.core.Prefs.VIDEO_AUTOPLAY_OFF;
    public static final String FORCE_REELS_QUALITY = io.nyx.app.core.Prefs.FORCE_REELS_QUALITY;
    public static final String HIGH_RES_IMAGES = io.nyx.app.core.Prefs.HIGH_RES_IMAGES;
    public static final String MAX_UPLOAD_QUALITY = io.nyx.app.core.Prefs.MAX_UPLOAD_QUALITY;
    public static final String SPOOF_DEVICE = io.nyx.app.core.Prefs.SPOOF_DEVICE;
    public static final String HIDE_TAB_HOME = io.nyx.app.core.Prefs.HIDE_TAB_HOME;
    public static final String HIDE_TAB_REELS = io.nyx.app.core.Prefs.HIDE_TAB_REELS;
    public static final String HIDE_TAB_CREATE = io.nyx.app.core.Prefs.HIDE_TAB_CREATE;
    public static final String HIDE_TAB_DM = io.nyx.app.core.Prefs.HIDE_TAB_DM;
    public static final String HIDE_TAB_ACTIVITY = io.nyx.app.core.Prefs.HIDE_TAB_ACTIVITY;
    public static final String HIDE_TAB_PROFILE = io.nyx.app.core.Prefs.HIDE_TAB_PROFILE;
    public static final String LOCK_APP = io.nyx.app.core.Prefs.LOCK_APP;
    public static final String LOCK_DM = io.nyx.app.core.Prefs.LOCK_DM;
    public static final String BLOCK_ADS = io.nyx.app.core.Prefs.BLOCK_ADS;
    public static final String BLOCK_ANALYTICS = io.nyx.app.core.Prefs.BLOCK_ANALYTICS;
    public static final String STRIP_TRACKING = io.nyx.app.core.Prefs.STRIP_TRACKING;
    public static final String NO_SAVE_SEARCH = io.nyx.app.core.Prefs.NO_SAVE_SEARCH;
    public static final String SPOOF_LOCATION = io.nyx.app.core.Prefs.SPOOF_LOCATION;
    public static final String HIDE_SUGGESTIONS = io.nyx.app.core.Prefs.HIDE_SUGGESTIONS;
    public static final String HIDE_THREADS_SUGGEST = io.nyx.app.core.Prefs.HIDE_THREADS_SUGGEST;
    public static final String REMOVE_META_AI = io.nyx.app.core.Prefs.REMOVE_META_AI;
    public static final String HIDE_ONBOARDING_NAGS = io.nyx.app.core.Prefs.HIDE_ONBOARDING_NAGS;
    public static final String HIDE_PLUS_UPSELLS = io.nyx.app.core.Prefs.HIDE_PLUS_UPSELLS;
    public static final String DISABLE_STORIES = io.nyx.app.core.Prefs.DISABLE_STORIES;
    public static final String DISABLE_FEED = io.nyx.app.core.Prefs.DISABLE_FEED;
    public static final String DISABLE_REELS = io.nyx.app.core.Prefs.DISABLE_REELS;
    public static final String DISABLE_EXPLORE = io.nyx.app.core.Prefs.DISABLE_EXPLORE;
    public static final String DISABLE_COMMENTS = io.nyx.app.core.Prefs.DISABLE_COMMENTS;
    public static final String EXTREME_MODE = io.nyx.app.core.Prefs.EXTREME_MODE;
    public static final String UNLIMITED_ACCOUNTS = io.nyx.app.core.Prefs.UNLIMITED_ACCOUNTS;
    public static final String OPEN_LINKS_EXTERNAL = io.nyx.app.core.Prefs.OPEN_LINKS_EXTERNAL;
    public static final String NOTIFICATION_FIX = io.nyx.app.core.Prefs.NOTIFICATION_FIX;
    public static final String AUTO_CLEAR_CACHE = io.nyx.app.core.Prefs.AUTO_CLEAR_CACHE;
    public static final String COPY_COMMENT = io.nyx.app.core.Prefs.COPY_COMMENT;
    public static final String COPY_CAPTION = io.nyx.app.core.Prefs.COPY_CAPTION;
    public static final String PHOTO_ZOOM = io.nyx.app.core.Prefs.PHOTO_ZOOM;
    public static final String FOLLOWER_TOAST = io.nyx.app.core.Prefs.FOLLOWER_TOAST;
    public static final String DEV_OPTIONS = io.nyx.app.core.Prefs.DEV_OPTIONS;
    public static final String FEATURE_TOASTS = io.nyx.app.core.Prefs.FEATURE_TOASTS;
    public static final String SPEED_VALUE = io.nyx.app.core.Prefs.SPEED_VALUE;
    public static final String QUALITY_VALUE = io.nyx.app.core.Prefs.QUALITY_VALUE;
    public static final String STORY_CACHE_HOURS = io.nyx.app.core.Prefs.STORY_CACHE_HOURS;
    public static final String LOCK_CODE = io.nyx.app.core.Prefs.LOCK_CODE;
    public static final String DL_USERNAME_FOLDERS = io.nyx.app.core.Prefs.DL_USERNAME_FOLDERS;
    public static final String DL_TIMESTAMP = io.nyx.app.core.Prefs.DL_TIMESTAMP;
    public static final String DL_DATE = io.nyx.app.core.Prefs.DL_DATE;

    private PrefsKeys() {}
}
