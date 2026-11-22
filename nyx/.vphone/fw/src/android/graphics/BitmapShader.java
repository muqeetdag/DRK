package android.graphics;

/** Top-level BitmapShader. */
public class BitmapShader extends Shader.BitmapShader {
    public BitmapShader(Bitmap bitmap, Shader.TileMode tileX, Shader.TileMode tileY) {
        super(bitmap, tileX, tileY);
    }
}
