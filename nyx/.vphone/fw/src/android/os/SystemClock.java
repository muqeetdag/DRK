package android.os;

/** Clock helpers. */
public class SystemClock {
    private static final long START = System.currentTimeMillis();
    public static long uptimeMillis() { return System.currentTimeMillis() - START; }
    public static long elapsedRealtime() { return uptimeMillis(); }
    public static long currentThreadTimeMillis() { return uptimeMillis(); }
    public static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
