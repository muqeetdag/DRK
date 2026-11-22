package android.graphics;

/** Top-level RadialGradient (Android's real location); backed by Shader.RadialGradient. */
public class RadialGradient extends Shader.RadialGradient {
    public RadialGradient(float cx, float cy, float radius, int[] colors, float[] stops,
                          Shader.TileMode tile) {
        super(cx, cy, radius, colors, stops, tile);
    }

    public RadialGradient(float cx, float cy, float radius, int color0, int color1, Shader.TileMode tile) {
        super(cx, cy, radius, color0, color1, tile);
    }
}
