package android.graphics;

import java.awt.Graphics2D;

/** Paint: colour, text size, stroke, shaders, alpha and letter spacing. */
public class Paint {

    public static final int ANTI_ALIAS_FLAG = 1;
    public static final int FILTER_BITMAP_FLAG = 2;
    public static final int DITHER_FLAG = 4;
    public static final int UNDERLINE_TEXT_FLAG = 8;
    public static final int STRIKE_THRU_TEXT_FLAG = 16;
    public static final int FAKE_BOLD_TEXT_FLAG = 32;

    public enum Style { FILL, STROKE, FILL_AND_STROKE }

    public enum Cap { BUTT, ROUND, SQUARE }

    public enum Join { MITER, ROUND, BEVEL }

    public enum Align { LEFT, CENTER, RIGHT }

    public int color = Color.BLACK;
    public float textSize = 16f;
    public float strokeWidth = 1f;
    public Style style = Style.FILL;
    public Typeface typeface;
    public Align align = Align.LEFT;
    public boolean antiAlias = true;
    public boolean fakeBold;
    public boolean underline;
    public float letterSpacing = 0f;
    public float shadowRadius = 0f;
    public float shadowDx = 0f, shadowDy = 0f;
    public int shadowColor = 0;
    public Shader shader;
    public float textScaleX = 1f;
    public Cap cap = Cap.BUTT;
    public Join join = Join.MITER;

    public Paint() {}

    public Paint(int flags) { antiAlias = (flags & ANTI_ALIAS_FLAG) != 0; }

    public Paint(Paint other) { set(other); }

    public void set(Paint other) {
        if (other == null) return;
        color = other.color;
        textSize = other.textSize;
        strokeWidth = other.strokeWidth;
        style = other.style;
        typeface = other.typeface;
        align = other.align;
        antiAlias = other.antiAlias;
        fakeBold = other.fakeBold;
        underline = other.underline;
        letterSpacing = other.letterSpacing;
        shadowRadius = other.shadowRadius;
        shadowDx = other.shadowDx;
        shadowDy = other.shadowDy;
        shadowColor = other.shadowColor;
        shader = other.shader;
        textScaleX = other.textScaleX;
    }

    public void reset() {
        color = Color.BLACK;
        textSize = 16f;
        strokeWidth = 1f;
        style = Style.FILL;
        typeface = null;
        align = Align.LEFT;
        letterSpacing = 0f;
        shadowRadius = 0f;
        shader = null;
        fakeBold = false;
    }

    public void setColor(int color) { this.color = color; }

    public int getColor() { return color; }

    public void setAlpha(int alpha) {
        this.color = (this.color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public int getAlpha() { return color >>> 24; }

    public void setTextSize(float size) { this.textSize = size; }

    public float getTextSize() { return textSize; }

    public void setStrokeWidth(float width) { this.strokeWidth = width; }

    public float getStrokeWidth() { return strokeWidth; }

    public void setStyle(Style style) { this.style = style; }

    public Style getStyle() { return style; }

    public boolean isStroke() { return style == Style.STROKE; }

    public void setTypeface(Typeface tf) { this.typeface = tf; }

    public Typeface getTypeface() { return typeface; }

    public void setAntiAlias(boolean on) { antiAlias = on; }

    public void setShader(Shader shader) { this.shader = shader; }

    public Shader getShader() { return shader; }

    public void setLetterSpacing(float spacing) { letterSpacing = spacing; }

    public float getLetterSpacing() { return letterSpacing; }

    public void setTextAlign(Align align) { this.align = align; }

    public Align getTextAlign() { return align; }

    public void setFakeBoldText(boolean bold) { fakeBold = bold; }

    public void setUnderlineText(boolean underline) { this.underline = underline; }

    public void setShadowLayer(float radius, float dx, float dy, int color) {
        shadowRadius = radius;
        shadowDx = dx;
        shadowDy = dy;
        shadowColor = color;
    }

    public void clearShadowLayer() { shadowRadius = 0f; }

    public void setTextScaleX(float scale) { textScaleX = scale; }

    public void setStrokeCap(Cap cap) { this.cap = cap; }

    public void setStrokeJoin(Join join) { this.join = join; }

    private static Canvas metricsCanvas;

    private static synchronized Canvas metrics() {
        if (metricsCanvas == null) {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1, 1,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            metricsCanvas = new Canvas(img.createGraphics());
        }
        return metricsCanvas;
    }

    public float measureText(String text) { return measureText(text, metrics()); }

    public float measureText(String text, Canvas canvas) {
        if (text == null) return 0f;
        float width = canvas.measureTextWidth(text, this);
        if (fakeBold) width += text.length() * 0.35f;
        return width * textScaleX;
    }

    public float measureText(CharSequence text) { return measureText(text == null ? "" : text.toString()); }

    /** Applies every attribute to the AWT graphics before a draw. */
    public void prepare(Graphics2D g) {
        java.awt.Color base = new java.awt.Color(color, true);
        if (shader != null) {
            g.setPaint(shader.paintFor(g.getTransform()));
        } else {
            g.setColor(base);
        }
        if (shadowRadius > 0f && shadowColor != 0) {
            // A soft drop shadow approximated with a translucent offset copy.
            g.setComposite(java.awt.AlphaComposite.SrcOver);
        }
    }

    public java.awt.Font font() {
        return Typeface.font(typeface, textSize, fakeBold);
    }

    public float letterSpacingPx(String text) {
        if (letterSpacing == 0f || text == null || text.isEmpty()) return 0f;
        return letterSpacing * textSize;
    }

    public void setFlags(int flags) { antiAlias = (flags & ANTI_ALIAS_FLAG) != 0; }

    public int getFlags() { return antiAlias ? ANTI_ALIAS_FLAG : 0; }
}
