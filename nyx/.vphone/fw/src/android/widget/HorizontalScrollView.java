package android.widget;

import android.content.Context;
import android.view.View;

/** HorizontalScrollView: single row, horizontally scrollable. */
public class HorizontalScrollView extends FrameLayout {

    public HorizontalScrollView(Context context) { super(context); }

    public void setFillViewport(boolean fill) {}

    public void setHorizontalScrollBarEnabled(boolean enabled) {}

    public void setOverScrollMode(int mode) {}

    public void setSmoothScrollingEnabled(boolean enabled) {}

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int totalWidth = 0;
        int maxHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            totalWidth += child.getMeasuredWidth();
            maxHeight = Math.max(maxHeight, child.getMeasuredHeight());
        }
        int wMode = MeasureSpec.getMode(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int contentWidth = totalWidth + getPaddingLeft() + getPaddingRight();
        int contentHeight = maxHeight + getPaddingTop() + getPaddingBottom();

        // NOTE: inside a vertically scrolling LinearLayout the parent hands us an
        // UNSPECIFIED height spec, i.e. getSize() == 0. Clamping against that made
        // the whole strip 0px tall (the accent swatches vanished). Only a real
        // constraint (AT_MOST / EXACTLY with a positive size) may clamp us.
        int width;
        if (wMode == MeasureSpec.UNSPECIFIED || widthSize <= 0) width = contentWidth;
        else if (wMode == MeasureSpec.EXACTLY) width = widthSize;
        else width = Math.max(Math.min(widthSize, contentWidth), 0);

        int height;
        if (hMode == MeasureSpec.UNSPECIFIED || heightSize <= 0) height = contentHeight;
        else if (hMode == MeasureSpec.EXACTLY) height = Math.max(heightSize, contentHeight);
        else height = Math.max(Math.min(heightSize, contentHeight), 0);

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int cursor = getPaddingLeft();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.layout(cursor, 0, cursor + child.getMeasuredWidth(), child.getMeasuredHeight());
            cursor += child.getMeasuredWidth();
        }
    }
}
