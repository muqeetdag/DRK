package android.os;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Single-threaded message loop for the virtual phone. Time is virtual where
 * possible: runUntil() advances a clock so animations and postDelayed chains
 * can be played forward deterministically.
 */
public class Looper {

    private static Looper mainLooper;
    private static final AtomicLong IDS = new AtomicLong();

    private final PriorityQueue<Message> queue = new PriorityQueue<>(
            (a, b) -> Long.compare(a.when, b.when));
    private final List<Runnable> idleHandlers = new ArrayList<>();
    private final long seqBase = 0;
    private long nextSeq = 0;
    public boolean paused;
    private long virtualNow = System.currentTimeMillis();

    public static Looper getMainLooper() {
        if (mainLooper == null) mainLooper = new Looper();
        return mainLooper;
    }

    public static Looper myLooper() { return getMainLooper(); }

    public static void prepare() {}

    public static void loop() {}

    public static void prepareMainLooper() {}

    public void quit() { paused = true; }

    public void quitSafely() { paused = true; }

    public Handler handler() { return new Handler(this); }

    void schedule(Message message) {
        message.when = Math.max(message.when, virtualNow);
        queue.add(message);
    }

    void remove(Runnable runnable) {
        queue.removeIf(m -> m.callback == runnable);
    }

    public long now() { return virtualNow; }

    public void advanceTo(long time) { virtualNow = Math.max(virtualNow, time); }

    /** Runs every message due within the given number of virtual milliseconds. */
    public int runFor(long durationMs) {
        long deadline = virtualNow + durationMs;
        int executed = 0;
        while (!queue.isEmpty() && queue.peek().when <= deadline) {
            Message message = queue.poll();
            virtualNow = Math.max(virtualNow, message.when);
            dispatch(message);
            executed++;
        }
        virtualNow = deadline;
        return executed;
    }

    /** Runs queued work regardless of its delay (used to settle async flows). */
    public int drain(int maxMessages) {
        int executed = 0;
        while (!queue.isEmpty() && executed < maxMessages) {
            Message message = queue.poll();
            virtualNow = Math.max(virtualNow, message.when);
            dispatch(message);
            executed++;
        }
        return executed;
    }

    private void dispatch(Message message) {
        try {
            if (message.target != null) message.target.dispatchMessage(message);
            else if (message.callback != null) message.callback.run();
        } catch (Throwable error) {
            System.out.println("[looper] message threw: " + error);
            error.printStackTrace(System.out);
        }
    }

    public int pending() { return queue.size(); }

    public void reset() {
        queue.clear();
        virtualNow = System.currentTimeMillis();
    }
}
