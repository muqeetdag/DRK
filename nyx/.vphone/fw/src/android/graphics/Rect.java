package android.graphics;

/** Integer rectangle, matching android.graphics.Rect semantics. */
public class Rect {

    public int left, top, right, bottom;

    public Rect() {}

    public Rect(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public Rect(Rect source) {
        if (source != null) set(source);
    }

    public void set(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public void set(Rect other) { set(other.left, other.top, other.right, other.bottom); }

    public void setEmpty() { set(0, 0, 0, 0); }

    public boolean isEmpty() { return left >= right || top >= bottom; }

    public int width() { return right - left; }

    public int height() { return bottom - top; }

    public int centerX() { return (left + right) >> 1; }

    public int centerY() { return (top + bottom) >> 1; }

    public void offset(int dx, int dy) {
        left += dx;
        top += dy;
        right += dx;
        bottom += dy;
    }

    public void inset(int dx, int dy) {
        left += dx;
        top += dy;
        right -= dx;
        bottom -= dy;
    }

    public boolean contains(int x, int y) {
        return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom;
    }

    public boolean intersect(Rect other) {
        int l = Math.max(left, other.left);
        int t = Math.max(top, other.top);
        int r = Math.min(right, other.right);
        int b = Math.min(bottom, other.bottom);
        if (l < r && t < b) {
            set(l, t, r, b);
            return true;
        }
        return false;
    }

    @Override public String toString() { return "Rect(" + left + ", " + top + " - " + right + ", " + bottom + ")"; }
}
