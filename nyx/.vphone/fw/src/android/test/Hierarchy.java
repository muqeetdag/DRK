package android.test;

import android.view.View;
import android.view.ViewGroup;

/** Dumps the measured view tree — the emulator's "uiautomator dump". */
public final class Hierarchy {

    private Hierarchy() {}

    public static String dump(View root) {
        StringBuilder sb = new StringBuilder();
        dump(root, 0, sb);
        return sb.toString();
    }

    private static void dump(View view, int depth, StringBuilder sb) {
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append(view.getClass().getSimpleName())
                .append(" [").append(view.getLeft()).append(',').append(view.getTop())
                .append(" – ").append(view.getRight()).append(',').append(view.getBottom())
                .append("] ").append(view.getWidth()).append('x').append(view.getHeight());
        if (view instanceof android.widget.TextView) {
            CharSequence text = ((android.widget.TextView) view).getText();
            String flat = text == null ? "" : text.toString().replace('\n', ' ');
            if (flat.length() > 46) flat = flat.substring(0, 46) + "…";
            sb.append("  \"").append(flat).append('"');
        }
        if (view.getVisibility() != View.VISIBLE) sb.append("  (hidden)");
        sb.append('\n');
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dump(group.getChildAt(i), depth + 1, sb);
            }
        }
    }

    public static int count(View view) {
        if (view == null) return 0;
        int count = 1;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += count(group.getChildAt(i));
        }
        return count;
    }
}
