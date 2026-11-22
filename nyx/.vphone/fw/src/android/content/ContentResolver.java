package android.content;

import android.database.Cursor;
import android.net.Uri;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** ContentResolver over a temp file per Uri, enough to exercise media saves. */
public class ContentResolver {

    private final Context context;
    private final java.util.Map<String, File> files = new java.util.LinkedHashMap<>();

    public ContentResolver(Context context) { this.context = context; }

    public Uri insert(Uri collection, ContentValues values) {
        String name = values == null ? null : values.getAsString("_display_name");
        if (name == null) name = "media-" + System.nanoTime() + ".bin";
        File dir = new File(context.getCacheDir(), "mediastore");
        dir.mkdirs();
        File target = new File(dir, name);
        String id = String.valueOf(Math.abs(target.getAbsolutePath().hashCode()));
        files.put(id, target);
        return Uri.parse(collection.toString() + "/" + id);
    }

    public int update(Uri uri, ContentValues values, String selection, String[] args) { return 1; }

    public int delete(Uri uri, String selection, String[] args) {
        File file = files.get(uri.getLastPathSegment());
        return file != null && file.delete() ? 1 : 0;
    }

    public OutputStream openOutputStream(Uri uri) throws FileNotFoundException {
        File file = files.get(uri.getLastPathSegment());
        if (file == null) throw new FileNotFoundException(uri.toString());
        return new FileOutputStream(file);
    }

    public OutputStream openOutputStream(Uri uri, String mode) throws FileNotFoundException {
        return openOutputStream(uri);
    }

    public InputStream openInputStream(Uri uri) throws FileNotFoundException {
        File file = files.get(uri.getLastPathSegment());
        if (file == null) throw new FileNotFoundException(uri.toString());
        return new java.io.FileInputStream(file);
    }

    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        return null;
    }
}
