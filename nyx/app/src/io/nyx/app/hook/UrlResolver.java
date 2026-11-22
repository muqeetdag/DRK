package io.nyx.app.hook;

import android.view.View;
import android.net.Uri;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves the media URL of whatever is on screen. Two passes:
 *
 *  1. shallow visible-tree scan for URL-bearing tags, content descriptions and
 *     drawable sources (fast, no reflection into host models),
 *  2. one-level reflection into host model objects found in tags.
 *
 * Videos are preferred when present, and a short-lived cache keeps the last
 * good URL so the dock's central button still works right after a swipe.
 */
public final class UrlResolver {

    private static final String[] URL_FIELDS = {
            "mUrl", "url", "mVideoUrl", "videoUrl", "mImageUrl", "imageUrl", "src", "A0"
    };
    private static final String[] URL_METHODS = {
            "getVideoUrl", "getImageUrl", "getUrl", "getSrc"
    };

    private static String cached = null;
    private static long cachedAt = 0L;

    private UrlResolver() {}

    public static String resolve(View root) {
        try {
            List<String> found = new ArrayList<>();
            scan(root, 0, found);
            if (!found.isEmpty()) {
                String chosen = pick(found);
                if (chosen != null) {
                    cached = chosen;
                    cachedAt = System.currentTimeMillis();
                }
                return chosen;
            }
        } catch (Throwable ignored) {
        }
        if (cached != null && System.currentTimeMillis() - cachedAt < 12000) return cached;
        return null;
    }

    private static String pick(List<String> urls) {
        for (String url : urls) if (url.contains(".mp4") || url.contains("video")) return url;
        for (String url : urls) if (!url.contains(".webp") && !url.contains("profile_pic")) return url;
        return urls.isEmpty() ? null : urls.get(0);
    }

    private static void scan(View view, int depth, List<String> out) {
        if (view == null || depth > 44 || out.size() >= 12) return;
        add(out, sanitize(String.valueOf(view.getContentDescription())));
        add(out, fromObject(view.getTag(), 0));
        if (view instanceof ImageView) {
            try {
                Object drawable = ((ImageView) view).getDrawable();
                add(out, fromObject(drawable, 0));
            } catch (Throwable ignored) {
            }
        }
        String name = view.getClass().getName().toLowerCase();
        if (name.contains("playable") || name.contains("video")) {
            add(out, fromObject(view, 0));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scan(group.getChildAt(i), depth + 1, out);
            }
        }
    }

    private static void add(List<String> out, String url) {
        if (url != null && !out.contains(url)) out.add(url);
    }

    private static String fromObject(Object object, int depth) {
        if (object == null || depth > 2) return null;
        if (object instanceof String) return sanitize((String) object);
        if (object instanceof CharSequence) return sanitize(object.toString());
        if (object instanceof Uri) return sanitize(object.toString());

        String direct = reflectUrl(object);
        if (direct != null) return direct;
        if (object instanceof Object[]) {
            for (Object item : (Object[]) object) {
                String found = fromObject(item, depth + 1);
                if (found != null) return found;
            }
            return null;
        }
        if (object instanceof Iterable) {
            for (Object item : (Iterable<?>) object) {
                String found = fromObject(item, depth + 1);
                if (found != null) return found;
            }
            return null;
        }
        for (String fieldName : new String[]{"media", "model", "data", "item", "video"}) {
            try {
                Field field = object.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                String found = fromObject(field.get(object), depth + 1);
                if (found != null) return found;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String reflectUrl(Object object) {
        for (String methodName : URL_METHODS) {
            try {
                Method method = object.getClass().getMethod(methodName);
                method.setAccessible(true);
                String url = sanitize(String.valueOf(method.invoke(object)));
                if (url != null) return url;
            } catch (Throwable ignored) {
            }
        }
        for (String fieldName : URL_FIELDS) {
            Class<?> type = object.getClass();
            while (type != null && type != Object.class) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    String url = sanitize(String.valueOf(field.get(object)));
                    if (url != null) return url;
                    break;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                } catch (Throwable ignored) {
                    break;
                }
            }
        }
        return null;
    }

    /** Only absolute https media URLs survive; sentinels are rejected. */
    public static String sanitize(String candidate) {
        if (candidate == null) return null;
        String value = candidate.trim();
        int start = value.indexOf("https://");
        if (start < 0) return null;
        int end = value.length();
        for (char stop : new char[]{'"', '\'', ' ', '\n', '\t', ')'}) {
            int index = value.indexOf(stop, start);
            if (index > 0 && index < end) end = index;
        }
        String url = value.substring(start, end);
        if (url.length() < 24 || url.length() > 4096) return null;
        if (url.endsWith("instagram.com") || url.endsWith("instagram.com/")) return null;
        boolean media = url.contains(".mp4") || url.contains(".jpg") || url.contains(".webp")
                || url.contains(".png") || url.contains("cdninstagram") || url.contains("fbcdn")
                || url.contains("scontent");
        return media ? url : null;
    }

    public static List<String> empty() {
        return Collections.emptyList();
    }
}
