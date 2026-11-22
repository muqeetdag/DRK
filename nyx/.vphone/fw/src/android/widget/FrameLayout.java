package android.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

/** FrameLayout: stacked children, gravity + margins. */
public class FrameLayout extends ViewGroup {

    private int foregroundGravity = Gravity.FILL;
    private boolean traceNext;

    public FrameLayout(Context context) { super(context); }

    public FrameLayout(Context context, android.util.AttributeSet attrs) { this(context); }

    public void setForegroundGravity(int gravity) {}

    public void setMeasureAllChildren(boolean measure) {}

    public void setForeground(android.graphics.drawable.Drawable drawable) {}

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int maxWidth = 0;
        int maxHeight = 0;
        int matchParentCount = 0;
        boolean trace = System.getProperty("vphone.traceFrame") != null;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            if (trace) {
                System.out.println("[frame] " + child.getClass().getSimpleName()
                        + " measured=" + child.getMeasuredWidth() + "x" + child.getMeasuredHeight()
                        + " lp=" + child.getLayoutParams().width + "x" + child.getLayoutParams().height
                        + " parentSpec=" + MeasureSpec.toString(widthMeasureSpec)
                        + "/" + MeasureSpec.toString(heightMeasureSpec));
            }
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp.width == LayoutParams.MATCH_PARENT) matchParentCount++;
            maxWidth = Math.max(maxWidth, child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
            maxHeight = Math.max(maxHeight, child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
        }

        int width = widthMode == MeasureSpec.EXACTLY ? widthSize
                : Math.max(maxWidth + getPaddingLeft() + getPaddingRight(), getSuggestedMinimumWidth());
        if (widthMode == MeasureSpec.AT_MOST) width = Math.min(width, widthSize);
        int height = heightMode == MeasureSpec.EXACTLY ? heightSize
                : Math.max(maxHeight + getPaddingTop() + getPaddingBottom(), getSuggestedMinimumHeight());
        if (heightMode == MeasureSpec.AT_MOST) height = Math.min(height, heightSize);
        setMeasuredDimension(width, height);
    }

    @Override
    public void layout(int l, int t, int r, int b) {
        super.layout(l, t, r, b);
        // Containers that host dynamic children must re-run their own layout even when
        // their frame is unchanged (Android does this from the traversal's layout pass).
        onLayout(true, l, t, r, b);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int innerLeft = getPaddingLeft();
        int innerTop = getPaddingTop();
        int innerRight = getWidth() - getPaddingRight();
        int innerBottom = getHeight() - getPaddingBottom();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int cw = lp.width == LayoutParams.MATCH_PARENT ? (innerRight - innerLeft) - lp.leftMargin - lp.rightMargin
                    : child.getMeasuredWidth();
            int ch = lp.height == LayoutParams.MATCH_PARENT ? innerBottom - innerTop : child.getMeasuredHeight();
            int gravity = lp.gravity != 0 ? lp.gravity : Gravity.TOP | Gravity.START;
            int h = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
            int v = gravity & Gravity.VERTICAL_GRAVITY_MASK;
            int left = innerLeft + lp.leftMargin;
            int top = innerTop + lp.topMargin;
            if (h == Gravity.CENTER_HORIZONTAL) left = innerLeft + (innerRight - innerLeft - cw) / 2;
            else if (h == Gravity.RIGHT) left = innerRight - cw - lp.rightMargin;
            if (v == Gravity.CENTER_VERTICAL) top = innerTop + (innerBottom - innerTop - ch) / 2;
            else if (v == Gravity.BOTTOM) top = innerBottom - ch - lp.bottomMargin;
            if (System.getProperty("vphone.trace") != null) {
                if (System.getProperty("vphone.trace") != null) System.out.println("[fl-layout] parent=" + getWidth() + "x" + getHeight()
                        + " child=" + child.getClass().getSimpleName()
                        + " measured=" + child.getMeasuredWidth() + "x" + child.getMeasuredHeight()
                        + " -> " + left + "," + top + " " + cw + "x" + ch
                        + " lp=" + lp.width + "x" + lp.height);
            }
            child.layout(left, top, left + cw, top + ch);
        }
    }

    @Override
    protected boolean checkLayoutParamsType(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    public LayoutParams generateLayoutParams(android.view.View.LayoutParams p) {
        return new LayoutParams(p.width, p.height);
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public LayoutParams(Context c, android.util.AttributeSet attrs) { super(c, attrs); }
        public LayoutParams(int width, int height) { super(width, height); }

        public LayoutParams(android.view.View.LayoutParams source) { super(source); }
    }
}
