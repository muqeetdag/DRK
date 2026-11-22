package android.os;

/** Message for Handler-based flows. */
public class Message {
    public int what;
    public int arg1;
    public int arg2;
    public Object obj;
    public Handler target;
    Runnable callback;
    long when = System.currentTimeMillis();

    public Message() {}

    public Message(int what) { this.what = what; }

    public Message(int what, Object obj) { this.what = what; this.obj = obj; }

    Message(Runnable callback, Object obj, int what, long when) {
        this.callback = callback;
        this.obj = obj;
        this.what = what;
        this.when = when;
    }

    public static Message obtain() { return new Message(); }

    public void sendToTarget() { if (target != null) target.sendMessage(this); }
}
