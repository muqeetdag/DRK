package android.graphics;

/** Top-level LinearGradient. */
public class LinearGradient extends Shader.LinearGradient {
    public LinearGradient(float x0, float y0, float x1, float y1, int[] colors, float[] stops,
                          Shader.TileMode tile) {
        super(x0, y0, x1, y1, colors, stops, tile);
    }

    public LinearGradient(float x0, float y0, float x1, float y1, int color0, int color1,
                          Shader.TileMode tile) {
        super(x0, y0, x1, y1, color0, color1, tile);
    }
}
