package android.graphics;

import java.awt.Color;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Shaders. Colours are provided as Android ARGB ints; each shader builds the
 * matching AWT paint the first time it is used, so gradients look correct
 * (including the zero-size case, which throws here like it does on Android —
 * that is exactly the crash class the app guards against).
 */
public abstract class Shader {

    public enum TileMode { CLAMP, REPEAT, MIRROR }

    final int[] colors;
    final float[] positions;
    final TileMode tileMode;
    final boolean radial;

    Shader(int[] colors, float[] positions, TileMode tileMode, boolean radial) {
        if (colors == null || colors.length == 0) {
            throw new IllegalArgumentException("needs >= 1 colors");
        }
        if (positions != null && positions.length != colors.length) {
            throw new IllegalArgumentException("color and position arrays must be of equal length");
        }
        this.colors = colors.clone();
        this.positions = positions == null ? null : positions.clone();
        this.tileMode = tileMode == null ? TileMode.CLAMP : tileMode;
        this.radial = radial;
    }

    public void setLocalMatrix(Matrix matrix) {}

    public boolean hasLocalMatrix() { return false; }

    /** Builds an AWT paint in the current user space. */
    protected abstract java.awt.Paint paint(AffineTransform transform);

    final java.awt.Paint paintFor(AffineTransform transform) { return paint(transform); }

    static Color[] awt(int[] colors) {
        Color[] out = new Color[colors.length];
        for (int i = 0; i < colors.length; i++) out[i] = new Color(colors[i], true);
        return out;
    }

    static float[] fractions(float[] positions, int count) {
        if (positions != null) {
            float[] copy = new float[count];
            for (int i = 0; i < count; i++) {
                float v = i < positions.length ? positions[i] : i / (float) (count - 1);
                copy[i] = Math.max(0f, Math.min(1f, v));
            }
            // Fractions must be strictly ascending for AWT.
            for (int i = 1; i < copy.length; i++) {
                if (copy[i] <= copy[i - 1]) copy[i] = Math.min(1f, copy[i - 1] + 0.0001f);
            }
            return copy;
        }
        float[] even = new float[count];
        for (int i = 0; i < count; i++) even[i] = count == 1 ? 0f : i / (float) (count - 1);
        return even;
    }

    static MultipleGradientPaint.CycleMethod cycle(TileMode mode) {
        switch (mode) {
            case REPEAT: return MultipleGradientPaint.CycleMethod.REPEAT;
            case MIRROR: return MultipleGradientPaint.CycleMethod.REFLECT;
            default: return MultipleGradientPaint.CycleMethod.NO_CYCLE;
        }
    }

    public static class LinearGradient extends Shader {
        private final float x0, y0, x1, y1;

        public LinearGradient(float x0, float y0, float x1, float y1, int[] colors,
                              float[] positions, TileMode tile) {
            super(colors, positions, tile, false);
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        public LinearGradient(float x0, float y0, float x1, float y1, int color0, int color1,
                              TileMode tile) {
            this(x0, y0, x1, y1, new int[]{color0, color1}, null, tile);
        }

        @Override protected java.awt.Paint paint(AffineTransform transform) {
            return new LinearGradientPaint(new Point2D.Float(x0, y0), new Point2D.Float(x1, y1),
                    fractions(positions, colors.length), awt(colors), cycle(tileMode));
        }
    }

    public static class RadialGradient extends Shader {
        private final float cx, cy, radius;

        public RadialGradient(float centerX, float centerY, float radius, int[] colors,
                              float[] stops, TileMode tile) {
            super(colors, stops, tile, true);
            // This mirrors Android: a non-positive radius is a programming error.
            if (radius <= 0) throw new IllegalArgumentException("radius must be > 0");
            this.cx = centerX;
            this.cy = centerY;
            this.radius = radius;
        }

        public RadialGradient(float centerX, float centerY, float radius, int color0, int color1,
                              TileMode tile) {
            this(centerX, centerY, radius, new int[]{color0, color1}, null, tile);
        }

        @Override protected java.awt.Paint paint(AffineTransform transform) {
            return new RadialGradientPaint(new Point2D.Float(cx, cy), radius,
                    fractions(positions, colors.length), awt(colors), cycle(tileMode));
        }
    }

    public static class SweepGradient extends Shader {
        private final float cx, cy;

        public SweepGradient(float cx, float cy, int[] colors, float[] positions) {
            super(colors, positions, TileMode.CLAMP, false);
            this.cx = cx;
            this.cy = cy;
        }

        public SweepGradient(float cx, float cy, int color0, int color1) {
            this(cx, cy, new int[]{color0, color1}, null);
        }

        @Override protected java.awt.Paint paint(AffineTransform transform) {
            // Approximate a sweep with a conic-like ring of many radial pieces.
            return new java.awt.GradientPaint(cx - 100, cy, awt(colors)[0],
                    cx + 100, cy, awt(colors)[colors.length - 1]);
        }
    }

    public static class BitmapShader extends Shader {
        private final Bitmap bitmap;

        public BitmapShader(Bitmap bitmap, TileMode tileX, TileMode tileY) {
            super(new int[]{0xFFFFFFFF}, null, tileX, false);
            this.bitmap = bitmap;
        }

        @Override protected java.awt.Paint paint(AffineTransform transform) {
            Rectangle2D anchor = new Rectangle2D.Float(0, 0, bitmap.getWidth(), bitmap.getHeight());
            return new java.awt.TexturePaint(bitmap.image, anchor);
        }
    }
}
