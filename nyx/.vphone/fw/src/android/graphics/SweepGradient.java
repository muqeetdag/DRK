package android.graphics;

/** Top-level SweepGradient. */
public class SweepGradient extends Shader.SweepGradient {
    public SweepGradient(float cx, float cy, int[] colors, float[] positions) {
        super(cx, cy, colors, positions);
    }

    public SweepGradient(float cx, float cy, int color0, int color1) {
        super(cx, cy, color0, color1);
    }
}
