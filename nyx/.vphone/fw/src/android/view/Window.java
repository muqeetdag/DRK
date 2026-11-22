package android.view;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;

/** Window: status/nav bar colours, flags and the content root. */
public class Window {

    public static final int FEATURE_NO_TITLE = 1;

    private final Context context;
    private int statusBarColor = 0xFF000000;
    private int navigationBarColor = 0xFF000000;
    private int flags;
    private int softInputMode;
    private Drawable backgroundDrawable;
    private FrameLayout decor;

    public Window(Context context) { this.context = context; }

    public void attachContent(FrameLayout root) { this.decor = root; }

    public FrameLayout getDecorView() { return decor; }

    public Context getContext() { return context; }

    public void setStatusBarColor(int color) { statusBarColor = color; }

    public int getStatusBarColor() { return statusBarColor; }

    public void setNavigationBarColor(int color) { navigationBarColor = color; }

    public int getNavigationBarColor() { return navigationBarColor; }

    public void addFlags(int flags) { this.flags |= flags; }

    public void clearFlags(int flags) { this.flags &= ~flags; }

    public void setFlags(int flags, int mask) { this.flags = (this.flags & ~mask) | (flags & mask); }

    public int getAttributesFlags() { return flags; }

    public void setSoftInputMode(int mode) { softInputMode = mode; }

    public void setBackgroundDrawable(Drawable drawable) { backgroundDrawable = drawable; }

    public Drawable getBackgroundDrawable() { return backgroundDrawable; }

    public void setBackgroundDrawableResource(int resId) {}

    public View getCurrentFocus() { return null; }

    public void setDimAmount(float amount) {}

    public void setLayout(int width, int height) {}

    public void setGravity(int gravity) {}

    public void setWindowAnimations(int resId) {}

    public void requestFeature(int featureId) {}

    public boolean requestFeature(int featureId, boolean value) { return true; }

    public void setStyle(int resId) {}

    public void setStatusBarContrastEnforced(boolean enforced) {}

    public void setNavigationBarDividerColor(int color) {}

    public void addContentView(View view, ViewGroup.LayoutParams params) {
        if (decor != null) decor.addView(view, params);
    }

    public void setTitle(CharSequence title) {}

    public View peekDecorView() { return decor; }
}
