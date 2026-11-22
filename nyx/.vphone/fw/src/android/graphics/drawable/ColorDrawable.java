package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** Solid colour drawable. */
public class ColorDrawable extends Drawable {

    private int color;
    private final Paint paint = new Paint();
    private final RectF rect = new RectF();

    public ColorDrawable() {}

    public ColorDrawable(int color) { this.color = color; }

    public void setColor(int color) { this.color = color; }

    public int getColor() { return color; }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        rect.set(b.left, b.top, b.right, b.bottom);
        paint.setColor(color);
        canvas.drawRect(rect, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        color = (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
        super.setAlpha(alpha);
    }
}
