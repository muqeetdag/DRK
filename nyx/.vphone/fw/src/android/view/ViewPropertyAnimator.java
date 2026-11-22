package android.view;

/**
 * ViewPropertyAnimator. Animations complete immediately but record themselves,
 * and end actions run — enough to observe ordering without frame timing.
 */
public class ViewPropertyAnimator {

    private final View view;
    private float alpha = Float.NaN;
    private float translationX = Float.NaN, translationY = Float.NaN;
    private float scaleX = Float.NaN, scaleY = Float.NaN;
    private long duration = 300;
    private Runnable endAction;
    private Runnable startAction;
    private android.view.animation.Interpolator interpolator;
    private boolean started;

    public ViewPropertyAnimator(View view) { this.view = view; }

    public ViewPropertyAnimator alpha(float value) { alpha = value; return this; }

    public ViewPropertyAnimator translationX(float value) { translationX = value; return this; }

    public ViewPropertyAnimator translationY(float value) { translationY = value; return this; }

    public ViewPropertyAnimator translationYBy(float value) {
        translationY = view.getTranslationY() + value;
        return this;
    }

    public ViewPropertyAnimator scaleX(float value) { scaleX = value; return this; }

    public ViewPropertyAnimator scaleY(float value) { scaleY = value; return this; }

    public ViewPropertyAnimator rotation(float value) { return this; }

    public ViewPropertyAnimator rotationBy(float value) { return this; }

    public ViewPropertyAnimator setDuration(long duration) { this.duration = duration; return this; }

    public long getDuration() { return duration; }

    public ViewPropertyAnimator setStartDelay(long delay) { return this; }

    public ViewPropertyAnimator setInterpolator(android.view.animation.Interpolator i) {
        interpolator = i;
        return this;
    }

    public ViewPropertyAnimator setUpdateListener(Object listener) { return this; }

    public ViewPropertyAnimator setListener(Object listener) { return this; }

    public ViewPropertyAnimator withStartAction(Runnable action) { startAction = action; return this; }

    public ViewPropertyAnimator withEndAction(Runnable action) { endAction = action; return this; }

    public void start() {
        if (started) return;
        started = true;
        if (startAction != null) startAction.run();
        applyEndState();
        if (view != null) view.invalidate();
        if (endAction != null) {
            // End actions run on the next loop turn, like a finished animation.
            view.post(endAction);
        }
    }

    public void cancel() { started = false; }

    private void applyEndState() {
        if (view == null) return;
        if (!Float.isNaN(alpha)) view.setAlpha(alpha);
        if (!Float.isNaN(translationY)) view.setTranslationY(translationY);
        if (!Float.isNaN(translationX)) view.setTranslationX(translationX);
        if (!Float.isNaN(scaleX)) view.setScaleX(scaleX);
        if (!Float.isNaN(scaleY)) view.setScaleY(scaleY);
    }

    public long duration() { return duration; }
}
