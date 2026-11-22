package android.text;

import java.util.Iterator;
import java.util.List;

/** The handful of TextUtils helpers the app uses. */
public class TextUtils {

    public enum TruncateAt { START, MIDDLE, END, MARQUEE }

    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    public static String join(CharSequence delimiter, Object[] tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(tokens[i]);
        }
        return sb.toString();
    }

    public static String join(CharSequence delimiter, Iterable<?> tokens) {
        StringBuilder sb = new StringBuilder();
        Iterator<?> it = tokens.iterator();
        boolean first = true;
        while (it.hasNext()) {
            if (!first) sb.append(delimiter);
            sb.append(it.next());
            first = false;
        }
        return sb.toString();
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.toString().equals(b.toString());
    }

    public static String[] split(String text, String expression) {
        if (text == null) return new String[0];
        return text.split(expression);
    }

    public static CharSequence concat(CharSequence... parts) {
        StringBuilder sb = new StringBuilder();
        for (CharSequence part : parts) if (part != null) sb.append(part);
        return sb.toString();
    }

    public static String stringOrEmpty(CharSequence s) { return s == null ? "" : s.toString(); }
}
