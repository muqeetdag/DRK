package android.content;

/** Clipboard payload. */
public class ClipData {

    public final String text;

    public ClipData(String text) { this.text = text; }

    public static ClipData newPlainText(CharSequence label, CharSequence text) {
        return new ClipData(text == null ? "" : text.toString());
    }

    public CharSequence getText() { return text; }

    public int getItemCount() { return 1; }

    public Item getItemAt(int index) { return new Item(text); }

    public static class Item {
        public Item(CharSequence text) {}
        public CharSequence getText() { return null; }
    }
}
