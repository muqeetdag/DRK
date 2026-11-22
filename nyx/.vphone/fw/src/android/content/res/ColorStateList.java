package android.content.res;

import android.graphics.Color;

/** Colour state list, flattened to the default colour for our purposes. */
public class ColorStateList {

    private final int defaultColor;

    public ColorStateList(int[][] states, int[] colors) {
        defaultColor = colors.length > 0 ? colors[0] : Color.WHITE;
    }

    private ColorStateList(int color) { defaultColor = color; }

    public static ColorStateList valueOf(int color) { return new ColorStateList(color); }

    public static ColorStateList valueOf(int[][] states, int[] colors) {
        return new ColorStateList(states, colors);
    }

    public int getDefaultColor() { return defaultColor; }

    public int getColorForState(int[] stateSet, int defaultColor) { return this.defaultColor; }

    public boolean isStateful() { return false; }
}
