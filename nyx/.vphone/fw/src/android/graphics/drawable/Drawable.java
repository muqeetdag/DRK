package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Base drawable. Tracks bounds, visibility and the callback that lets a View
 * redraw itself when the drawable animates.
 */
public abstract class Drawable {

    private final Rect bounds = new Rect();
    private int alpha = 255;
    private boolean visible = true;
    private Callback callback;
    private int level;

    public interface Callback {
        void invalidateDrawable(Drawable who);
        void scheduleDrawable(Drawable who, Runnable what, long when);
        void unscheduleDrawable(Drawable who, Runnable what);
    }

    public abstract void draw(Canvas canvas);

    public void setBounds(int left, int top, int right, int bottom) {
        bounds.set(left, top, right, bottom);
    }

    public void setBounds(Rect rect) { bounds.set(rect); }

    public final Rect getBounds() { return bounds; }

    public Rect copyBounds() { return new Rect(bounds); }

    public void setAlpha(int alpha) { this.alpha = alpha; }

    public int getAlpha() { return alpha; }

    public void setVisible(boolean visible, boolean restart) {
        this.visible = visible;
        if (visible != restart) invalidateSelf();
    }

    public boolean isVisible() { return visible; }

    public void setCallback(Callback cb) { callback = cb; }

    public Callback getCallback() { return callback; }

    public void invalidateSelf() {
        if (callback != null) callback.invalidateDrawable(this);
    }

    public void scheduleSelf(Runnable what, long when) {
        if (callback != null) callback.scheduleDrawable(this, what, when);
    }

    public void unscheduleSelf(Runnable what) {
        if (callback != null) callback.unscheduleDrawable(this, what);
    }

    public boolean setState(int[] stateSet) { return false; }

    public int[] getState() { return new int[0]; }

    public boolean isStateful() { return false; }

    public int getIntrinsicWidth() { return -1; }

    public int getIntrinsicHeight() { return -1; }

    public int getOpacity() { return 255; }

    public void setLevel(int level) { this.level = level; }

    public int getLevel() { return level; }

    public boolean getPadding(Rect padding) {
        if (padding != null) padding.set(0, 0, 0, 0);
        return false;
    }

    public void jumpToCurrentState() {}

    public void clearColorFilter() {}

    public void setTint(int color) {}

    public void setTintList(android.content.res.ColorStateList tint) {}

    public static Drawable createFromPath(String path) { return null; }

    public Drawable mutate() { return this; }

    /** Convenience for bitmap drawables. */
    public static class ConstantState {
        public Drawable newDrawable() { return null; }
    }

    static void fill(Canvas canvas, RectF r, Paint p) { canvas.drawRect(r, p); }
}
