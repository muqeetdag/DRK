package android.graphics;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * AWT-backed android.graphics.Bitmap. Pixels are TYPE_INT_ARGB, matching
 * Android's in-memory format, so drawing behaviour transfers 1:1.
 */
public class Bitmap {

    public final BufferedImage image;
    private final boolean mutable;

    public Bitmap(BufferedImage image, boolean mutable) {
        this.image = image;
        this.mutable = mutable;
    }

    public static Bitmap createBitmap(int width, int height, Bitmap.Config config) {
        BufferedImage img = new BufferedImage(Math.max(1, width), Math.max(1, height),
                BufferedImage.TYPE_INT_ARGB);
        return new Bitmap(img, true);
    }

    public static Bitmap createBitmap(Bitmap source) {
        BufferedImage copy = new BufferedImage(Math.max(1, source.getWidth()),
                Math.max(1, source.getHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(source.image, 0, 0, null);
        g.dispose();
        return new Bitmap(copy, true);
    }

    public static Bitmap createScaledBitmap(Bitmap source, int width, int height, boolean filter) {
        BufferedImage scaled = new BufferedImage(Math.max(1, width), Math.max(1, height),
                BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                filter ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                        : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(source.image, 0, 0, width, height, null);
        g.dispose();
        return new Bitmap(scaled, true);
    }

    public int getWidth() { return image.getWidth(); }

    public int getHeight() { return image.getHeight(); }

    public int getPixel(int x, int y) { return image.getRGB(x, y); }

    public void setPixel(int x, int y, int color) { image.setRGB(x, y, color); }

    public void eraseColor(int color) {
        Graphics2D g = image.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setColor(new java.awt.Color(color, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();
    }

    public Graphics2D awt() { return image.createGraphics(); }

    public boolean isMutable() { return mutable; }

    public Bitmap copy(Bitmap.Config config, boolean mutable) { return createBitmap(this); }

    public enum Config { ARGB_8888, RGB_565, ALPHA_8 }

    // ------------------------------------------------------------- helpers
    public static int[] pixels(Bitmap bitmap) {
        int[] data = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.image.getRGB(0, 0, bitmap.getWidth(), bitmap.getHeight(), data, 0, bitmap.getWidth());
        return data;
    }

    public static void applyRounded(Bitmap bitmap, float radius) {
        BufferedImage src = bitmap.image;
        BufferedImage mask = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(java.awt.Color.WHITE);
        g.fill(new RoundRectangle2D.Float(0, 0, src.getWidth(), src.getHeight(),
                radius * 2, radius * 2));
        g.dispose();
        java.awt.Composite composite = AlphaComposite.getInstance(AlphaComposite.DST_IN);
        Graphics2D target = src.createGraphics();
        target.setComposite(composite);
        target.drawImage(mask, 0, 0, null);
        target.dispose();
    }

    static Path2D toPath(float[] pts) {
        Path2D.Float path = new Path2D.Float();
        if (pts.length >= 2) path.moveTo(pts[0], pts[1]);
        for (int i = 2; i + 1 < pts.length; i += 2) path.lineTo(pts[i], pts[i + 1]);
        return path;
    }

    static void stroke(Graphics2D g, java.awt.Shape shape, float width) {
        g.setStroke(new BasicStroke(Math.max(0.1f, width), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.draw(shape);
    }

    public static Shape roundedShape(RectF r, float rx, float ry) {
        return new RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2);
    }

    static Rectangle2D rectShape(RectF r) {
        return new Rectangle2D.Float(r.left, r.top, r.width(), r.height());
    }

    static AffineTransform identity() { return new AffineTransform(); }

    static GeneralPath generalPath() { return new GeneralPath(); }
}
