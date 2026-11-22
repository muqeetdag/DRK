package android.database;
public interface Cursor {
    int getCount();
    boolean moveToFirst();
    boolean moveToNext();
    int getColumnIndex(String name);
    String getString(int index);
    int getInt(int index);
    long getLong(int index);
    void close();
    boolean isClosed();
}
