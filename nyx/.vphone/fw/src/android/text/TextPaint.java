package android.text;

import android.graphics.Paint;

/** Paint specialisation for text. */
public class TextPaint extends Paint {
    public TextPaint() { super(); }
    public TextPaint(int flags) { super(flags); }
    public TextPaint(Paint p) { super(p); }
    public int bgColor;
    public int baselineShift;
    public int linkColor;
}
