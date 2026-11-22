package android.graphics;

import java.awt.Font;

/** Android typeface names mapped onto the AWT logical fonts. */
public class Typeface {

    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int BOLD_ITALIC = 3;

    public static final Typeface DEFAULT = new Typeface("sans-serif", 0);
    public static final Typeface DEFAULT_BOLD = new Typeface("sans-serif", BOLD);
    public static final Typeface SANS_SERIF = new Typeface("sans-serif", 0);
    public static final Typeface SERIF = new Typeface("serif", 0);
    public static final Typeface MONOSPACE = new Typeface("monospace", 0);

    final String family;
    final int style;

    private Typeface(String family, int style) {
        this.family = family;
        this.style = style;
    }

    public static Typeface create(String familyName, int style) {
        return new Typeface(familyName == null ? "sans-serif" : familyName, style);
    }

    public static Typeface create(Typeface family, int style) {
        return new Typeface(family == null ? "sans-serif" : family.family, style);
    }

    public static Typeface defaultFromStyle(int style) {
        return style == 0 ? DEFAULT : DEFAULT_BOLD;
    }

    public int getStyle() { return style; }

    public boolean isBold() { return (style & BOLD) != 0; }

    public boolean isItalic() { return (style & ITALIC) != 0; }

    public String familyName() { return family; }

    /** AWT only ships a handful of families; map Android names onto them. */
    static Font font(Typeface typeface, float sizePx, boolean fakeBold) {
        Typeface tf = typeface == null ? DEFAULT : typeface;
        String name = tf.family == null ? "" : tf.family.toLowerCase();
        String awtFamily;
        if (name.contains("mono")) awtFamily = Font.MONOSPACED;
        else if (name.contains("serif") && !name.contains("sans")) awtFamily = Font.SERIF;
        else awtFamily = Font.SANS_SERIF;

        int awtStyle = Font.PLAIN;
        boolean bold = tf.isBold() || fakeBold || name.contains("black") || name.contains("bold");
        if (bold) awtStyle |= Font.BOLD;
        if (tf.isItalic()) awtStyle |= Font.ITALIC;
        return new Font(awtFamily, awtStyle, Math.max(1, Math.round(sizePx)));
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Typeface)) return false;
        Typeface other = (Typeface) o;
        return style == other.style && family.equals(other.family);
    }

    @Override public int hashCode() { return family.hashCode() * 31 + style; }
}
