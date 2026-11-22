package android.view.animation;

/** Animation container (window transitions only in this app). */
public class Animation {
    public static final int INFINITE = -1;
    public static final int RESTART = 1;
    public static final int REVERSE = 2;
    public static final int START_ON_FIRST_FRAME = -1;
    public static final int ABSOLUTE = 0;
    public static final int RELATIVE_TO_SELF = 1;
    public static final int RELATIVE_TO_PARENT = 2;
    public static final int ZORDER_NORMAL = 0;
    public static final int ZORDER_TOP = 1;
    public static final int ZORDER_BOTTOM = -1;

    private long duration = 300;
    private long startOffset;
    private boolean fillAfter;
    private boolean fillBefore = true;
    private int repeatCount;
    private int repeatMode;
    private Interpolator interpolator;

    public void setDuration(long durationMillis) { duration = durationMillis; }

    public long getDuration() { return duration; }

    public void setStartOffset(long startOffset) { this.startOffset = startOffset; }

    public void setFillAfter(boolean fillAfter) { this.fillAfter = fillAfter; }

    public void setFillBefore(boolean fillBefore) { this.fillBefore = fillBefore; }

    public void setRepeatCount(int repeatCount) { this.repeatCount = repeatCount; }

    public void setRepeatMode(int repeatMode) { this.repeatMode = repeatMode; }

    public void setInterpolator(Interpolator interpolator) { this.interpolator = interpolator; }

    public void start() {}

    public void startNow() {}

    public void cancel() {}

    public void reset() {}

    public boolean hasEnded() { return true; }

    public void setAnimationListener(Object listener) {}
}
