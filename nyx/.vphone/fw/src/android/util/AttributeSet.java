package android.util;

/** Opaque XML attributes (unused at runtime, present for signatures). */
public interface AttributeSet {
    int getAttributeCount();
    String getAttributeName(int index);
    String getAttributeValue(int index);
    String getAttributeValue(String namespace, String name);
    int getAttributeIntValue(String namespace, String attribute, int defaultValue);
}
