package com.nyx.vphone;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferencesImpl;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.test.Env;
import android.test.Hierarchy;
import android.test.Launcher;
import android.test.Services;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Virtual phone runner.
 *
 * Boots the real console APK classes on a faithful Android framework stub,
 * renders each screen to a PNG, taps through the UI, and asserts on what
 * happened. Any exception thrown by app code is a test failure with a stack
 * trace — this is where "it crashes on my phone" gets reproduced in the
 * terminal before a build is shipped.
 */
public final class VPhone {

    private static final List<String> failures = new ArrayList<>();
    private static final List<String> passes = new ArrayList<>();
    private static final ByteArrayOutputStream log = new ByteArrayOutputStream();

    private static File work;
    private static File appDir;
    private static SharedPreferencesImpl prefs;
    private static android.content.res.Resources resources;

    public static void main(String[] args) throws Exception {
        String apk = args.length > 0 ? args[0] : "../dist/nyx-1.2.0-obsidian.apk";
        String outDirArg = args.length > 1 ? args[1] : "artifacts";
        // -Dvphone.sdk=21 boots the virtual phone as Android 5.0 instead of 14.
        int sdk = Integer.getInteger("vphone.sdk", 34);
        android.os.Build.VERSION.setSdk(sdk);
        work = new File(System.getProperty("user.dir"));
        appDir = new File(work, "run");
        File screens = new File(outDirArg);
        screens.mkdirs();

        log("virtual phone boot  android=" + android.os.Build.VERSION.RELEASE
                + " (API " + sdk + ")  apk=" + new File(apk).getAbsolutePath());
        bootRuntime(apk);

        // ---------------------------------------------------------------- suite
        section("BOOT");
        bootConsole(screens);

        section("ONBOARDING");
        onboarding(screens);

        section("CONSOLE TABS");
        tabs(screens);

        section("TOGGLES + PERSISTENCE");
        persistence(screens);

        section("ACTIONS");
        actions(screens);

        section("THEME CHANGE");
        themeChange(screens);

        section("REAL SESSION");
        session(screens);

        section("SURVIVE RESTART");
        restart(screens);

        section("DEGRADED DEVICE");
        degraded(screens);

        System.out.println();
        for (String pass : passes) System.out.println("  PASS  " + pass);
        for (String fail : failures) System.out.println("  FAIL  " + fail);
        System.out.println("\n" + passes.size() + " passed, " + failures.size() + " failed");
        if (!failures.isEmpty()) System.exit(1);
    }

    // ------------------------------------------------------------------- boot
    private static void bootRuntime(String apkPath) throws Exception {
        File apk = new File(apkPath);
        if (!apk.exists()) throw new IllegalStateException("APK not found: " + apkPath);

        // Every run starts as a clean install, so results are reproducible.
        if (appDir.exists()) deleteTree(appDir);
        File unpack = new File(appDir, "app");
        unpack.mkdirs();
        unzip(apk, unpack);
        Env.resRoot = unpack.getAbsolutePath();

        Environment.setRoot(new File(appDir, "sdcard"));
        File files = new File(appDir, "data/files");
        File cache = new File(appDir, "data/cache");
        files.mkdirs();
        cache.mkdirs();
        prefs = new SharedPreferencesImpl(new File(files, "nyx_prefs.xml"), 0);

        AppResources appResources = new AppResources(unpack);
        resources = appResources.resources();
        String dump = aaptDump(apk);
        appResources.load(dump);
        log("resources loaded: " + resources.entries().size()
                + (appResources.warnings().isEmpty() ? "" : " warnings=" + appResources.warnings()));

        android.content.Context context = android.test.Boot.boot(resources, files, cache,
                new File(appDir, "sdcard/Android/data"), prefs);

        // Register the two activities by reflection exactly as the manifest does.
        Class<?> onboarding = Class.forName("io.nyx.app.ui.OnboardingActivity");
        Class<?> console = Class.forName("io.nyx.app.ui.MainActivity");
        Launcher.register(onboarding.getName(), () -> (Activity) instantiate(onboarding));
        Launcher.register(console.getName(), () -> (Activity) instantiate(console));

        new android.app.Application().onCreate();
        log("runtime ready");
    }

    private static Object instantiate(Class<?> cls) {
        try {
            java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("cannot instantiate " + cls, e);
        }
    }

    private static String aaptDump(File apk) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("../.build/tools/aapt2", "dump", "resources",
                apk.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        process.waitFor();
        return out.toString("UTF-8");
    }

    // ------------------------------------------------------------------ suites
    private static void bootConsole(File screens) {
        guard("fresh install redirects to onboarding", () -> {
            Launcher.launch("io.nyx.app.ui.MainActivity");
            Activity activity = Services.top();
            check("first launch shows onboarding, not console", activity != null
                    && activity.getClass().getName().endsWith("OnboardingActivity"),
                    activity == null ? "none" : activity.getClass().getSimpleName());
            check("activity resumed", activity != null && activity.isResumed());
            DeviceScreen screen = new DeviceScreen(activity);
            screen.setContent(activity.getContentRoot());
            screen.setSystemBarColors(activity.getWindow().getStatusBarColor(),
                    activity.getWindow().getNavigationBarColor());
            screen.measureAndLayout();
            check("view tree measured", screen.isMeasured());
            check("tree has content", Hierarchy.count(activity.getContentRoot()) > 15,
                    Hierarchy.count(activity.getContentRoot()) + " nodes");
            check("no view with negative width", noInvalidBounds(activity.getContentRoot()));
            capture(screen, new File(screens, "00-onboarding-first-launch.png"));
            logTree(activity, "console");
        });
    }

    private static void onboarding(File screens) {
        guard("onboarding renders and hands over to console", () -> {
            Services.clear();
            // fresh install: no prefs at all
            prefs.edit().clear().commit();
            Launcher.launch("io.nyx.app.ui.OnboardingActivity");
            Activity activity = Services.top();
            check("onboarding is showing", activity != null
                    && activity.getClass().getName().endsWith("OnboardingActivity"));

            DeviceScreen screen = new DeviceScreen(activity);
            screen.setContent(activity.getContentRoot());
            screen.measureAndLayout();
            check("onboarding has root/non-root choices", countClickable(activity.getContentRoot()) >= 3,
                    countClickable(activity.getContentRoot()) + " clickable");
            capture(screen, new File(screens, "00-onboarding.png"));

            View rootCard = findTextContaining(activity.getContentRoot(), "ROOTED");
            check("ROOTED choice present", rootCard != null);
            View nonRoot = findTextContaining(activity.getContentRoot(), "NON-ROOT");
            check("NON-ROOT choice present", nonRoot != null);

            View continueButton = findTextContaining(activity.getContentRoot(), "CONTINUE");
            check("continue button present", continueButton != null);
            if (continueButton != null) {
                tapAndSettle(screen, continueButton);
                check("continue launches console",
                        Services.top() != null
                                && Services.top().getClass().getName().endsWith("MainActivity"));
                check("onboarding remembered", prefs.getBoolean("onboarded", false));
                int mode = prefs.getInt("install_mode", 0);
                check("install mode stored", mode == 1 || mode == 2, "mode=" + mode);
            }
        });
    }

    private static void tabs(File screens) {
        for (String[] tab : new String[][]{{"THEME", "02-console-theme"},
                {"MEDIA", "03-console-media"}, {"SYSTEM", "04-console-system"},
                {"TWEAKS", "05-console-tweaks"}}) {
            guard("tab " + tab[0] + " builds and draws", () -> {
                Activity activity = ActivityHolder.get(screens);
                DeviceScreen screen = new DeviceScreen(activity);
                screen.setContent(activity.getContentRoot());
                screen.measureAndLayout();
                View found = findTagged(activity.getContentRoot(), tab[0]);
                check("tab label " + tab[0] + " present", found != null);
                if (found != null) {
                    tapAndSettle(screen, found);
                    screen.measureAndLayout();
                    check("tab " + tab[0] + " has content",
                            Hierarchy.count(activity.getContentRoot()) > 12);
                    capture(screen, new File(screens, tab[1] + ".png"));
                    logTree(activity, "tab-" + tab[0]);
                }
            });
        }
    }

    private static void persistence(File screens) {
        guard("toggles write through and stay consistent", () -> {
            Activity activity = ActivityHolder.get(screens);
            View toggle = findFirstToggle(activity.getContentRoot());
            check("found a toggle to flip", toggle != null);
            if (toggle == null) return;
            String before = snapshotPrefs();
            String chipBefore = textOf(activity.getContentRoot(), "rituals armed");
            tapAndSettle(new DeviceScreen(activity), toggle);
            String after = snapshotPrefs();
            String chipAfter = textOf(activity.getContentRoot(), "rituals armed");
            log("  rituals chip: '" + chipBefore + "' -> '" + chipAfter + "'");
            check("status chip reflects the toggle immediately",
                    chipBefore != null && chipAfter != null && !chipBefore.equals(chipAfter),
                    chipBefore + " -> " + chipAfter);
            check("toggle changed persisted state", !before.equals(after));
            check("prefs file written", new File(appDir, "data/files/nyx_prefs.xml").exists());
            check("mirror file exists for the hook",
                    new File(appDir, "data/files/nyx_prefs_mirror.xml").exists());
            capture(new DeviceScreen(activity), new File(screens, "06-after-toggle.png"));
        });
    }

    private static void actions(File screens) {
        guard("action rows open without crashing", () -> {
            Activity activity = ActivityHolder.get(screens);
            DeviceScreen screen = new DeviceScreen(activity);
            screen.setContent(activity.getContentRoot());
            screen.measureAndLayout();
            View system = findTagged(activity.getContentRoot(), "SYSTEM");
            if (system != null) tapAndSettle(screen, system);

            int dialogsOpened = 0;
            for (String label : new String[]{"Last crash report", "Repair settings",
                    "Release notes", "Wipe module data", "Folder name"}) {
                View row = findTextContaining(activity.getContentRoot(), label);
                if (row == null) {
                    log("  (skipped action, not on this screen: " + label + ")");
                    continue;
                }
                tapAndSettle(screen, row);
                Dialog dialog = Services.visibleDialog();
                if (dialog != null) {
                    dialogsOpened++;
                    check("dialog for '" + label + "' renders",
                            dialog.getDecorView() != null);
                    dialog.dismiss();
                }
                if (Services.top() != null) {
                    activity = Services.top();
                    screen.setContent(activity.getContentRoot());
                    screen.measureAndLayout();
                }
            }
            check("at least two dialogs opened cleanly", dialogsOpened >= 2,
                    dialogsOpened + " dialogs");
            log("toasts seen: " + Services.TOASTS);
            capture(screen, new File(screens, "07-after-actions.png"));
        });
    }

    private static void themeChange(File screens) {
        guard("changing palette repaints without crashing", () -> {
            Activity activity = ActivityHolder.get(screens);
            DeviceScreen screen = new DeviceScreen(activity);
            screen.setContent(activity.getContentRoot());
            screen.measureAndLayout();
            View themeTab = findTagged(activity.getContentRoot(), "THEME");
            check("THEME tab item located", themeTab != null);
            if (themeTab != null) tapAndSettle(screen, themeTab);
            View palette = findTextContaining(activity.getContentRoot(), "Synthwave");
            check("palette list rendered", palette != null);
            if (palette != null) {
                String before = snapshotPrefs();
                tapAndSettle(screen, palette);
                check("palette preference changed", !before.equals(snapshotPrefs()));
                capture(new DeviceScreen(activity), new File(screens, "08-theme-synthwave.png"));
            }
        });
    }

    /**
     * A realistic session: open, scroll, switch tabs by tapping the real tab bar,
     * press HOME, come back, press BACK. Any crash during this is a failure.
     */
    private static void session(File screens) {
        guard("full user session (scroll, tabs, home, back)", () -> {
            Activity activity = Services.top();
            DeviceScreen screen = new DeviceScreen(activity);
            screen.setContent(activity.getContentRoot());
            screen.measureAndLayout();
            int navigations = 0;

            for (String label : new String[]{"THEME", "MEDIA", "SYSTEM", "TWEAKS"}) {
                View tab = findTabItem(activity.getContentRoot(), label);
                check("tab item '" + label + "' found", tab != null);
                if (tab == null) continue;
                tapAndSettle(screen, tab);
                navigations++;
                check("after tapping " + label + ", that tab is selected",
                        isTabSelected(activity.getContentRoot(), label), label);
            }

            // scroll the current page to the bottom and back up
            for (int i = 0; i < 3; i++) {
                screen.scrollBy(700);
                Looper.getMainLooper().runFor(120);
            }
            capture(screen, new File(screens, "10-scrolled-bottom.png"));
            check("scroll moved the content", scrollOffset(screen) > 0,
                    "scrollY=" + scrollOffset(screen));
            for (int i = 0; i < 3; i++) screen.scrollBy(-700);

            // HOME then return
            tapScreen(screen, DeviceScreen.WIDTH / 2, DeviceScreen.HEIGHT - 40);
            check("HOME pauses the activity", activity.isResumed() == false || Services.LOG.contains("home"));
            android.test.Services.resumeTop();
            screen.measureAndLayout();
            check("activity resumed after returning", activity.isResumed());
            capture(screen, new File(screens, "11-after-home.png"));

            // BACK exits the console without throwing
            tapScreen(screen, 120, DeviceScreen.HEIGHT - 40);
            check("BACK finished the console", activity.isFinishing());
            check("navigated " + navigations + " tabs without crashing", navigations == 4);
        });
    }

    /** Tab items carry a TextView[]{glyph,label} tag — use it to find the bar item. */
    private static View findTabItem(View root, String label) {
        return findTagged(root, label);
    }

    private static View findTagged(View view, String label) {
        if (view == null) return null;
        Object tag = view.getTag();
        if (tag instanceof TextView[]) {
            TextView[] pair = (TextView[]) tag;
            if (pair.length > 1 && pair[1].getText() != null
                    && pair[1].getText().toString().equalsIgnoreCase(label)) return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTagged(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean isTabSelected(View root, String label) {
        View item = findTagged(root, label);
        return item != null && item.getAlpha() > 0.9f;
    }

    private static int scrollOffset(DeviceScreen screen) {
        View scroll = screen.findScrollView(Services.top().getContentRoot());
        return scroll == null ? -1 : (int) scroll.scrollY;
    }

    private static void restart(File screens) {
        guard("relaunch skips onboarding and restores prefs", () -> {
            android.test.Services.STACK.clear();
            Launcher.launch("io.nyx.app.ui.MainActivity");
            Activity activity = Services.top();
            check("console relaunched", activity != null);
            check("mode survived restart", prefs.getInt("install_mode", 0) == 1
                    || prefs.getInt("install_mode", 0) == 2);
            check("onboarded flag survived", prefs.getBoolean("onboarded", false));
        });
    }

    private static void degraded(File screens) {
        guard("console survives missing Instagram / LSPosed", () -> {
            android.content.pm.VirtualPackageManager pm =
                    (android.content.pm.VirtualPackageManager) Services.top().getPackageManager();
            pm.uninstall("com.instagram.android");
            pm.uninstall("org.lsposed.manager");
            android.test.Services.STACK.clear();
            Launcher.launch("io.nyx.app.ui.MainActivity");
            Activity activity = Services.top();
            DeviceScreen screen = new DeviceScreen(activity);
            screen.setContent(activity.getContentRoot());
            screen.measureAndLayout();
            check("console still renders", Hierarchy.count(activity.getContentRoot()) > 15);
            capture(screen, new File(screens, "09-no-instagram.png"));
        });
    }

    // ----------------------------------------------------------------- helpers
    private static ActivityHolder holder;

    private static final class ActivityHolder {
        static Activity get(File screens) {
            Activity activity = Services.top();
            if (activity == null) {
                Launcher.launch("io.nyx.app.ui.MainActivity");
                activity = Services.top();
            }
            return activity;
        }
    }

    /**
     * Taps a view the way a finger would: absolute centre, converted to the
     * target's own coordinates, dispatched down the tree from the root.
     */
    /** Taps raw screen coordinates, including the system navigation bar. */
    private static void tapScreen(DeviceScreen screen, int x, int y) {
        String system = screen.tapSystem(x, y);
        if (system != null) {
            log("  system tap: " + system);
            Looper.getMainLooper().runFor(500);
            screen.measureAndLayout();
            return;
        }
        View root = Services.top() == null ? null : Services.top().getContentRoot();
        View target = root == null ? null : findClickableAt(root, x, y);
        if (target != null) {
            int[] t = new int[2];
            absoluteOffset(target, t);
            target.dispatchTouchEvent(new android.view.MotionEvent(0, x - t[0], y - t[1]));
            target.dispatchTouchEvent(new android.view.MotionEvent(1, x - t[0], y - t[1]));
        }
        Looper.getMainLooper().runFor(500);
        screen.measureAndLayout();
    }

    private static void tapAndSettle(DeviceScreen screen, View view) {
        screen.setContent(Services.top().getContentRoot());
        screen.measureAndLayout();
        int[] abs = new int[2];
        absoluteOffset(view, abs);
        int x = abs[0] + view.getWidth() / 2;
        int y = abs[1] + view.getHeight() / 2;
        View root = Services.top().getContentRoot();
        View target = findClickableAt(root, x, y);
        boolean tapped;
        if (target != null) {
            int[] t = new int[2];
            absoluteOffset(target, t);
            target.dispatchTouchEvent(new android.view.MotionEvent(0, x - t[0], y - t[1]));
            target.dispatchTouchEvent(new android.view.MotionEvent(1, x - t[0], y - t[1]));
            tapped = true;
        } else {
            tapped = view.performClick();
        }
        log("  tap " + view.getClass().getSimpleName() + " @ " + x + "," + y
                + (tapped ? " handled" : " MISSED"));
        Looper.getMainLooper().runFor(600);
        screen.measureAndLayout();
    }

    private static void absoluteOffset(View view, int[] out) {
        out[0] = 0;
        out[1] = 0;
        View cursor = view;
        while (cursor != null) {
            out[0] += cursor.getLeft();
            out[1] += cursor.getTop();
            View parentView = cursor.getParent() instanceof View ? (View) cursor.getParent() : null;
            cursor = parentView;
        }
    }

    private static View findClickableAt(View view, int x, int y) {
        if (view == null || view.getVisibility() != View.VISIBLE) return null;
        View found = null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                int[] abs = new int[2];
                absoluteOffset(child, abs);
                if (x >= abs[0] && x < abs[0] + child.getWidth()
                        && y >= abs[1] && y < abs[1] + child.getHeight()) {
                    View nested = findClickableAt(child, x, y);
                    if (nested != null) { found = nested; break; }
                }
            }
        }
        // Touch goes to the outer handler first in Android; either is correct to
        // click, but preferring the deepest clickable keeps tab/row taps precise.
        if (found != null) return found;
        return view.hasOnClickListeners() ? view : null;
    }

    private static void capture(DeviceScreen screen, File file) {
        try {
            android.graphics.Bitmap bitmap = screen.render();
            ImageIO.write(bitmap.image, "png", file);
            log("  screenshot -> " + file.getName() + " (" + bitmap.getWidth() + "x"
                    + bitmap.getHeight() + ")");
            check("render " + file.getName() + " non-empty",
                    !isBlank(bitmap.image), file.getName());
        } catch (Throwable t) {
            fail("render failed for " + file.getName() + ": " + t);
            t.printStackTrace(System.out);
        }
    }

    private static boolean isBlank(java.awt.image.BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int distinct = 0;
        int first = image.getRGB(w / 2, h / 2);
        outer:
        for (int y = 0; y < h; y += 40) {
            for (int x = 0; x < w; x += 40) {
                if (image.getRGB(x, y) != first) {
                    distinct++;
                    if (distinct > 6) break outer;
                }
            }
        }
        return distinct <= 6;
    }

    private static boolean noInvalidBounds(View view) {
        if (view == null) return true;
        if (view.getWidth() < 0 || view.getHeight() < 0) return false;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (!noInvalidBounds(group.getChildAt(i))) return false;
            }
        }
        return true;
    }

    private static int countClickable(View view) {
        if (view == null) return 0;
        int count = view.hasOnClickListeners() ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                count += countClickable(group.getChildAt(i));
            }
        }
        return count;
    }

    /**
     * Finds the row a label belongs to. A bare TextView usually has no click
     * listener (the handler lives on its parent row), so walk back up to the
     * nearest clickable ancestor — that is what a real finger would hit.
     */
    private static View findTextContaining(View view, String needle) {
        View text = findTextView(view, needle);
        return text == null ? null : clickableAncestor(text);
    }

    private static View clickableAncestor(View view) {
        View cursor = view;
        while (cursor != null) {
            if (cursor.hasOnClickListeners()) return cursor;
            Object parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return view;
    }

    /** Raw text of the first TextView containing `needle`, for state assertions. */
    private static String textOf(View view, String needle) {
        View found = findTextView(view, needle);
        return found == null ? null : String.valueOf(((TextView) found).getText());
    }

    private static View findTextView(View view, String needle) {
        if (view == null) return null;
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.toString().toLowerCase()
                    .contains(needle.toLowerCase())) return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTextView(group.getChildAt(i), needle);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findFirstToggle(View view) {
        if (view == null) return null;
        if (view.hasOnClickListeners() && view.getClass().getSimpleName().contains("Switch")) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findFirstToggle(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String snapshotPrefs() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append(';');
        }
        return sb.toString();
    }

    private static void logTree(Activity activity, String label) {
        String dump = Hierarchy.dump(activity.getContentRoot());
        try (FileOutputStream out = new FileOutputStream(new File(appDir, "tree-" + label + ".txt"))) {
            out.write(dump.getBytes("UTF-8"));
        } catch (Throwable ignored) {
        }
        log("  tree[" + label + "]: "
                + dump.split("\n").length + " lines, " + dump.length() + " chars");
    }

    // ------------------------------------------------------------------ asserts
    private static void guard(String name, Runnable body) {
        try {
            body.run();
            passes.add(name);
        } catch (Throwable t) {
            fail(name + " :: " + t);
            t.printStackTrace(System.out);
        }
    }

    private static void check(String name, boolean ok) { check(name, ok, ""); }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passes.add(name + (detail.isEmpty() ? "" : " (" + detail + ")"));
        } else {
            failures.add(name + (detail.isEmpty() ? "" : " (" + detail + ")"));
        }
    }

    private static void fail(String message) { failures.add(message); }

    private static void section(String name) { System.out.println("\n=== " + name + " ==="); }

    private static void log(String message) { System.out.println("[vphone] " + message); }

    // -------------------------------------------------------------- file utils
    private static void unzip(File zip, File target) throws Exception {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                File out = new File(target, entry.getName());
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                out.getParentFile().mkdirs();
                try (InputStream in = zf.getInputStream(entry);
                     FileOutputStream os = new FileOutputStream(out)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) os.write(buffer, 0, read);
                }
            }
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
