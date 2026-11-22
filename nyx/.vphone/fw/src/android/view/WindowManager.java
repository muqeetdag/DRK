package android.view;

import android.util.DisplayMetrics;

/** WindowManager exposing display metrics + layout params constants. */
public class WindowManager {

    public DisplayMetrics getDisplayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.widthPixels = android.test.Env.widthPx;
        metrics.heightPixels = android.test.Env.heightPx;
        return metrics;
    }

    public static class LayoutParams {
        public static final int FLAG_FULLSCREEN = 0x00000400;
        public static final int FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = 0x80000000;
        public static final int FLAG_TRANSLUCENT_STATUS = 0x04000000;
        public static final int FLAG_TRANSLUCENT_NAVIGATION = 0x08000000;
        public static final int FLAG_KEEP_SCREEN_ON = 0x00000080;
        public static final int FLAG_LAYOUT_NO_LIMITS = 0x00000200;
        public static final int FLAG_SECURE = 0x00002000;
        public static final int FLAG_NOT_FOCUSABLE = 0x00000008;
        public static final int FLAG_NOT_TOUCH_MODAL = 0x00000020;
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;
        public static final int SOFT_INPUT_ADJUST_RESIZE = 0x10;
        public static final int SOFT_INPUT_STATE_VISIBLE = 0x4;

        public int width = MATCH_PARENT;
        public int height = MATCH_PARENT;
        public int flags;
        public int gravity;
        public float dimAmount;
        public int type;
    }
}
