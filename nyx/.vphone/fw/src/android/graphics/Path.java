package android.graphics;

import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;

/** android.graphics.Path with the subset our code draws. */
public class Path {

    private final Path2D.Float path = new Path2D.Float();
    public float offsetX = 0f, offsetY = 0f;

    public void moveTo(float x, float y) { path.moveTo(x, y); }

    public void lineTo(float x, float y) { path.lineTo(x, y); }

    public void quadTo(float x1, float y1, float x2, float y2) {
        path.quadTo(x1, y1, x2, y2);
    }

    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        java.awt.geom.Point2D current = path.getCurrentPoint();
        double cx = current == null ? x1 : current.getX();
        double cy = current == null ? y1 : current.getY();
        path.append(new java.awt.geom.CubicCurve2D.Double(cx, cy, x1, y1, x2, y2, x3, y3), true);
    }

    public void close() { path.closePath(); }

    public void reset() { path.reset(); }

    public void rewind() { path.reset(); }

    public void addCircle(float x, float y, float radius, Direction dir) {
        path.append(new java.awt.geom.Ellipse2D.Float(x - radius, y - radius, radius * 2, radius * 2),
                false);
    }

    public void addRoundRect(RectF rect, float rx, float ry, Direction dir) {
        path.append(new java.awt.geom.RoundRectangle2D.Float(rect.left, rect.top, rect.width(),
                rect.height(), rx * 2, ry * 2), false);
    }

    public void addRect(RectF rect, Direction dir) {
        path.append(new java.awt.geom.Rectangle2D.Float(rect.left, rect.top, rect.width(),
                rect.height()), false);
    }

    public void arcTo(RectF oval, float startAngle, float sweepAngle, boolean forceMoveTo) {
        java.awt.geom.Arc2D.Float arc = new java.awt.geom.Arc2D.Float(oval.left, oval.top,
                oval.width(), oval.height(), startAngle, sweepAngle, java.awt.geom.Arc2D.OPEN);
        path.append(arc, !forceMoveTo);
    }

    public boolean isEmpty() { return path.getCurrentPoint() == null; }

    public void offset(int dx, int dy) { offsetX += dx; offsetY += dy; }

    public void offset(float dx, float dy) { offsetX += dx; offsetY += dy; }

    public void setFillType(FillType type) {}

    public void transform(Matrix matrix) {
        if (matrix != null) path.transform(matrix.awt());
    }

    public Path2D.Float awt() { return path; }

    public enum Direction { CW, CCW }

    public enum FillType { WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD }

    public enum Op { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }
}
