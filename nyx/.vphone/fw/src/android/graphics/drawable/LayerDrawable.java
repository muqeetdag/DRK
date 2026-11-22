package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;

/** Layer list, matching the parts of the API the app touches. */
public class LayerDrawable extends Drawable {

    private final Drawable[] layers;
    private final RectF rect = new RectF();

    public LayerDrawable(Drawable[] layers) {
        this.layers = layers == null ? new Drawable[0] : layers;
        for (Drawable layer : this.layers) {
            if (layer != null) layer.setCallback(new Callback() {
                @Override public void invalidateDrawable(Drawable who) { invalidateSelf(); }
                @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {
                    scheduleSelf(what, when);
                }
                @Override public void unscheduleDrawable(Drawable who, Runnable what) {
                    unscheduleSelf(what);
                }
            });
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
        for (Drawable layer : layers) {
            if (layer == null) continue;
            layer.setBounds(bounds);
            layer.draw(canvas);
        }
    }

    public int getNumberOfLayers() { return layers.length; }

    public Drawable getDrawable(int index) { return layers[index]; }

    public void setLayerInset(int index, int l, int t, int r, int b) {}
}
