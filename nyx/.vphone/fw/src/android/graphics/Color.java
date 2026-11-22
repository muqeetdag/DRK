package android.graphics;

/** 32-bit ARGB colour helpers, identical maths to Android. */
public class Color {

    public static final int BLACK = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int TRANSPARENT = 0x00000000;
    public static final int RED = 0xFFFF0000;
    public static final int GREEN = 0xFF00FF00;
    public static final int BLUE = 0xFF0000FF;
    public static final int YELLOW = 0xFFFFFF00;
    public static final int CYAN = 0xFF00FFFF;
    public static final int MAGENTA = 0xFFFF00FF;
    public static final int GRAY = 0xFF888888;
    public static final int DKGRAY = 0xFF444444;
    public static final int LTGRAY = 0xFFCCCCCC;

    public static int alpha(int color) { return color >>> 24; }

    public static int red(int color) { return (color >> 16) & 0xFF; }

    public static int green(int color) { return (color >> 8) & 0xFF; }

    public static int blue(int color) { return color & 0xFF; }

    public static int rgb(int red, int green, int blue) {
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    public static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public static int parseColor(String colorString) {
        if (colorString == null) throw new IllegalArgumentException("Unknown color");
        if (colorString.charAt(0) == '#') {
            long color = Long.parseLong(colorString.substring(1), 16);
            if (colorString.length() == 7) {
                color |= 0x00000000ff000000L;
            } else if (colorString.length() != 9) {
                throw new IllegalArgumentException("Unknown color");
            }
            return (int) color;
        }
        Integer named = null;
        switch (colorString.toLowerCase()) {
            case "black": named = BLACK; break;
            case "white": named = WHITE; break;
            case "red": named = RED; break;
            case "green": named = GREEN; break;
            case "blue": named = BLUE; break;
            case "yellow": named = YELLOW; break;
            case "cyan": named = CYAN; break;
            case "magenta": named = MAGENTA; break;
            case "gray": case "grey": named = GRAY; break;
            case "ltgray": case "lightgray": named = LTGRAY; break;
            case "dkgray": case "darkgray": named = DKGRAY; break;
            case "transparent": named = TRANSPARENT; break;
            default: break;
        }
        if (named == null) throw new IllegalArgumentException("Unknown color");
        return named;
    }

    public static float[] colorToHSV(int color) {
        java.awt.Color awt = new java.awt.Color(color, true);
        float[] hsv = java.awt.Color.RGBtoHSB(awt.getRed(), awt.getGreen(), awt.getBlue(), null);
        return new float[]{hsv[0] * 360f, hsv[1], hsv[2]};
    }

    public static int HSVToColor(float[] hsv) {
        int rgb = java.awt.Color.HSBtoRGB(hsv[0] / 360f, hsv[1], hsv[2]);
        return rgb | 0xFF000000;
    }

    public static int HSVToColor(int alpha, float[] hsv) {
        return (alpha << 24) | (HSVToColor(hsv) & 0x00FFFFFF);
    }

    private Color() {}
}
