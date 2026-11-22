package android.content;

import android.graphics.drawable.Drawable;

/**
 * Bridges R.* integer constants to the emulator's resource table without the
 * app knowing. The virtual phone installs a resolver before launch.
 */
public final class ResBridge {

    public interface Resolver {
        String string(int id);
        Integer color(int id);
        Drawable drawable(int id);
        boolean exists(int id);
    }

    private static Resolver resolver;

    private ResBridge() {}

    public static void install(Resolver r) { resolver = r; }

    public static boolean exists(int id) { return resolver != null && resolver.exists(id); }

    public static String string(int id) {
        if (resolver == null) throw new IllegalStateException("resource bridge not installed");
        return resolver.string(id);
    }

    public static int color(int id) {
        if (resolver == null) throw new IllegalStateException("resource bridge not installed");
        Integer value = resolver.color(id);
        return value == null ? 0 : value;
    }

    public static Drawable drawable(int id) {
        if (resolver == null) throw new IllegalStateException("resource bridge not installed");
        return resolver.drawable(id);
    }

    public static Drawable bitmapDrawable(String path) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmaps.load(path);
        return bitmap == null ? null : new android.graphics.drawable.BitmapDrawable(bitmap);
    }
}
