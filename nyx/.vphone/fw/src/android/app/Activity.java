package android.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * Activity with the real lifecycle order and a content-view hierarchy that can
 * be measured, laid out, drawn and clicked.
 */
public class Activity extends Context {

    public static final int RESULT_OK = -1;
    public static final int RESULT_CANCELED = 0;
    public static final int RESULT_FIRST_USER = 1;

    private Window window;
    private FrameLayout contentRoot;
    private View contentView;
    private final WindowManager windowManager = new WindowManager();
    private Intent intent = new Intent();
    private int themeResId;
    private CharSequence title;
    private boolean finishing;
    private boolean destroyed;
    private boolean created;
    private boolean resumed;
    private int requestedOrientation = -1;
    private int resultCode = RESULT_CANCELED;
    private Intent resultIntent;
    private android.app.Application application;

    // -------------------------------------------------------------- lifecycle
    protected void onCreate(Bundle savedInstanceState) {}

    protected void onStart() {}

    protected void onRestart() {}

    protected void onResume() {}

    protected void onPause() {}

    protected void onStop() {}

    protected void onDestroy() {}

    protected void onNewIntent(Intent intent) { this.intent = intent; }

    protected void onSaveInstanceState(Bundle outState) {}

    protected void onRestoreInstanceState(Bundle savedInstanceState) {}

    protected void onPostCreate(Bundle savedInstanceState) {}

    protected void onPostResume() {}

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {}

    protected void onBackPressed() { finish(); }

    protected void onUserLeaveHint() {}

    // -------------------------------------------------------- emulator hooks
    /** Invoked by the virtual phone; keeps the exact Android call order. */
    public final void dispatchCreate(Bundle state) {
        created = true;
        onCreate(state);
        onPostCreate(state);
    }

    public final void dispatchStart() { onStart(); }

    public final void dispatchResume() {
        onResume();
        resumed = true;
        onPostResume();
    }

    public final void dispatchPause() {
        onPause();
        resumed = false;
    }

    public final void dispatchStop() { onStop(); }

    public final void dispatchDestroy() {
        destroyed = true;
        onDestroy();
    }

    public final void dispatchBackPressed() { onBackPressed(); }

    public final boolean isCreated() { return created; }

    public final boolean isResumed() { return resumed; }

    public final boolean isDestroyed() { return destroyed; }

    public final boolean isFinishing() { return finishing; }

    // ------------------------------------------------------------------ views
    @Override public android.content.res.Resources getResources() { return android.test.Boot.CONTEXT.getResources(); }

    @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
        return android.test.Boot.CONTEXT.getSharedPreferences(name, mode);
    }

    @Override public Object getSystemService(String name) { return android.test.Boot.CONTEXT.getSystemService(name); }

    @Override public java.io.File getFilesDir() { return android.test.Boot.CONTEXT.getFilesDir(); }

    @Override public java.io.File getCacheDir() { return android.test.Boot.CONTEXT.getCacheDir(); }

    @Override public android.content.pm.PackageManager getPackageManager() {
        return android.test.Boot.CONTEXT.getPackageManager();
    }

    @Override public android.content.ContentResolver getContentResolver() {
        return android.test.Boot.CONTEXT.getContentResolver();
    }

    public Window getWindow() {
        if (window == null) window = new Window(this);
        return window;
    }

    public void setContentView(int layoutResID) {
        throw new UnsupportedOperationException("layout resources are not used by this app");
    }

    public void setContentView(View view) {
        if (view == null) throw new IllegalArgumentException("content view must not be null");
        contentView = view;
        contentRoot = new FrameLayout(this);
        contentRoot.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getWindow().attachContent(contentRoot);
    }

    public void setContentView(View view, ViewGroup.LayoutParams params) {
        setContentView(view);
    }

    public void addContentView(View view, ViewGroup.LayoutParams params) {
        if (contentRoot != null) contentRoot.addView(view);
    }

    public View findViewById(int id) {
        if (contentRoot == null) return null;
        if (contentRoot.getId() == id) return contentRoot;
        return contentRoot.findViewById(id);
    }

    public View getCurrentFocus() { return null; }

    public View getDecorView() { return contentRoot; }

    public View getContentRoot() { return contentRoot; }

    public View getContentView() { return contentView; }

    public void invalidateOptionsMenu() {}

    public WindowManager getWindowManager() { return windowManager; }

    public void setTitle(CharSequence title) { this.title = title; }

    public void setTitle(int titleId) { setTitle(getString(titleId)); }

    public final CharSequence getTitle() { return title; }

    public void setTheme(int resId) {
        themeResId = resId;
        getWindow().setStyle(resId);
    }

    public int getThemeResId() { return themeResId; }

    // -------------------------------------------------------------- navigation
    public void finish() {
        finishing = true;
        android.test.Services.finish(this);
    }

    public void finishAffinity() { finish(); }

    public void finishAndRemoveTask() { finish(); }

    public boolean isTaskRoot() { return true; }

    public void moveTaskToBack(boolean nonRoot) {
        android.test.Services.moveTaskToBack(this);
    }

    public void startActivity(Intent intent) {
        android.test.Services.startActivity(this, intent);
    }

    public void startActivity(Intent intent, Bundle options) { startActivity(intent); }

    public void startActivityForResult(Intent intent, int requestCode) {
        android.test.Services.startActivity(this, intent);
    }

    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        startActivity(intent);
    }

    public void setResult(int resultCode) { this.resultCode = resultCode; }

    public void setResult(int resultCode, Intent data) {
        this.resultCode = resultCode;
        this.resultIntent = data;
    }

    public final void setRequestedOrientation(int orientation) {
        requestedOrientation = orientation;
    }

    public final int getRequestedOrientation() {
        return requestedOrientation < 0 ? 1 : requestedOrientation;
    }

    public void overridePendingTransition(int enterAnim, int exitAnim) {}

    public void recreate() {}

    public boolean shouldShowRequestPermissionRationale(String permission) { return false; }

    public void requestPermissions(String[] permissions, int requestCode) {}

    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                          int[] grantResults) {}

    public Intent getIntent() { return intent; }

    public void setIntent(Intent intent) { this.intent = intent; }

    public android.app.Application getApplication() { return application; }

    public void setApplication(Application application) { this.application = application; }

    public boolean isChangingConfigurations() { return false; }

    public void setFinishOnTouchOutside(boolean finish) {}

    public void runOnUiThread(Runnable action) { action.run(); }

    public void onAttachedToWindow() {}

    public void onWindowFocusChanged(boolean hasFocus) {}

    public void onContentChanged() {}

    public void setVolumeControlStream(int streamType) {}

    @Override public String getString(int resId) {
        try {
            return getResources().getString(resId);
        } catch (Throwable t) {
            if (System.getProperty("vphone.verbose") != null) {
                System.out.println("[warn] getString(0x" + Integer.toHexString(resId) + ") failed: " + t);
            }
            throw t;
        }
    }

    public Window getWindowInternal() { return window; }

    public void dumpHierarchy() {
        if (contentRoot != null) android.test.Hierarchy.dump(contentRoot);
    }

    public void makeToast(CharSequence text, int duration) {
        Toast.makeText(this, text, duration).show();
    }

    public Drawable getDrawableSafe(int resId) {
        try {
            return getDrawable(resId);
        } catch (Throwable t) {
            return null;
        }
    }
}
