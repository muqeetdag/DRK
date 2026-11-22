package android.graphics;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Software Canvas. Supports every operation the app uses: paths, round rects,
 * ovals, text with letter-spacing, shaders (linear/radial/sweep), clipping,
 * alpha compositing and save/restore. Rendering happens through AWT, so the
 * output is a truthful picture of the layout maths, not a mock sketch.
 */
public class Canvas {

    private final Graphics2D g;
    private final Deque<State> state = new ArrayDeque<>();
    private final Deque<Rect> clipStack = new ArrayDeque<>();
    public final Rect clipBounds = new Rect();
    public final Rect bounds = new Rect();

    public Canvas(Bitmap bitmap) {
        this(bitmap.awt());
        bounds.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        clipBounds.set(bounds);
    }

    public Canvas(java.awt.image.BufferedImage image) {
        this(image.createGraphics());
    }

    public Canvas(Graphics2D graphics) {
        this.g = graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    public Graphics2D awt() { return g; }

    public int getWidth() { return bounds.width(); }

    public int getHeight() { return bounds.height(); }

    public void translate(float dx, float dy) { g.translate(dx, dy); }

    public void scale(float sx, float sy) { g.scale(sx, sy); }

    public void rotate(float degrees) { g.rotate(Math.toRadians(degrees)); }

    public int save() {
        state.push(new State(new java.awt.geom.AffineTransform(g.getTransform()), g.getClip()));
        return state.size();
    }

    public void restore() {
        if (state.isEmpty()) return;
        State saved = state.pop();
        g.setTransform(saved.transform);
        g.setClip(saved.clip);
    }

    public void restoreToCount(int count) {
        // Android semantics: restore until getSaveCount() == count, i.e. the save
        // taken when save() returned `count` is popped as well.
        while (getSaveCount() > count) restore();
    }

    public int getSaveCount() { return state.size() + 1; }

    private static final class State {
        final java.awt.geom.AffineTransform transform;
        final java.awt.Shape clip;

        State(java.awt.geom.AffineTransform transform, java.awt.Shape clip) {
            this.transform = transform;
            this.clip = clip;
        }
    }

    public boolean clipRect(Rect rect) {
        g.clip(new Rectangle2D.Float(rect.left, rect.top, rect.width(), rect.height()));
        clipBounds.set(rect);
        return true;
    }

    public boolean clipRect(float left, float top, float right, float bottom) {
        g.clip(new Rectangle2D.Float(left, top, right - left, bottom - top));
        clipBounds.set(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom));
        return true;
    }

    public boolean clipRect(RectF rect) {
        return clipRect(rect.left, rect.top, rect.right, rect.bottom);
    }

    public boolean clipPath(Path path) {
        g.clip(path.awt());
        return true;
    }

    public void drawColor(int color) {
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        g.setColor(new java.awt.Color(color, true));
        g.fillRect(-100000, -100000, 200000, 200000);
    }

    public void drawPaint(Paint paint) {
        paint.prepare(g);
        g.fillRect(-100000, -100000, 200000, 200000);
    }

    public void drawRect(RectF rect, Paint paint) {
        paint.prepare(g);
        if (paint.isStroke()) {
            Bitmap.stroke(g, Bitmap.rectShape(rect), paint.strokeWidth);
        } else {
            g.fill(Bitmap.rectShape(rect));
        }
    }

    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        drawRect(new RectF(left, top, right, bottom), paint);
    }

    public void drawRect(Rect rect, Paint paint) {
        drawRect(new RectF(rect.left, rect.top, rect.right, rect.bottom), paint);
    }

    public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
        paint.prepare(g);
        java.awt.Shape shape = Bitmap.roundedShape(rect, Math.max(0, rx), Math.max(0, ry));
        if (paint.isStroke()) Bitmap.stroke(g, shape, paint.strokeWidth);
        else g.fill(shape);
    }

    public void drawRoundRect(float left, float top, float right, float bottom, float rx, float ry,
                              Paint paint) {
        drawRoundRect(new RectF(left, top, right, bottom), rx, ry, paint);
    }

    public void drawOval(RectF rect, Paint paint) {
        paint.prepare(g);
        java.awt.Shape shape = new Ellipse2D.Float(rect.left, rect.top, rect.width(), rect.height());
        if (paint.isStroke()) Bitmap.stroke(g, shape, paint.strokeWidth);
        else g.fill(shape);
    }

    public void drawOval(float left, float top, float right, float bottom, Paint paint) {
        drawOval(new RectF(left, top, right, bottom), paint);
    }

    public void drawCircle(float cx, float cy, float radius, Paint paint) {
        paint.prepare(g);
        java.awt.Shape shape = new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2);
        if (paint.isStroke()) Bitmap.stroke(g, shape, paint.strokeWidth);
        else g.fill(shape);
    }

    public void drawLine(float startX, float startY, float stopX, float stopY, Paint paint) {
        paint.prepare(g);
        Bitmap.stroke(g, new java.awt.geom.Line2D.Float(startX, startY, stopX, stopY),
                paint.strokeWidth);
    }

    public void drawPath(Path path, Paint paint) {
        paint.prepare(g);
        // The path itself carries the current translate offset when built by callers.
        AffineTransform saved = g.getTransform();
        g.translate(path.offsetX, path.offsetY);
        java.awt.Shape shape = path.awt();
        if (paint.isStroke()) Bitmap.stroke(g, shape, paint.strokeWidth);
        else g.fill(shape);
        g.setTransform(saved);
    }

    public void drawBitmap(Bitmap bitmap, float left, float top, Paint paint) {
        if (bitmap == null) return;
        g.drawImage(bitmap.image, Math.round(left), Math.round(top), null);
    }

    public void drawBitmap(Bitmap bitmap, Rect src, RectF dst, Paint paint) {
        if (bitmap == null) return;
        g.drawImage(bitmap.image,
                Math.round(dst.left), Math.round(dst.top), Math.round(dst.right), Math.round(dst.bottom),
                src.left, src.top, src.right, src.bottom, null);
    }

    public void drawBitmap(Bitmap bitmap, Rect src, Rect dst, Paint paint) {
        if (bitmap == null) return;
        g.drawImage(bitmap.image, dst.left, dst.top, dst.right, dst.bottom,
                src.left, src.top, src.right, src.bottom, null);
    }

    public void drawText(String text, float x, float y, Paint paint) {
        if (text == null || text.isEmpty()) return;
        paint.prepare(g);
        java.awt.Font font = paint.font();
        g.setFont(font);
        float spacing = paint.letterSpacingPx(text);
        if (Math.abs(spacing) < 0.01f) {
            g.drawString(text, x, y);
            return;
        }
        float cursor = x;
        FontMetrics metrics = g.getFontMetrics(font);
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            g.drawString(ch, cursor, y);
            cursor += metrics.stringWidth(ch) + spacing;
        }
    }

    public void drawText(char[] text, int index, int count, float x, float y, Paint paint) {
        drawText(new String(text, index, count), x, y, paint);
    }

    public void drawText(CharSequence text, int start, int end, float x, float y, Paint paint) {
        if (text == null) return;
        int s = Math.max(0, start);
        int e = Math.min(text.length(), end);
        if (e <= s) return;
        drawText(text.subSequence(s, e).toString(), x, y, paint);
    }

    public void drawTextOnPath(String text, Path path, float hOffset, float vOffset, Paint paint) {
        // Not used by the app; kept for API completeness.
        drawText(text, path.offsetX + hOffset, path.offsetY + vOffset, paint);
    }

    public void drawArc(RectF oval, float startAngle, float sweepAngle, boolean useCenter,
                        Paint paint) {
        paint.prepare(g);
        java.awt.geom.Arc2D.Float arc = new java.awt.geom.Arc2D.Float(oval.left, oval.top,
                oval.width(), oval.height(), startAngle, sweepAngle,
                useCenter ? java.awt.geom.Arc2D.PIE : java.awt.geom.Arc2D.OPEN);
        if (paint.isStroke()) Bitmap.stroke(g, arc, paint.strokeWidth);
        else g.fill(arc);
    }

    /** Font metrics the way Android reports them. */
    public float measureTextWidth(String text, Paint paint) {
        if (text == null) return 0;
        java.awt.Font font = paint.font();
        FontMetrics metrics = g.getFontMetrics(font);
        float spacing = paint.letterSpacingPx(text);
        if (Math.abs(spacing) < 0.01f) return metrics.stringWidth(text);
        float total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += metrics.stringWidth(String.valueOf(text.charAt(i))) + spacing;
        }
        return Math.max(0, total - spacing);
    }

    public static Path2D.Float emptyPath() { return new Path2D.Float(); }

    public static RoundRectangle2D.Float roundRect(float l, float t, float r, float b, float rx, float ry) {
        return new RoundRectangle2D.Float(l, t, r - l, b - t, rx * 2, ry * 2);
    }

    public static BasicStroke strokeOf(float width) {
        return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    public static void hint(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
}
