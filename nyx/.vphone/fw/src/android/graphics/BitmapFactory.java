package android.graphics;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/** BitmapFactory over ImageIO, so PNG assets in the APK render for real. */
public class BitmapFactory {

    public static Bitmap decodeFile(String pathName) { return Bitmaps.load(pathName); }

    public static Bitmap decodeFile(String pathName, Options options) {
        Bitmap bitmap = Bitmaps.load(pathName);
        if (bitmap != null && options != null && options.inSampleSize > 1) {
            int size = options.inSampleSize;
            bitmap = Bitmap.createScaledBitmap(bitmap, Math.max(1, bitmap.getWidth() / size),
                    Math.max(1, bitmap.getHeight() / size), true);
        }
        return bitmap;
    }

    public static Bitmap decodeStream(InputStream in) {
        return in == null ? null : Bitmaps.load(in);
    }

    public static Bitmap decodeStream(InputStream in, Rect outPadding, Options options) {
        return decodeStream(in);
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length) {
        if (data == null) return null;
        return Bitmaps.load(new java.io.ByteArrayInputStream(data, offset, length));
    }

    public static class Options {
        public boolean inJustDecodeBounds;
        public int inSampleSize = 1;
        public boolean inMutable;
        public Bitmap.Config inPreferredConfig = Bitmap.Config.ARGB_8888;
    }

    // ------------------------------------------------------------------ files
    public static Bitmap open(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return Bitmaps.load(in);
        } catch (Throwable t) {
            return null;
        }
    }
}
