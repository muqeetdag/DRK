package android.content;

/** Clipboard backed by the emulator's service state. */
public class ClipboardManager {

    public void setPrimaryClip(ClipData clip) {
        if (clip != null) android.test.Services.clipboard = clip.text;
    }

    public ClipData getPrimaryClip() { return new ClipData(android.test.Services.clipboard); }

    public CharSequence getText() { return android.test.Services.clipboard; }

    public boolean hasPrimaryClip() { return android.test.Services.clipboard != null; }

    public void clearPrimaryClip() { android.test.Services.clipboard = ""; }

    public void addPrimaryClipChangedListener(Object listener) {}

    public void removePrimaryClipChangedListener(Object listener) {}
}
