package io.nyx.app.hook;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Remembers story media URLs for a configurable window (1..720 hours).
 *
 * Only the URL and a timestamp are stored - a few hundred bytes per story - so
 * this is safe to leave on. Saving still downloads on demand, which means the
 * cache never fills the phone on its own.
 */
final class StoryCache {

    private static final int MAX_ENTRIES = 4000;
    private static final String FILE = "nyx_story_cache.tsv";

    /** url -> expiry epoch millis, insertion ordered, oldest evicted first. */
    private static final Map<String, Long> ENTRIES = new LinkedHashMap<String, Long>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private static boolean loaded = false;
    private static long lastFlush = 0L;

    private StoryCache() {}

    static synchronized void remember(String url, int hours) {
        try {
            if (url == null || !url.startsWith("http")) return;
            loadOnce();
            int window = Math.max(1, Math.min(720, hours));
            long expiry = System.currentTimeMillis() + window * 3600_000L;
            Long existing = ENTRIES.get(url);
            if (existing != null && existing > expiry) return;
            ENTRIES.put(url, expiry);
            long now = System.currentTimeMillis();
            if (now - lastFlush > 60_000L) {
                lastFlush = now;
                flush();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Live (unexpired) URLs, newest last. */
    static synchronized java.util.List<String> live() {
        loadOnce();
        long now = System.currentTimeMillis();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Map.Entry<String, Long> entry : ENTRIES.entrySet()) {
            if (entry.getValue() > now) out.add(entry.getKey());
        }
        return out;
    }

    static synchronized int count() {
        return live().size();
    }

    private static void loadOnce() {
        if (loaded) return;
        loaded = true;
        try {
            File file = file();
            if (file == null || !file.exists()) return;
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                try {
                    ENTRIES.put(line.substring(0, tab), Long.parseLong(line.substring(tab + 1)));
                } catch (Throwable ignored) {
                }
            }
            reader.close();
        } catch (Throwable ignored) {
        }
    }

    private static synchronized void flush() {
        try {
            File file = file();
            if (file == null) return;
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Long> entry : ENTRIES.entrySet()) {
                sb.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(sb.toString().getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        }
    }

    private static File file() {
        String base = PrefsReader.filesDir();
        if (base == null) return null;
        File dir = new File(base);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, FILE);
    }

    /** Debug string for the console: how big the cache is right now. */
    static String summary() {
        return count() + " cached stories · "
                + new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
    }
}
