package android.view;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import java.util.ArrayList;
import java.util.List;

/**
 * Core View with a real measure/layout/draw pipeline.
 *
 * Deliberately faithful where it matters for crash-hunting: onDraw only runs
 * on a view with positive bounds (like Android, where a 0x0 view can still be
 * drawn — the app's gradient guards matter), and the same method order is used.
 */
public class View {

    // ---------------------------------------------------------------- measure
    public static final int MEASURED_SIZE_MASK = 0x00FFFFFF;
    public static final int MEASURED_STATE_MASK = 0xFF000000;
    public static final int MEASURED_STATE_TOO_SMALL = 0x01000000;

    // ----------------------------------------------------------------- gravity
    public static final int NO_ID = -1;
    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;
    public static final int TEXT_ALIGNMENT_INHERIT = 0;
    public static final int TEXT_ALIGNMENT_GRAVITY = 1;
    public static final int TEXT_ALIGNMENT_CENTER = 2;
    public static final int TEXT_ALIGNMENT_TEXT_START = 3;
    public static final int TEXT_ALIGNMENT_TEXT_END = 4;
    public static final int TEXT_ALIGNMENT_VIEW_START = 5;
    public static final int TEXT_ALIGNMENT_VIEW_END = 6;

    public static final int OVER_SCROLL_NEVER = 2;

    protected Context mContext;
    private ViewParent parent;
    int left, top, right, bottom;
    int measuredWidth, measuredHeight;
    private int visibility = VISIBLE;
    private int id = NO_ID;
    private Object tag;
    private boolean clickable;
    private boolean enabled = true;
    private boolean pressed;
    private boolean focused;
    private boolean selected;
    private OnClickListener onClickListener;
    private OnLongClickListener onLongClickListener;
    private OnTouchListener onTouchListener;
    private OnScrollChangeListener onScrollChangeListener;
    private int backgroundColorInt = 0;
    private Drawable background;
    private float alpha = 1f;
    private float translationX, translationY, elevation;
    private float scaleX = 1f, scaleY = 1f, rotation;
    private int paddingLeft, paddingTop, paddingRight, paddingBottom;
    private LayoutParams layoutParams;
    private int minimumWidth, minimumHeight;
    private boolean willNotDraw;
    private boolean attached;
    private final List<Runnable> pending = new ArrayList<>();
    private Animation animation;
    public float scrollX, scrollY;

    public View(Context context) { this.mContext = context; }

    public View(Context context, android.util.AttributeSet attrs) { this(context); }

    public Context getContext() { return mContext; }

    // -------------------------------------------------------------- hierarchy
    public ViewParent getParent() { return parent; }

    void assignParent(ViewParent parent) { this.parent = parent; }

    public View getRootView() {
        View v = this;
        while (v.parent instanceof View) v = (View) v.parent;
        return v;
    }

    public boolean isAttachedToWindow() { return attached; }

    public void onAttachedToWindow() { attached = true; }

    public void onDetachedFromWindow() { attached = false; }

    // ------------------------------------------------------------------ ids
    public void setId(int id) { this.id = id; }

    public int getId() { return id; }

    public void setTag(Object tag) { this.tag = tag; }

    public Object getTag() { return tag; }

    public View findViewWithTag(Object tag) {
        if (tag == null) return null;
        if (tag.equals(this.tag)) return this;
        if (this instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) this;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = group.getChildAt(i).findViewWithTag(tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    public View findViewById(int id) {
        if (this.id == id) return this;
        if (this instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) this;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = group.getChildAt(i).findViewById(id);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ----------------------------------------------------------- visibility
    public void setVisibility(int visibility) { this.visibility = visibility; }

    public int getVisibility() { return visibility; }

    public boolean isShown() { return visibility == VISIBLE; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isEnabled() { return enabled; }

    public final boolean isClickable() { return clickable; }

    public void setClickable(boolean clickable) { this.clickable = clickable; }

    public void setLongClickable(boolean longClickable) {}

    public boolean isPressed() { return pressed; }

    public void setPressed(boolean pressed) {
        if (this.pressed == pressed) return;
        this.pressed = pressed;
        if (background != null) background.setState(pressed
                ? new int[]{android.R.attr.state_pressed} : new int[0]);
        invalidate();
    }

    public void setSelected(boolean selected) { this.selected = selected; }

    public boolean isSelected() { return selected; }

    public void setFocusable(boolean focusable) {}

    public void setFocusableInTouchMode(boolean focusable) {}

    public boolean requestFocus() { focused = true; return true; }

    public boolean isFocused() { return focused; }

    public void setSoundEffectsEnabled(boolean enabled) {}

    // ------------------------------------------------------------- appearance
    public void setBackgroundColor(int color) {
        backgroundColorInt = color;
        if (background != null && color != 0) background = null;
        invalidate();
    }

    public void setBackground(Drawable drawable) {
        this.background = drawable;
        if (drawable != null) {
            drawable.setCallback(new Drawable.Callback() {
                @Override public void invalidateDrawable(Drawable who) { invalidate(); }
                @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {
                    postDelayed(what, Math.max(0, when - System.currentTimeMillis()));
                }
                @Override public void unscheduleDrawable(Drawable who, Runnable what) {
                    removeCallbacks(what);
                }
            });
        }
    }

    public void setBackgroundDrawable(Drawable drawable) { setBackground(drawable); }

    public Drawable getBackground() { return background; }

    public void setBackgroundResource(int resId) {
        setBackground(android.content.ResBridge.drawable(resId));
    }

    public void setAlpha(float alpha) { this.alpha = alpha; }

    public float getAlpha() { return alpha; }

    public void setTranslationX(float v) { translationX = v; }

    public float getTranslationX() { return translationX; }

    public void setTranslationY(float v) { translationY = v; }

    public float getTranslationY() { return translationY; }

    public void setScaleX(float v) { scaleX = v; }

    public void setScaleY(float v) { scaleY = v; }

    public float getScaleX() { return scaleX; }

    public float getScaleY() { return scaleY; }

    public void setRotation(float v) { rotation = v; }

    public float getRotation() { return rotation; }

    public void setElevation(float v) { elevation = v; }

    public float getElevation() { return elevation; }

    public void setPadding(int l, int t, int r, int b) {
        paddingLeft = l;
        paddingTop = t;
        paddingRight = r;
        paddingBottom = b;
    }

    public void setPaddingRelative(int start, int top, int end, int bottom) {
        setPadding(start, top, end, bottom);
    }

    public int getPaddingLeft() { return paddingLeft; }

    public int getPaddingTop() { return paddingTop; }

    public int getPaddingRight() { return paddingRight; }

    public int getPaddingStart() { return paddingLeft; }

    public int getPaddingEnd() { return paddingRight; }

    public int getPaddingBottom() { return paddingBottom; }

    public void setMinimumWidth(int min) { minimumWidth = min; }

    public void setMinimumHeight(int min) { minimumHeight = min; }

    public int getMinimumWidth() { return minimumWidth; }

    public int getMinimumHeight() { return minimumHeight; }

    public void setWillNotDraw(boolean willNotDraw) { this.willNotDraw = willNotDraw; }

    public void setFitsSystemWindows(boolean fit) {}

    public void setClipToPadding(boolean clip) {}

    public void setClipChildren(boolean clip) {}

    public static final int LAYER_TYPE_NONE = 0;
    public static final int LAYER_TYPE_SOFTWARE = 1;
    public static final int LAYER_TYPE_HARDWARE = 2;

    public void setLayerType(int layerType, android.graphics.Paint paint) {}

    public int getLayerType() { return LAYER_TYPE_NONE; }

    public android.content.res.Resources getResources() {
        return mContext == null ? null : mContext.getResources();
    }

    public void setContentDescription(CharSequence description) { tag = tag == null ? description : tag; }

    public CharSequence getContentDescription() { return null; }

    public void setOnClickListener(OnClickListener listener) {
        this.onClickListener = listener;
        if (listener != null) clickable = true;
    }

    public boolean hasOnClickListeners() { return onClickListener != null; }

    public void setOnLongClickListener(OnLongClickListener listener) {
        this.onLongClickListener = listener;
        if (listener != null) clickable = true;
    }

    public void setOnTouchListener(OnTouchListener listener) { onTouchListener = listener; }

    public void setOnScrollChangeListener(OnScrollChangeListener listener) {
        this.onScrollChangeListener = listener;
    }

    public void setOnHierarchyChangeListener(Object listener) {}

    public void setOnFocusChangeListener(Object listener) {}

    public void setOnKeyListener(Object listener) {}

    public void addOnLayoutChangeListener(Object listener) {}

    public void setLayoutParams(LayoutParams params) { this.layoutParams = params; }

    public LayoutParams getLayoutParams() {
        if (layoutParams == null) layoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        return layoutParams;
    }

    public void invalidate() {
        ViewParent p = parent;
        if (p != null) p.invalidateChild(this, null);
    }

    public void postInvalidate() { invalidate(); }

    public void postInvalidateOnAnimation() { invalidate(); }

    public void requestLayout() {
        ViewParent p = parent;
        if (p != null) p.requestLayout();
    }

    public boolean post(Runnable action) { return postDelayed(action, 0); }

    public boolean postDelayed(Runnable action, long delayMillis) {
        Handler handler = Looper.getMainLooper().handler();
        handler.postDelayed(() -> {
            if (attached || parent != null || getRootView() == this) action.run();
        }, delayMillis);
        return true;
    }

    public boolean removeCallbacks(Runnable action) { return true; }

    public void postOnAnimation(Runnable action) { post(action); }

    public void postOnAnimationDelayed(Runnable action, long delay) { postDelayed(action, delay); }

    public ViewPropertyAnimator animate() { return new ViewPropertyAnimator(this); }

    public void startAnimation(Animation anim) { this.animation = anim; }

    public void clearAnimation() { animation = null; }

    public Animation getAnimation() { return animation; }

    public void setAnimation(Animation anim) { animation = anim; }

    // --------------------------------------------------------------- layout
    public final int getLeft() { return left; }

    public final int getTop() { return top; }

    public final int getRight() { return right; }

    public final int getBottom() { return bottom; }

    public final int getWidth() { return right - left; }

    public final int getHeight() { return bottom - top; }

    public final int getMeasuredWidth() { return measuredWidth; }

    public final int getMeasuredHeight() { return measuredHeight; }

    public void layout(int l, int t, int r, int b) {
        boolean changed = l != left || t != top || r != right || b != bottom;
        left = l;
        top = t;
        right = r;
        bottom = b;
        if (changed) {
            onLayout(true, l, t, r, b);
            onSizeChanged(r - l, b - t, 0, 0);
        }
        if (background != null) background.setBounds(l, t, r, b);
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {}

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {}

    public final void measure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int wMode = MeasureSpec.getMode(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (wMode == MeasureSpec.EXACTLY) measuredWidth = w;
        if (hMode == MeasureSpec.EXACTLY) measuredHeight = h;
        // Android's traversal follows measure() with layout(); dynamic containers
        // (a FrameLayout rebuilt by the console) only propagate sizes then.
        layout(left, top, right, bottom);
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int wMode = MeasureSpec.getMode(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int desiredW = getDefaultSize(minimumWidth, widthMeasureSpec);
        int desiredH = getDefaultSize(minimumHeight, heightMeasureSpec);
        measuredWidth = wMode == MeasureSpec.UNSPECIFIED ? desiredW : w;
        measuredHeight = hMode == MeasureSpec.UNSPECIFIED ? desiredH : h;
    }

    public static int getDefaultSize(int size, int measureSpec) {
        int result = size;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        if (specMode == MeasureSpec.UNSPECIFIED || specMode == MeasureSpec.AT_MOST) {
            result = size;
        } else {
            result = specSize;
        }
        return result;
    }

    protected int getSuggestedMinimumWidth() { return minimumWidth; }

    protected int getSuggestedMinimumHeight() { return minimumHeight; }

    protected void setMeasuredDimension(int measuredWidth, int measuredHeight) {
        this.measuredWidth = measuredWidth;
        this.measuredHeight = measuredHeight;
    }

    public void getHitRect(Rect outRect) {
        outRect.set(left, top, right, bottom);
    }

    public final boolean getGlobalVisibleRect(Rect r) { r.set(left, top, right, bottom); return true; }

    public void getDrawingRect(Rect outRect) {
        outRect.set(0, 0, getWidth(), getHeight());
    }

    public void getLocationOnScreen(int[] outLocation) {
        outLocation[0] = left;
        outLocation[1] = top;
    }

    public void getLocationInWindow(int[] outLocation) { getLocationOnScreen(outLocation); }

    // ---------------------------------------------------------------- drawing
    public void draw(Canvas canvas) {
        int save = canvas.save();
        if (alpha < 1f || translationX != 0f || translationY != 0f || scaleX != 1f
                || scaleY != 1f || rotation != 0f) {
            canvas.translate(left + translationX + getWidth() * 0.5f,
                    top + translationY + getHeight() * 0.5f);
            if (scaleX != 1f || scaleY != 1f) canvas.scale(scaleX, scaleY);
            if (rotation != 0f) canvas.rotate(rotation);
            canvas.translate(-getWidth() * 0.5f, -getHeight() * 0.5f);
        } else {
            canvas.translate(left, top);
        }
        // Android clips a view to its own bounds unless clipChildren is off; doing
        // the same here makes overflowing text visible in the harness instead of
        // silently bleeding into the neighbour.
        canvas.clipRect(0, 0, getWidth(), getHeight());
        if (backgroundColorInt != 0) {
            android.graphics.Paint bg = new android.graphics.Paint();
            bg.setColor(backgroundColorInt);
            canvas.drawRect(new android.graphics.RectF(0, 0, getWidth(), getHeight()), bg);
        }
        if (background != null) {
            background.setBounds(0, 0, getWidth(), getHeight());
            background.draw(canvas);
        }
        onDraw(canvas);
        dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    protected void onDraw(Canvas canvas) {}

    protected void dispatchDraw(Canvas canvas) {}

    public void drawableHotspotChanged(float x, float y) {}

    // ---------------------------------------------------------------- input
    public interface OnClickListener { void onClick(View v); }

    public interface OnLongClickListener { boolean onLongClick(View v); }

    public interface OnTouchListener { boolean onTouch(View v, MotionEvent event); }

    public interface OnScrollChangeListener {
        void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY);
    }

    public interface OnLayoutChangeListener {
        void onLayoutChange(View v, int left, int top, int right, int bottom,
                            int oldLeft, int oldTop, int oldRight, int oldBottom);
    }

    public interface OnAttachStateChangeListener {
        void onViewAttachedToWindow(View v);
        void onViewDetachedFromWindow(View v);
    }

    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!enabled) return false;
        if (onTouchListener != null && onTouchListener.onTouch(this, event)) return true;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            setPressed(true);
            return clickable;
        }
        if (action == MotionEvent.ACTION_MOVE) return clickable;
        if (action == MotionEvent.ACTION_UP) {
            boolean wasPressed = pressed;
            setPressed(false);
            if (clickable && wasPressed) {
                performClick();
                return true;
            }
            return clickable;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            setPressed(false);
            return clickable;
        }
        return false;
    }

    public boolean onTouchEvent(MotionEvent event) { return clickable; }

    public boolean performClick() {
        if (onClickListener != null) {
            onClickListener.onClick(this);
            return true;
        }
        return false;
    }

    public boolean performLongClick() {
        if (onLongClickListener != null) return onLongClickListener.onLongClick(this);
        return false;
    }

    public boolean callOnClick() { return performClick(); }

    public void getDrawingRect(android.graphics.RectF out) {}

    // ----------------------------------------------------------------- misc
    public void setSystemUiVisibility(int visibility) {}

    public void setFitsSystemWindows() {}

    public void setImportantForAccessibility(int mode) {}

    public void setAccessibilityDelegate(Object delegate) {}

    public void announceForAccessibility(CharSequence text) {}

    public void setElevation() {}

    public void setStateListAnimator(Object animator) {}

    public void setNestedScrollingEnabled(boolean enabled) {}

    public boolean canScrollVertically(int direction) { return false; }

    public boolean canScrollHorizontally(int direction) { return false; }

    @Override public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", " + left + "," + top + ", "
                + right + "," + bottom + "}";
    }

    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int FILL_PARENT = -1;
        public static final int WRAP_CONTENT = -2;
        public static final int WRAP_CONTENT_UNSPECIFIED = -2;

        public int width;
        public int height;
        public int gravity;

        public LayoutParams(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public LayoutParams(Context c, android.util.AttributeSet attrs) { this(WRAP_CONTENT, WRAP_CONTENT); }

        public LayoutParams(LayoutParams source) {
            this.width = source.width;
            this.height = source.height;
            this.gravity = source.gravity;
        }

        public void setMargins(int left, int top, int right, int bottom) { }

        public void setMarginStart(int start) {}

        public void setMarginEnd(int end) {}
    }

    public static class MeasureSpec {
        private static final int MODE_SHIFT = 30;
        private static final int MODE_MASK = 0x3 << MODE_SHIFT;
        public static final int UNSPECIFIED = 0 << MODE_SHIFT;
        public static final int EXACTLY = 1 << MODE_SHIFT;
        public static final int AT_MOST = 2 << MODE_SHIFT;

        public static int makeMeasureSpec(int size, int mode) {
            return (size & ~MODE_MASK) | (mode & MODE_MASK);
        }

        public static int getMode(int measureSpec) { return measureSpec & MODE_MASK; }

        public static int getSize(int measureSpec) { return measureSpec & ~MODE_MASK; }

        public static String toString(int measureSpec) {
            return "MeasureSpec(" + getSize(measureSpec) + ", " + getMode(measureSpec) + ")";
        }
    }
}
