package android.graphics;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

import javax.imageio.ImageIO;

/** Image loading (PNG/JPG via ImageIO, standalone VectorDrawable PNGs). */
public final class Bitmaps {

    private Bitmaps() {}

    public static Bitmap load(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) return null;
            BufferedImage img = ImageIO.read(file);
            if (img == null) return null;
            if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
                BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(),
                        BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = argb.createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
                img = argb;
            }
            return new Bitmap(img, true);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Bitmap load(InputStream in) {
        try {
            BufferedImage img = ImageIO.read(in);
            if (img == null) return null;
            if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
                BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(),
                        BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = argb.createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
                img = argb;
            }
            return new Bitmap(img, true);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Bitmap loadResource(String resPath, int targetSize) {
        Bitmap bitmap = load(resPath);
        if (bitmap == null) return null;
        if (targetSize > 0 && bitmap.getWidth() != targetSize) {
            return Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true);
        }
        return bitmap;
    }
}
