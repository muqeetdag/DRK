package android.widget;

import android.content.Context;
import android.text.InputType;

/** Editable text field. */
public class EditText extends TextView {

    public EditText(Context context) {
        super(context);
        setClickable(true);
        setInputType(InputType.TYPE_CLASS_TEXT);
    }

    public EditText(Context context, android.util.AttributeSet attrs) { this(context); }

    public android.text.Editable getText2() { return null; }

    public void setSelection(int index) {}

    public void selectAll() {}

    public int getSelectionStart() { return 0; }

    public int getSelectionEnd() { return 0; }
}
