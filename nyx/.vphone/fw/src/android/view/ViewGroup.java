package android.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

/** ViewGroup with z-ordered children, touch dispatch and layout parameters. */
public abstract class ViewGroup extends View implements ViewParent {

    public static final int FOCUS_BEFORE_DESCENDANTS = 0x20000;
    public static final int FOCUS_AFTER_DESCENDANTS = 0x40000;
    public static final int CLIP_TO_PADDING_MASK = 34;

    private final List<View> children = new ArrayList<>();
    private boolean layoutRequested;
    private View focused;
    private int descendantFocusability;
    private boolean motionTarget;
    private View touchTarget;

    public ViewGroup(Context context) { super(context); }

    public ViewGroup(Context context, android.util.AttributeSet attrs) { super(context); }

    // ------------------------------------------------------------- children
    public void addView(View child) {
        // Android keeps params the child already carries; only children without
        // any get the container default.
        LayoutParams params = child.getLayoutParams() != null
                ? child.getLayoutParams() : defaultParams();
        addView(child, -1, params);
    }

    public void addView(View child, int width, int height) {
        LayoutParams lp = child.getLayoutParams();
        lp.width = width;
        lp.height = height;
        addView(child, lp);
    }

    public void addView(View child, LayoutParams params) { addView(child, -1, params); }

    public void addView(View child, int index) { addView(child, index, defaultParams()); }

    public void addView(View child, int index, LayoutParams params) {
        if (child == null) throw new IllegalArgumentException("Cannot add a null child view to a ViewGroup");
        if (child.getParent() != null && child.getParent() != this) {
            ((ViewGroup) child.getParent()).removeView(child);
        }
        child.setLayoutParams(checkLayoutParams(params));
        child.assignParent(this);
        if (index < 0 || index > children.size()) children.add(child);
        else children.add(index, child);
        child.onAttachedToWindow();
        requestLayout();
        invalidate();
    }

    public void removeView(View child) {
        if (child == null) return;
        if (children.remove(child)) {
            child.onDetachedFromWindow();
            requestLayout();
            invalidate();
        }
    }

    public void removeViewAt(int index) {
        if (index >= 0 && index < children.size()) removeView(children.get(index));
    }

    public void removeViewInLayout(View child) { removeView(child); }

    public void removeAllViews() {
        for (View child : new ArrayList<>(children)) child.onDetachedFromWindow();
        children.clear();
        requestLayout();
        invalidate();
    }

    public void removeAllViewsInLayout() { removeAllViews(); }

    public void bringChildToFront(View child) {
        if (children.remove(child)) children.add(child);
    }

    public int getChildCount() { return children.size(); }

    public View getChildAt(int index) {
        if (index < 0 || index >= children.size()) return null;
        return children.get(index);
    }

    public int indexOfChild(View child) { return children.indexOf(child); }

    public void addViewInLayout(View child, int index, LayoutParams params) {
        addView(child, index, params);
    }

    private LayoutParams defaultParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    // ------------------------------------------------------------ lifecycle
    @Override
    public void requestLayout() {
        layoutRequested = true;
        super.requestLayout();
    }

    @Override
    public boolean isLayoutRequested() { return layoutRequested; }

    @Override
    public void invalidateChild(View child, Rect dirty) { invalidate(); }

    /** Converts generic params into this container's params class (Android semantics). */
    protected LayoutParams checkLayoutParams(LayoutParams p) {
        if (p == null) return generateDefaultLayoutParams();
        if (checkLayoutParamsType(p)) return p;
        return generateLayoutParams(p);
    }

    /** Overridden by each container to test its own params class. */
    protected boolean checkLayoutParamsType(LayoutParams p) { return true; }

    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    public LayoutParams generateLayoutParams(LayoutParams p) {
        return new LayoutParams(p.width, p.height);
    }

    public void setDescendantFocusability(int focusability) {
        descendantFocusability = focusability;
    }

    public int getDescendantFocusability() { return descendantFocusability; }

    // ---------------------------------------------------------------- layout
    public final void measureChildren(int widthMeasureSpec, int heightMeasureSpec) {
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
        }
    }

    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        LayoutParams lp = child.getLayoutParams();
        int childWidthSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight(), lp.width);
        int childHeightSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                getPaddingTop() + getPaddingBottom(), lp.height);
        child.measure(childWidthSpec, childHeightSpec);
    }

    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
                                           int parentHeightMeasureSpec, int heightUsed) {
        measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec);
    }

    public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
        int specMode = MeasureSpec.getMode(spec);
        int specSize = MeasureSpec.getSize(spec);
        int size = Math.max(0, specSize - padding);
        int resultSize;
        int resultMode;
        switch (specMode) {
            // Parent has imposed an exact size on us.
            case MeasureSpec.EXACTLY:
                if (childDimension >= 0) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    resultSize = size;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                } else {
                    resultSize = 0;
                    resultMode = MeasureSpec.UNSPECIFIED;
                }
                break;
            // Parent has imposed a maximum size on us.
            case MeasureSpec.AT_MOST:
                if (childDimension >= 0) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                } else {
                    resultSize = 0;
                    resultMode = MeasureSpec.UNSPECIFIED;
                }
                break;
            // Parent asked to see how big we want to be.
            case MeasureSpec.UNSPECIFIED:
            default:
                if (childDimension >= 0) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    resultSize = 0;
                    resultMode = MeasureSpec.UNSPECIFIED;
                } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                    resultSize = 0;
                    resultMode = MeasureSpec.UNSPECIFIED;
                } else {
                    resultSize = 0;
                    resultMode = MeasureSpec.UNSPECIFIED;
                }
                break;
        }
        return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            if (child.getVisibility() != VISIBLE) continue;
            // Android draws a zero-sized child too; the view's own guards handle it.
            child.draw(canvas);
        }
    }

    // ------------------------------------------------------------------ touch
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) touchTarget = null;
        if (touchTarget == null && action == MotionEvent.ACTION_DOWN) {
            touchTarget = findTarget(this, event.getX(), event.getY());
        }
        if (touchTarget != null) {
            MotionEvent local = event.offset(event.getX() - touchTarget.getLeft(),
                    event.getY() - touchTarget.getTop());
            boolean handled = touchTarget.dispatchTouchEvent(local);
            if (handled) return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touchTarget = null;
        }
        return super.dispatchTouchEvent(event);
    }

    private View findTarget(ViewGroup group, float x, float y) {
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != VISIBLE) continue;
            float lx = x - child.getLeft();
            float ly = y - child.getTop();
            if (lx >= 0 && ly >= 0 && lx < child.getWidth() && ly < child.getHeight()) {
                if (child instanceof ViewGroup) {
                    View nested = findTarget((ViewGroup) child, lx, ly);
                    if (nested != null) return nested;
                }
                return child;
            }
        }
        return null;
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) { return false; }

    public void requestDisallowInterceptTouchEvent(boolean disallow) {}

    public View getFocusedChild() { return focused; }

    public void setClipChildren(boolean clip) {}

    public void setClipToPadding(boolean clip) {}

    public void setLayoutTransition(Object transition) {}

    public void setLayoutMode(int mode) {}

    public void offsetDescendantRectToMyCoords(View descendant, Rect rect) {}

    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin, topMargin, rightMargin, bottomMargin;
        public int marginStart, marginEnd;
        /** FrameLayout (and others) express alignment here. */
        public int gravity;

        public MarginLayoutParams(Context c, android.util.AttributeSet attrs) { super(c, attrs); }

        public MarginLayoutParams(int width, int height) { super(width, height); }

        public MarginLayoutParams(LayoutParams source) { super(source); }

        @Override
        public void setMargins(int left, int top, int right, int bottom) {
            leftMargin = left;
            topMargin = top;
            rightMargin = right;
            bottomMargin = bottom;
        }

        @Override public void setMarginStart(int start) { marginStart = start; leftMargin = start; }

        @Override public void setMarginEnd(int end) { marginEnd = end; rightMargin = end; }

        public int getMarginStart() { return marginStart != 0 ? marginStart : leftMargin; }

        public int getMarginEnd() { return marginEnd != 0 ? marginEnd : rightMargin; }
    }

    @Override public String toString() {
        return getClass().getSimpleName() + "{children=" + children.size() + "}";
    }
}
