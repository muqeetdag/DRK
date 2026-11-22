package android.graphics;

import java.awt.geom.AffineTransform;

/** Thin AffineTransform wrapper. */
public class Matrix {

    final AffineTransform tx = new AffineTransform();

    public void reset() { tx.setToIdentity(); }

    public void setTranslate(float dx, float dy) { tx.setToTranslation(dx, dy); }

    public void setScale(float sx, float sy) { tx.setToScale(sx, sy); }

    public void postTranslate(float dx, float dy) { tx.translate(dx, dy); }

    public void postScale(float sx, float sy) { tx.scale(sx, sy); }

    public void postRotate(float degrees) { tx.rotate(Math.toRadians(degrees)); }

    public AffineTransform awt() { return tx; }
}
