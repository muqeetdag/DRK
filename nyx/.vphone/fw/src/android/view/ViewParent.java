package android.view;

/** Contract used by children to request layout / invalidation. */
public interface ViewParent {
    void requestLayout();
    void invalidateChild(View child, android.graphics.Rect dirty);
    ViewParent getParent();
    boolean isLayoutRequested();
}
