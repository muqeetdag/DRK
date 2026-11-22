package android.graphics.drawable;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmaps;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

/** Draws a bitmap, scaled to the bounds (like BitmapDrawable with default gravity). */
public class BitmapDrawable extends Drawable {

    private Bitmap bitmap;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    public BitmapDrawable(Bitmap bitmap) { this.bitmap = bitmap; }

    public BitmapDrawable(Resources res, Bitmap bitmap) { this(bitmap); }

    public BitmapDrawable(Resources res, String filePath) { this(Bitmaps.load(filePath)); }

    public Bitmap getBitmap() { return bitmap; }

    public void setBitmap(Bitmap bitmap) { this.bitmap = bitmap; }

    @Override
    public int getIntrinsicWidth() { return bitmap == null ? -1 : bitmap.getWidth(); }

    @Override
    public int getIntrinsicHeight() { return bitmap == null ? -1 : bitmap.getHeight(); }

    @Override
    public void draw(Canvas canvas) {
        if (bitmap == null) return;
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        canvas.drawBitmap(bitmap, src,
                new android.graphics.RectF(b.left, b.top, b.right, b.bottom), paint);
    }
}
