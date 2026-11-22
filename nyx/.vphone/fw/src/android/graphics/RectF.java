package android.graphics;

/** Immutable float rectangle, matching android.graphics.RectF. */
public class RectF {

    public float left, top, right, bottom;

    public RectF() {}

    public RectF(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public RectF(Rect source) {
        this.left = source.left;
        this.top = source.top;
        this.right = source.right;
        this.bottom = source.bottom;
    }

    public void set(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public void set(RectF other) { set(other.left, other.top, other.right, other.bottom); }

    public void set(Rect other) {
        set(other.left, other.top, other.right, other.bottom);
    }

    public void setEmpty() { set(0, 0, 0, 0); }

    public boolean isEmpty() { return left >= right || top >= bottom; }

    public float width() { return right - left; }

    public float height() { return bottom - top; }

    public float centerX() { return (left + right) * 0.5f; }

    public float centerY() { return (top + bottom) * 0.5f; }

    public void offset(float dx, float dy) {
        left += dx;
        top += dy;
        right += dx;
        bottom += dy;
    }

    public void inset(float dx, float dy) {
        left += dx;
        top += dy;
        right -= dx;
        bottom -= dy;
    }

    public boolean contains(float x, float y) {
        return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom;
    }

    public void round(Rect dst) {
        dst.left = Math.round(left);
        dst.top = Math.round(top);
        dst.right = Math.round(right);
        dst.bottom = Math.round(bottom);
    }

    @Override public String toString() { return "RectF(" + left + ", " + top + ", " + right + ", " + bottom + ")"; }
}
