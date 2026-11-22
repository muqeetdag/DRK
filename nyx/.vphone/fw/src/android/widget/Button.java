package android.widget;

import android.content.Context;

/** Plain button: a TextView that is clickable by default. */
public class Button extends TextView {
    public Button(Context context) {
        super(context);
        setClickable(true);
    }

    public Button(Context context, android.util.AttributeSet attrs) { this(context); }
}
