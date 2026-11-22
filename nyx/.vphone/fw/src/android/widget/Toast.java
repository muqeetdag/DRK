package android.widget;

import android.content.Context;
import android.view.View;

/** Toast that records its text for the harness. */
public class Toast {

    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    private final String text;
    private final int duration;

    private Toast(String text, int duration) {
        this.text = text;
        this.duration = duration;
    }

    public static Toast makeText(Context context, CharSequence text, int duration) {
        return new Toast(text == null ? "" : text.toString(), duration);
    }

    public static Toast makeText(Context context, int resId, int duration) {
        return new Toast(context.getString(resId), duration);
    }

    public void show() { android.test.Services.toast(this, text); }

    public void cancel() {}

    private View customView;

    public void setView(View view) { customView = view; }

    public View getView() { return customView; }

    public void setGravity(int gravity, int xOffset, int yOffset) {}

    public void setDuration(int duration) {}

    public String text() { return text; }

    public int duration() { return duration; }
}
