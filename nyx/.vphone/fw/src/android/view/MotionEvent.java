package android.view;

/** MotionEvent with the action constants and coordinates used for taps/swipes. */
public class MotionEvent {

    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;
    public static final int ACTION_OUTSIDE = 4;

    private final int action;
    private final float x, y;
    private final long eventTime;
    private final long downTime;

    public MotionEvent(int action, float x, float y) {
        this.action = action;
        this.x = x;
        this.y = y;
        this.eventTime = System.currentTimeMillis();
        this.downTime = this.eventTime;
    }

    public int getAction() { return action; }

    public int getActionMasked() { return action; }

    public float getX() { return x; }

    public float getY() { return y; }

    public float getRawX() { return x; }

    public float getRawY() { return y; }

    public long getEventTime() { return eventTime; }

    public long getDownTime() { return downTime; }

    public int getPointerCount() { return 1; }

    public int getPointerId(int index) { return 0; }

    public float getX(int pointerIndex) { return x; }

    public float getY(int pointerIndex) { return y; }

    public MotionEvent offset(float dx, float dy) { return new MotionEvent(action, x - dx, y - dy); }

    public static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y,
                                    int metaState) {
        return new MotionEvent(action, x, y);
    }

    @Override public String toString() { return "MotionEvent{" + action + " @ " + x + "," + y + "}"; }
}
