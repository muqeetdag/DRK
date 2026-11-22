package android.app;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

/** Dialog window hosting a content view. */
public class Dialog {

    private final Context context;
    private final int themeResId;
    private final Window window;
    private final FrameLayout content;
    private boolean showing;
    private boolean cancelable = true;
    private boolean canceledOnTouchOutside = true;
    private OnDismissListener onDismissListener;
    private OnCancelListener onCancelListener;
    private View contentView;
    private CharSequence title;

    public Dialog(Context context) { this(context, 0); }

    public Dialog(Context context, int themeResId) {
        this.context = context;
        this.themeResId = themeResId;
        this.window = new Window(context);
        this.content = new FrameLayout(context);
        boolean translucent = themeResId != 0;
        window.setLayout(translucent ? WindowManager.LayoutParams.WRAP_CONTENT
                : WindowManager.LayoutParams.MATCH_PARENT, translucent
                ? WindowManager.LayoutParams.WRAP_CONTENT
                : WindowManager.LayoutParams.MATCH_PARENT);
        window.attachContent(content);
    }

    public Context getContext() { return context; }

    public Window getWindow() { return window; }

    public View getDecorView() { return content; }

    public void setContentView(View view) {
        contentView = view;
        content.removeAllViews();
        content.addView(view, new FrameLayout.LayoutParams(-1, -2));
    }

    public void setContentView(View view, ViewGroup.LayoutParams params) {
        contentView = view;
        content.removeAllViews();
        content.addView(view, params);
    }

    public void setContentView(int layoutResId) {
        throw new UnsupportedOperationException("layout resources are not used by this app");
    }

    public View findViewById(int id) {
        if (contentView == null) return null;
        if (contentView.getId() == id) return contentView;
        return contentView.findViewById(id);
    }

    public void setTitle(CharSequence title) { this.title = title; }

    public void setTitle(int resId) { setTitle(context.getString(resId)); }

    public CharSequence getTitle() { return title; }

    public void show() {
        showing = true;
        android.test.Services.showDialog(this);
    }

    public void dismiss() {
        if (!showing) return;
        showing = false;
        android.test.Services.dismissDialog(this);
        if (onDismissListener != null) onDismissListener.onDismiss(this);
    }

    public void cancel() {
        if (onCancelListener != null) onCancelListener.onCancel(this);
        dismiss();
    }

    public boolean isShowing() { return showing; }

    public void setCancelable(boolean flag) { cancelable = flag; }

    public void setCanceledOnTouchOutside(boolean cancel) { canceledOnTouchOutside = cancel; }

    public void setOnDismissListener(OnDismissListener listener) { onDismissListener = listener; }

    public void setOnCancelListener(OnCancelListener listener) { onCancelListener = listener; }

    public void setOnShowListener(Object listener) {}

    public void hide() { showing = false; }

    public void setFeatureDrawableResource(int featureId, int resId) {}

    public Bundle onSaveInstanceState() { return new Bundle(); }

    public void onBackPressed() { cancel(); }

    public interface OnDismissListener { void onDismiss(Dialog dialog); }

    public interface OnCancelListener { void onCancel(Dialog dialog); }
}
