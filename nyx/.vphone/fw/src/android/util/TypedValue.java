package android.util;

/** TypedValue constants used by the resource layer. */
public class TypedValue {
    public static final int COMPLEX_UNIT_PX = 0;
    public static final int COMPLEX_UNIT_DIP = 1;
    public static final int COMPLEX_UNIT_SP = 2;
    public static final int COMPLEX_UNIT_PT = 3;
    public static final int COMPLEX_UNIT_IN = 4;
    public static final int COMPLEX_UNIT_MM = 5;
    public static final int TYPE_NULL = 0x00;
    public static final int TYPE_REFERENCE = 0x01;
    public static final int TYPE_ATTRIBUTE = 0x02;
    public static final int TYPE_STRING = 0x03;
    public static final int TYPE_DIMENSION = 0x05;
    public static final int TYPE_FLOAT = 0x04;
    public static final int TYPE_INT_DEC = 0x10;
    public static final int TYPE_INT_HEX = 0x11;
    public static final int TYPE_INT_BOOLEAN = 0x12;
    public static final int TYPE_INT_COLOR_ARGB8 = 0x1c;
    public static final int TYPE_INT_COLOR_RGB8 = 0x1d;
    public static final int TYPE_FIRST_COLOR_INT = 0x1c;
    public static final int TYPE_LAST_COLOR_INT = 0x1f;

    public int type;
    public int data;
    public CharSequence string;

    public float getFloat() { return Float.intBitsToFloat(data); }
}
