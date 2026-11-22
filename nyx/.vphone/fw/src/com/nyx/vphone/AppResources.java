package com.nyx.vphone;

import android.content.ResBridge;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the real resource table produced by aapt2 so R.string / R.color /
 * R.drawable resolve exactly as on a device.
 *
 * The APK's res/ folder is unzipped next to this, and `aapt2 dump resources`
 * gives ids + values; every id is registered, and a lookup that isn't there
 * throws like Android does.
 */
public final class AppResources {

    private final Resources resources = new Resources();
    private final File outDir;
    private final List<String> warnings = new ArrayList<>();

    public AppResources(File outDir) {
        this.outDir = outDir;
        resources.setResDir(new File(outDir, "res"));
    }

    public Resources resources() { return resources; }

    public List<String> warnings() { return warnings; }

    public void load(String dump) {
        String type = null;
        int currentId = 0;
        String currentName = null;
        String parent = null;
        List<String> arrayItems = null;

        for (String raw : dump.split("\n")) {
            String line = raw;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("type ")) {
                String[] parts = trimmed.split("\\s+");
                type = parts.length > 1 ? parts[1] : null;
                continue;
            }
            if (trimmed.startsWith("resource 0x")) {
                String[] parts = trimmed.split("\\s+");
                try {
                    currentId = (int) Long.parseLong(parts[1].substring(2), 16);
                } catch (Exception e) {
                    currentId = 0;
                }
                String name = parts.length > 2 ? parts[2] : "";
                int slash = name.indexOf('/');
                if (slash > 0) {
                    type = name.substring(0, slash);
                    name = name.substring(slash + 1);
                }
                currentName = name;
                parent = null;
                arrayItems = "array".equals(type) ? new ArrayList<>() : null;
                if (currentId != 0 && currentName != null) {
                    resources.put(type, currentId, currentName, null, null, null);
                }
                continue;
            }
            if (currentName == null) continue;

            if (trimmed.startsWith("parent=")) {
                parent = trimmed.substring("parent=".length()).trim();
                Resources.Res res = resources.byId(currentId);
                if (res != null) res.parent = parent;
                continue;
            }
            if (trimmed.startsWith("\"") && arrayItems == null) {
                String value = trimmed;
                if (value.endsWith("\"")) value = value.substring(1, value.length() - 1);
                setValue(currentId, value);
                continue;
            }
            if (trimmed.startsWith("(") && (trimmed.contains("(array)") || trimmed.contains("(file)")
                    || trimmed.contains("(style)") || trimmed.startsWith("(mdpi)")
                    || trimmed.startsWith("(hdpi)") || trimmed.startsWith("(xhdpi)")
                    || trimmed.startsWith("(xxhdpi)") || trimmed.startsWith("(xxxhdpi)")
                    || trimmed.startsWith("(anydpi)") || trimmed.startsWith("()")
                    || trimmed.startsWith("(dpi)"))) {
                if (trimmed.contains("(file)")) {
                    Matcher m = Pattern.compile("res/\\S+").matcher(trimmed);
                    if (m.find()) {
                        File file = new File(outDir, m.group());
                        if (file.exists()) {
                            Resources.Res res = resources.byId(currentId);
                            if (res != null) res.file = file;
                        } else {
                            warnings.add("missing asset for " + currentName + ": " + m.group());
                        }
                    }
                } else if (trimmed.startsWith("()")) {
                    String rest = trimmed.substring(2).trim();
                    if (rest.startsWith("#")) {
                        setValue(currentId, android.graphics.Color.parseColor(
                                rest.substring(0, rest.indexOf(' ') > 0 ? rest.indexOf(' ') : rest.length())));
                    } else if ("(array) size=".length() > 0 && rest.contains("(array)")) {
                        continue;
                    } else if (rest.startsWith("\"")) {
                        String value = rest;
                        int close = value.lastIndexOf('"');
                        if (close > 0) setValue(currentId, value.substring(1, close));
                    } else if (rest.startsWith("0x") || rest.startsWith("@")) {
                        continue;
                    } else if (rest.matches("^(true|false)$")) {
                        setValue(currentId, Boolean.parseBoolean(rest));
                    }
                } else if (trimmed.contains("(style)")) {
                    // style items are read from the following lines
                }
                continue;
            }
            if (trimmed.startsWith("0x") && trimmed.contains("=") && currentId != 0) {
                Resources.Res res = resources.byId(currentId);
                if (res != null) {
                    int eq = trimmed.indexOf('=');
                    res.styleItems.put(trimmed.substring(0, eq).trim(),
                            trimmed.substring(eq + 1).trim());
                }
                continue;
            }
            if (trimmed.startsWith("[") && arrayItems != null) {
                Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(trimmed);
                while (m.find()) arrayItems.add(m.group(1));
                Resources.Res res = resources.byId(currentId);
                if (res != null && !arrayItems.isEmpty()) {
                    res.arrayItems = arrayItems.toArray(new String[0]);
                }
                continue;
            }
        }
        installBridge();
    }

    private void setValue(int id, Object value) {
        Resources.Res res = resources.byId(id);
        if (res != null) res.value = value;
    }

    private void installBridge() {
        ResBridge.install(new ResBridge.Resolver() {
            @Override public String string(int id) { return resources.getString(id); }

            @Override public Integer color(int id) {
                Resources.Res res = resources.byId(id);
                return res != null && res.value instanceof Integer ? (Integer) res.value : null;
            }

            @Override public Drawable drawable(int id) { return resources.getDrawable(id); }

            @Override public boolean exists(int id) { return resources.byId(id) != null; }
        });
    }
}
