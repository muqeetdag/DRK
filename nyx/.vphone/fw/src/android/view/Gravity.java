package android.view;

/** Gravity constants + a static apply helper. */
public class Gravity {
    public static final int NO_GRAVITY = 0x0000;
    public static final int AXIS_SPECIFIED = 0x0001;
    public static final int AXIS_PULL_BEFORE = 0x0002;
    public static final int AXIS_PULL_AFTER = 0x0004;
    public static final int AXIS_X_SHIFT = 0;
    public static final int AXIS_Y_SHIFT = 4;
    public static final int TOP = 0x30;
    public static final int BOTTOM = 0x50;
    public static final int LEFT = 0x03;
    public static final int RIGHT = 0x05;
    public static final int CENTER_VERTICAL = 0x10;
    public static final int CENTER_HORIZONTAL = 0x01;
    public static final int CENTER = 0x11;
    public static final int START = 0x00800003;
    public static final int END = 0x00800005;
    public static final int FILL_VERTICAL = 0x70;
    public static final int FILL_HORIZONTAL = 0x07;
    public static final int FILL = 0x77;
    public static final int CLIP_HORIZONTAL = 0x08;
    public static final int HORIZONTAL_GRAVITY_MASK = 0x07;
    public static final int VERTICAL_GRAVITY_MASK = 0x70;
    public static final int RELATIVE_LAYOUT_DIRECTION = 0x00800000;
    public static final int DISPLAY_CLIP_VERTICAL = 0x10000000;

    public static void apply(int gravity, int w, int h, android.graphics.Rect container,
                             int xAdj, int yAdj, android.graphics.Rect outRect) {
        outRect.set(container);
    }
}
