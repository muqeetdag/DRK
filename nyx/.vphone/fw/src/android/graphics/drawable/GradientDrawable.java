package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * GradientDrawable: solid fill, corner radius, stroke, and linear/radial
 * gradients — everything the console builds at runtime.
 */
public class GradientDrawable extends Drawable {

    public enum Orientation {
        TOP_BOTTOM, TR_BL, RIGHT_LEFT, LEFT_RIGHT, BL_TR, BOTTOM_TOP, TL_BR
    }

    public enum ShapeType { RECTANGLE, OVAL, LINE, RING }

    public static final int RECTANGLE = 0;
    public static final int OVAL = 1;
    public static final int LINE = 2;
    public static final int RING = 3;

    private int shape = 0; // RECTANGLE
    private int solidColor = Color.TRANSPARENT;
    private int strokeWidth = 0;
    private int strokeColor = Color.TRANSPARENT;
    private float cornerRadius = 0f;
    private float[] corners = null;
    private Orientation orientation = Orientation.TOP_BOTTOM;
    private int[] gradientColors = null;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public GradientDrawable() {}

    public GradientDrawable(Orientation orientation, int[] colors) {
        this.orientation = orientation;
        this.gradientColors = colors;
    }

    public void setShape(int shape) { this.shape = shape; }

    public void setGradientColors(int[] colors) { gradientColors = colors; }

    public void setColors(int[] colors) { gradientColors = colors; }

    public int getShape() { return shape; }

    public void setColor(int argb) {
        solidColor = argb;
        gradientColors = null;
    }

    public void setCornerRadius(float radius) {
        cornerRadius = Math.max(0f, radius);
        corners = null;
    }

    public void setCornerRadii(float[] radii) { corners = radii; }

    public void setStroke(int width, int color) {
        strokeWidth = width;
        strokeColor = color;
    }

    public void setStroke(int width, int color, float dashWidth, float dashGap) {
        setStroke(width, color);
    }

    public void setGradientType(int type) {}

    public void setGradientCenter(float x, float y) {}

    public void setGradientRadius(float radius) {}

    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        super.setAlpha(alpha);
    }

    public int getColor() { return solidColor; }

    public float getCornerRadius() { return cornerRadius; }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
        if (rect.isEmpty() || rect.width() <= 0f || rect.height() <= 0f) return;

        float rx = cornerRadius;
        if (corners != null && corners.length > 0) rx = corners[0];

        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokeWidth);
        stroke.setColor(strokeColor);
        stroke.setStyle(Paint.Style.STROKE);

        if (gradientColors != null && gradientColors.length >= 2) {
            fill.setShader(buildShader(rect));
        } else {
            fill.setShader(null);
            fill.setColor(solidColor);
        }

        if (shape == OVAL) {
            canvas.drawOval(rect, fill);
            if (strokeWidth > 0) canvas.drawOval(rect, stroke);
            return;
        }
        if (shape == LINE) {
            canvas.drawLine(rect.left, rect.centerY(), rect.right, rect.centerY(), stroke);
            return;
        }
        canvas.drawRoundRect(rect, rx, rx, fill);
        if (strokeWidth > 0) {
            float inset = strokeWidth * 0.5f;
            RectF line = new RectF(rect.left + inset, rect.top + inset,
                    rect.right - inset, rect.bottom - inset);
            canvas.drawRoundRect(line, Math.max(0, rx - inset), Math.max(0, rx - inset), stroke);
        }
    }

    private Shader buildShader(RectF r) {
        int[] c = gradientColors;
        float cx = r.centerX();
        float cy = r.centerY();
        float radius = Math.max(r.width(), r.height()) * 0.5f;
        if (radius <= 0f) radius = 1f;
        switch (orientation) {
            case TL_BR:
                return new Shader.LinearGradient(r.left, r.top, r.right, r.bottom, c, null,
                        Shader.TileMode.CLAMP);
            case TR_BL:
                return new Shader.LinearGradient(r.right, r.top, r.left, r.bottom, c, null,
                        Shader.TileMode.CLAMP);
            case LEFT_RIGHT:
                return new Shader.LinearGradient(r.left, cy, r.right, cy, c, null,
                        Shader.TileMode.CLAMP);
            case RIGHT_LEFT:
                return new Shader.LinearGradient(r.right, cy, r.left, cy, c, null,
                        Shader.TileMode.CLAMP);
            case BOTTOM_TOP:
                return new Shader.LinearGradient(cx, r.bottom, cx, r.top, c, null,
                        Shader.TileMode.CLAMP);
            case BL_TR:
                return new Shader.LinearGradient(r.left, r.bottom, r.right, r.top, c, null,
                        Shader.TileMode.CLAMP);
            case TOP_BOTTOM:
            default:
                return new Shader.LinearGradient(cx, r.top, cx, r.bottom, c, null,
                        Shader.TileMode.CLAMP);
        }
    }

    @Override
    public boolean isStateful() { return false; }

    private int[] noExtras() { return new int[0]; }
}
