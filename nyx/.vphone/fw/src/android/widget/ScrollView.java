package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/** ScrollView with real vertical scrolling and nested-host measurement. */
public class ScrollView extends FrameLayout {

    private boolean fillViewport;
    private int scrollRange;

    public ScrollView(Context context) {
        super(context);
        setVerticalScrollBarEnabled(false);
    }

    public void setFillViewport(boolean fill) { fillViewport = fill; }

    public void setVerticalScrollBarEnabled(boolean enabled) {}

    public void setHorizontalScrollBarEnabled(boolean enabled) {}

    public void setSmoothScrollingEnabled(boolean enabled) {}

    public void setOverScrollMode(int mode) {}

    public int getMaxScrollAmount2() { return Math.max(0, scrollRange); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int childWidth = widthMode == MeasureSpec.UNSPECIFIED ? 0 : widthSize
                - getPaddingLeft() - getPaddingRight();

        int totalHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            // AOSP measures the scrolling child with an EXACTLY width (when we know
            // our own width) and an UNSPECIFIED height. AT_MOST width here is wrong:
            // MATCH_PARENT rows then collapse to their content width.
            int childWidthSpec = widthMode == MeasureSpec.UNSPECIFIED
                    ? MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    : MeasureSpec.makeMeasureSpec(Math.max(0, childWidth), MeasureSpec.EXACTLY);
            child.measure(childWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            totalHeight += child.getMeasuredHeight();
        }
        int childWidthFinal = 0;
        for (int i = 0; i < getChildCount(); i++) {
            childWidthFinal = Math.max(childWidthFinal, getChildAt(i).getMeasuredWidth());
        }
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int height = heightMode == MeasureSpec.EXACTLY ? heightSize
                : Math.min(totalHeight + getPaddingTop() + getPaddingBottom(),
                MeasureSpec.getSize(heightMeasureSpec));
        int widthOut = widthMode == MeasureSpec.EXACTLY ? widthSize
                : Math.min(childWidthFinal + getPaddingLeft() + getPaddingRight(),
                MeasureSpec.getSize(widthMeasureSpec));
        setMeasuredDimension(widthOut, height);
    }

    @Override
    public void layout(int l, int t, int r, int b) {
        super.layout(l, t, r, b);
        // FrameLayout only calls onLayout when the frame changed; a ScrollView must
        // re-run its own layout whenever it measures differently.
        onLayout(true, l, t, r, b);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childTop = getPaddingTop() - ((int) scrollY);
        int innerWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int childHeight = child.getMeasuredHeight();
            child.layout(getPaddingLeft(), childTop, getPaddingLeft() + innerWidth,
                    childTop + childHeight);
            childTop += childHeight;
        }
        int contentHeight = 0;
        for (int i = 0; i < getChildCount(); i++) contentHeight += getChildAt(i).getMeasuredHeight();
        scrollRange = Math.max(0, contentHeight + getPaddingTop() + getPaddingBottom() - getHeight());
        if (scrollY > scrollRange) {
            scrollY = scrollRange;
            onLayout(changed, l, t, r, b);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) { return super.dispatchTouchEvent(event); }

    public void scrollTo(int x, int y) {
        int clamped = Math.max(0, Math.min(scrollRange, y));
        if (clamped != (int) scrollY) {
            scrollY = clamped;
            requestLayout();
            invalidate();
        }
    }

    public void smoothScrollTo(int x, int y) { scrollTo(x, y); }

    public boolean fullScroll(int direction) { return false; }

    public boolean pageScroll(int direction) { return false; }

    public void setScrollY(int y) { scrollTo(0, y); }
}
