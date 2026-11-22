package android.content;
/** Thrown when no activity can handle an intent. */
public class ActivityNotFoundException extends RuntimeException {
    public ActivityNotFoundException() { super(); }
    public ActivityNotFoundException(String name) { super(name); }
}
