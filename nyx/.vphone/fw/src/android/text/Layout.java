package android.text;

import android.graphics.Canvas;

/** Layout stub, only used for enums the app references. */
public class Layout {
    public enum Alignment { ALIGN_NORMAL, ALIGN_CENTER, ALIGN_OPPOSITE }
    public enum TextDirectionHeuristic { LTR, RTL, FIRSTSTRONG_LTR, FIRSTSTRONG_RTL }
    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public int getLineCount() { return 1; }
    public float getLineWidth(int line) { return 0; }
    public Layout(CharSequence text, TextPaint paint, int width, Alignment align,
                  float spacingMult, float spacingAdd) {}
    public void draw(Canvas canvas) {}
}
