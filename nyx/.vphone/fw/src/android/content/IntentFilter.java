package android.content;

import java.util.ArrayList;
import java.util.List;

/** Receiver intent filter. */
public class IntentFilter {
    private final List<String> actions = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();

    public IntentFilter() {}

    public IntentFilter(String action) { addAction(action); }

    public final void addAction(String action) { actions.add(action); }

    public final void addCategory(String category) { categories.add(category); }

    public final int countActions() { return actions.size(); }

    public final String getAction(int index) { return actions.get(index); }

    public final boolean hasAction(String action) { return actions.contains(action); }
}
