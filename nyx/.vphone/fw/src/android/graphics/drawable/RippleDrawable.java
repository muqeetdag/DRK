package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** RippleDrawable: keeps the content drawable, paints a press ripple on top. */
public class RippleDrawable extends LayerDrawable {

    private final ColorStateList color;
    private final Drawable content;
    private boolean pressed;
    private long pressStart;
    private final Paint ripple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public RippleDrawable(ColorStateList color, Drawable content, Drawable mask) {
        super(content == null ? new Drawable[0] : new Drawable[]{content});
        this.color = color;
        this.content = content;
    }

    public ColorStateList getRippleColor() { return color; }

    @Override
    public boolean setState(int[] stateSet) {
        boolean wasPressed = pressed;
        boolean isPressed = false;
        for (int state : stateSet) {
            if (state == android.R.attr.state_pressed) isPressed = true;
        }
        if (isPressed && !wasPressed) pressStart = System.currentTimeMillis();
        pressed = isPressed;
        return wasPressed != pressed;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        rect.set(b.left, b.top, b.right, b.bottom);
        if (content != null) content.draw(canvas);
        if (pressed && color != null) {
            int argb = color.getDefaultColor();
            float t = Math.min(1f, (System.currentTimeMillis() - pressStart) / 320f);
            ripple.setColor((argb & 0x00FFFFFF) | (((int) (70 * (1f - t))) << 24));
            float radius = Math.max(rect.width(), rect.height()) * 0.5f * (0.3f + 0.7f * t);
            canvas.drawCircle(rect.centerX(), rect.centerY(), radius, ripple);
        }
    }
}
