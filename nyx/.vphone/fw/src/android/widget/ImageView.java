package android.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/** ImageView: bitmap or resource, scaled to bounds. */
public class ImageView extends android.view.View {

    private Drawable drawable;
    private int scaleType = 1;
    private boolean adjustViewBounds;
    private int tintColor = 0;

    public ImageView(Context context) { super(context); }

    public ImageView(Context context, android.util.AttributeSet attrs) { this(context); }

    public void setImageResource(int resId) {
        drawable = android.content.ResBridge.drawable(resId);
        requestLayout();
        invalidate();
    }

    public void setImageDrawable(Drawable drawable) {
        this.drawable = drawable;
        requestLayout();
        invalidate();
    }

    public void setImageBitmap(Bitmap bitmap) {
        this.drawable = bitmap == null ? null : new android.graphics.drawable.BitmapDrawable(bitmap);
        requestLayout();
        invalidate();
    }

    public Drawable getDrawable() { return drawable; }

    public void setScaleType(int type) { scaleType = type; }

    public void setAdjustViewBounds(boolean adjust) { adjustViewBounds = adjust; }

    public void setColorFilter(int color) {}

    public void setImageTintList(android.content.res.ColorStateList tint) {
        if (tint != null) tintColor = tint.getDefaultColor();
    }

    public void setImageAlpha(int alpha) {}

    @Override
    protected void onDraw(Canvas canvas) {
        if (drawable == null || getWidth() <= 0 || getHeight() <= 0) return;
        drawable.setBounds(0, 0, getWidth(), getHeight());
        drawable.draw(canvas);
    }
}
