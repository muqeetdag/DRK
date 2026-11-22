package android.os;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handler backed by the virtual phone's message loop. All posts are queued and
 * run by the emulator's main loop with real delays, so postDelayed-driven UI
 * flows behave like they do on device.
 */
public class Handler {

    public interface Callback { boolean handleMessage(Message msg); }

    private final Callback callback;
    private final Looper looper;

    public Handler() { this.looper = Looper.getMainLooper(); this.callback = null; }

    public Handler(Looper looper) { this.looper = looper; this.callback = null; }

    public Handler(Callback callback) { this.looper = Looper.getMainLooper(); this.callback = callback; }

    public Looper getLooper() { return looper; }

    public boolean post(Runnable r) { return postDelayed(r, 0); }

    public boolean postDelayed(Runnable r, long delayMillis) {
        looper.schedule(new Message(r, null, 0, System.currentTimeMillis() + delayMillis));
        return true;
    }

    public boolean postAtTime(Runnable r, long uptimeMillis) {
        looper.schedule(new Message(r, null, 0, uptimeMillis));
        return true;
    }

    public boolean postAtFrontOfQueue(Runnable r) { return post(r); }

    public void removeCallbacks(Runnable r) { looper.remove(r); }

    public void removeCallbacksAndMessages(Object token) {}

    public void removeMessages(int what) {}

    public Message obtainMessage() { return new Message(); }

    public Message obtainMessage(int what) { return new Message(what); }

    public Message obtainMessage(int what, Object obj) { return new Message(what, obj); }

    public boolean sendMessage(Message msg) {
        msg.target = this;
        looper.schedule(msg);
        return true;
    }

    public boolean sendEmptyMessage(int what) { return sendMessage(new Message(what)); }

    public boolean sendEmptyMessageDelayed(int what, long delayMillis) {
        Message msg = new Message(what);
        msg.when = System.currentTimeMillis() + delayMillis;
        return sendMessage(msg);
    }

    public void dispatchMessage(Message msg) {
        if (msg.callback != null) {
            msg.callback.run();
        } else if (callback != null) {
            callback.handleMessage(msg);
        } else {
            handleMessage(msg);
        }
    }

    public void handleMessage(Message msg) {}
}
