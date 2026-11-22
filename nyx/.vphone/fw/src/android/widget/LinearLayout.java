package android.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

/** LinearLayout: horizontal or vertical, weights, gravity, margins. */
public class LinearLayout extends ViewGroup {

    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;
    public static final int SHOW_DIVIDER_NONE = 0;

    private int orientation;
    private int gravity = Gravity.START | Gravity.TOP;
    private float weightSum = -1;

    public LinearLayout(Context context) { super(context); }

    public LinearLayout(Context context, android.util.AttributeSet attrs) { this(context); }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
        requestLayout();
    }

    public int getOrientation() { return orientation; }

    public void setGravity(int gravity) { this.gravity = gravity; }

    public int getGravity() { return gravity; }

    public void setHorizontalGravity(int gravity) {}

    public void setVerticalGravity(int gravity) {}

    public void setWeightSum(float sum) { weightSum = sum; }

    public void setShowDividers(int showDividers) {}

    public void setDividerDrawable(android.graphics.drawable.Drawable divider) {}

    public void setDividerPadding(int padding) {}

    public boolean isBaselineAligned() { return false; }

    public void setBaselineAligned(boolean aligned) {}

    private boolean vertical() { return orientation == VERTICAL; }

    /** Parent inner width available to a child, independent of the incoming spec. */
    private int innerRightBound() {
        return Math.max(0, getMeasuredWidthValue() - getPaddingLeft() - getPaddingRight());
    }

    private int getMeasuredWidthValue() {
        // Use the size we are being measured with; fall back to the last measured
        // width only when this pass has no width information at all.
        if (innerWidthGuess > 0) return innerWidthGuess;
        return Math.max(getMeasuredWidth2(), 0);
    }

    private int getMeasuredWidth2() {
        return super.getMeasuredWidth();
    }

    private int innerWidthGuess;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int totalLength = 0;
        int maxCross = 0;
        float totalWeight = 0;
        boolean hasWeight = false;

        innerWidthGuess = widthSize > 0 ? widthSize : innerWidthGuess;
        int innerWidthForChildren = widthMode == MeasureSpec.UNSPECIFIED
                ? Math.max(0, widthSize - getPaddingLeft() - getPaddingRight())
                : Math.max(0, widthSize - getPaddingLeft() - getPaddingRight());
        // Children must be measured against the parent's inner width. Using the raw
        // spec keeps `widthUsed` = padding, which is right for EXACTLY/AT_MOST.
        int childParentWidthSpec = widthMeasureSpec == 0
                ? MeasureSpec.makeMeasureSpec(innerWidthForChildren, MeasureSpec.AT_MOST)
                : widthMeasureSpec;

        if (System.getProperty("vphone.trace") != null) {
            System.out.println("[ll] " + (vertical() ? "V" : "H") + " wSpec="
                    + MeasureSpec.toString(widthMeasureSpec) + " hSpec="
                    + MeasureSpec.toString(heightMeasureSpec) + " children=" + getChildCount());
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                System.out.println("     #" + i + " " + c.getClass().getSimpleName()
                        + " lp=" + c.getLayoutParams().width + "x" + c.getLayoutParams().height
                        + " weight=" + LinearLayout.weightOf(c));
            }
        }
        // Pass 1: measure children without weights.
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp.weight > 0) {
                hasWeight = true;
                // A weighted child still owns its margins inside the parent's span;
                // Android folds them into mTotalLength before distributing `delta`.
                totalLength += vertical() ? lp.topMargin + lp.bottomMargin
                        : lp.leftMargin + lp.rightMargin;
                continue;
            }
            if (vertical()) {
                measureChild(child, childParentWidthSpec, heightMeasureSpec);
                totalLength += child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
                maxCross = Math.max(maxCross, child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
            } else {
                measureChild(child, widthMeasureSpec, heightMeasureSpec);
                totalLength += child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
                maxCross = Math.max(maxCross, child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
            }
        }

        // Pass 2: distribute remaining space to weighted children.
        int available = vertical() ? heightSize : widthSize;
        int remaining = Math.max(0, available - totalLength - (vertical()
                ? getPaddingTop() + getPaddingBottom() : getPaddingLeft() + getPaddingRight()));
        if (hasWeight) {
            float sum = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                sum += ((LayoutParams) child.getLayoutParams()).weight;
            }
            if (weightSum > 0) sum = weightSum;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (lp.weight <= 0) continue;
                int share = (int) (remaining * (lp.weight / Math.max(0.0001f, sum)));
                // `share` is the slot, margins live inside it — the child gets the rest.
                int slot = vertical() ? share - (lp.topMargin + lp.bottomMargin)
                        : share - (lp.leftMargin + lp.rightMargin);
                share = Math.max(0, slot);
                if (vertical()) {
                    // Width: full inner width. Height: the weighted share.
                    int wSpec = getChildMeasureSpec(childParentWidthSpec,
                            getPaddingLeft() + getPaddingRight(), lp.width);
                    child.measure(wSpec,
                            MeasureSpec.makeMeasureSpec(Math.max(0, share), MeasureSpec.EXACTLY));
                    totalLength += share + lp.topMargin + lp.bottomMargin;
                    maxCross = Math.max(maxCross, child.getMeasuredWidth());
                } else {
                    // Height keeps its own spec (WRAP_CONTENT stays AT_MOST, not EXACTLY);
                    // width is the weighted share.
                    int hSpec = getChildMeasureSpec(heightMeasureSpec,
                            getPaddingTop() + getPaddingBottom(), lp.height);
                    child.measure(MeasureSpec.makeMeasureSpec(Math.max(0, share), MeasureSpec.EXACTLY),
                            hSpec);
                    totalLength += share + lp.leftMargin + lp.rightMargin;
                    maxCross = Math.max(maxCross, child.getMeasuredHeight());
                }
            }
        }

        int width;
        int height;
        if (vertical()) {
            width = widthMode == MeasureSpec.EXACTLY ? widthSize
                    : maxCross + getPaddingLeft() + getPaddingRight();
            if (widthMode == MeasureSpec.AT_MOST) width = Math.min(width, widthSize);
            height = heightMode == MeasureSpec.EXACTLY ? heightSize
                    : totalLength + getPaddingTop() + getPaddingBottom();
            if (heightMode == MeasureSpec.AT_MOST) height = Math.min(height, heightSize);
        } else {
            height = heightMode == MeasureSpec.EXACTLY ? heightSize
                    : maxCross + getPaddingTop() + getPaddingBottom();
            if (heightMode == MeasureSpec.AT_MOST) height = Math.min(height, heightSize);
            width = widthMode == MeasureSpec.EXACTLY ? widthSize
                    : totalLength + getPaddingLeft() + getPaddingRight();
            if (widthMode == MeasureSpec.AT_MOST) width = Math.min(width, widthSize);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int innerLeft = getPaddingLeft();
        int innerTop = getPaddingTop();
        int innerRight = getWidth() - getPaddingRight();
        int innerBottom = getHeight() - getPaddingBottom();

        int cursor = vertical() ? innerTop : innerLeft;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();

            if (vertical()) {
                int childLeft = innerLeft + lp.leftMargin;
                int horizontal = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                if (lp.gravity != 0) horizontal = lp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                int space = innerRight - innerLeft - cw - lp.leftMargin - lp.rightMargin;
                if (horizontal == Gravity.CENTER_HORIZONTAL) childLeft = innerLeft + space / 2 + lp.leftMargin;
                else if (horizontal == Gravity.RIGHT || horizontal == Gravity.END) childLeft = innerRight - cw - lp.rightMargin;
                else if (lp.width == LayoutParams.MATCH_PARENT || lp.weight > 0) childLeft = innerLeft;
                int childTop = cursor + lp.topMargin;
                child.layout(childLeft, childTop, childLeft + cw, childTop + ch);
                cursor = childTop + ch + lp.bottomMargin;
            } else {
                int childTop = innerTop + lp.topMargin;
                int verticalGravity = lp.gravity != 0 ? lp.gravity & Gravity.VERTICAL_GRAVITY_MASK
                        : gravity & Gravity.VERTICAL_GRAVITY_MASK;
                int space = innerBottom - innerTop - ch - lp.topMargin - lp.bottomMargin;
                if (verticalGravity == Gravity.CENTER_VERTICAL) childTop = innerTop + space / 2 + lp.topMargin;
                else if (verticalGravity == Gravity.BOTTOM) childTop = innerBottom - ch - lp.bottomMargin;
                int childLeft = cursor + lp.leftMargin;
                child.layout(childLeft, childTop, childLeft + cw, childTop + ch);
                cursor = childLeft + cw + lp.rightMargin;
            }
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
        public float weight;

        public LayoutParams(Context c, android.util.AttributeSet attrs) { super(c, attrs); }

        public LayoutParams(int width, int height) { super(width, height); }

        public LayoutParams(android.view.View.LayoutParams source) { super(source); }

        public LayoutParams(int width, int height, float weight) {
            super(width, height);
            this.weight = weight;
        }

    }

    /** Static-weight layout helper used through LayoutParams. */
    static float weightOf(View child) {
        if (child.getLayoutParams() instanceof LayoutParams) {
            return ((LayoutParams) child.getLayoutParams()).weight;
        }
        return 0f;
    }
}
