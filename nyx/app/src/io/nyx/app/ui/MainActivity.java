package io.nyx.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.nyx.app.R;
import io.nyx.app.core.Brand;
import io.nyx.app.core.GenSettings;
import io.nyx.app.core.Haptics;
import io.nyx.app.core.MediaStoreX;
import io.nyx.app.core.Prefs;
import io.nyx.app.core.Themes;

/**
 * The companion console. Built entirely from framework views with a small
 * programmatic design system so the dark aesthetic stays identical on every
 * OEM skin and the APK stays tiny.
 */
public class MainActivity extends Activity {

    private static final int TAB_HOME = 0;
    private static final int TAB_THEME = 1;
    private static final int TAB_MEDIA = 2;
    private static final int TAB_SYSTEM = 3;
    private static final String IG = "com.instagram.android";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private Themes.Palette theme;
    private FrameLayout content;
    private LinearLayout tabBar;
    private int tab = TAB_HOME;
    private int density;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(new SplashView(this));
        density = (int) getResources().getDisplayMetrics().density;
        Prefs.seedIfEmpty(this);
        Prefs.writeMirror(this);
        prefs = Prefs.open(this);
        theme = Themes.current(this);
        styleSystemBars();
        buildShell();
        show(TAB_HOME);
        if (content != null) {
            content.setAlpha(0f);
            content.animate().alpha(1f).setDuration(260).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Themes.Palette fresh = Themes.current(this);
        boolean repaint = fresh.bg != theme.bg || fresh.accent != theme.accent
                || fresh.secondary != theme.secondary;
        theme = fresh;
        if (repaint) {
            styleSystemBars();
            buildShell();
        }
        show(tab);
    }

    private void styleSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(theme.bg);
        window.setNavigationBarColor(theme.bg);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    }

    // ------------------------------------------------------------- shell
    private void buildShell() {
        // SplashView is replaced here; the console fades in via content alpha.
        FrameLayout root = new FrameLayout(this);
        AuroraView aurora = new AuroraView(this, theme);
        root.addView(aurora, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setFitsSystemWindows(true);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        content = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(dp(10), dp(8), dp(10), dp(14));
        tabBar.setBackground(rounded(0xEE0A0C14, 26, 1, theme.line));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, -2);
        barLp.setMargins(dp(14), 0, dp(14), dp(12));
        column.addView(tabBar, barLp);

        String[] labels = {"CONSOLE", "THEME", "MEDIA", "SYSTEM"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(4), dp(8), dp(4), dp(6));
            TextView glyph = new TextView(this);
            glyph.setText(i == 0 ? "◈" : i == 1 ? "◐" : i == 2 ? "▣" : "⌘");
            glyph.setTextSize(17);
            glyph.setGravity(Gravity.CENTER);
            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(9);
            label.setLetterSpacing(0.12f);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(3), 0, 0);
            item.addView(glyph);
            item.addView(label);
            item.setTag(new TextView[]{glyph, label});
            item.setOnClickListener(v -> {
                Haptics.tick(this);
                show(index);
            });
            tabBar.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        setContentView(root);
        paintTabs();
    }

    private void paintTabs() {
        for (int i = 0; i < tabBar.getChildCount(); i++) {
            View child = tabBar.getChildAt(i);
            TextView[] pair = (TextView[]) child.getTag();
            boolean on = i == tab;
            pair[0].setTextColor(on ? theme.accent : theme.muted);
            pair[1].setTextColor(on ? theme.text : theme.muted);
            child.setAlpha(on ? 1f : 0.72f);
            child.setBackground(on
                    ? rounded(Themes.alpha(theme.accent, 26), 18, 0, 0)
                    : null);
        }
    }

    // ------------------------------------------------------------- pages
    private void show(int which) {
        tab = which;
        paintTabs();
        content.removeAllViews();
        content.setPadding(dp(18), dp(10), dp(18), dp(24));
        switch (which) {
            case TAB_HOME: buildHome(); break;
            case TAB_THEME: buildTheme(); break;
            case TAB_MEDIA: buildMedia(); break;
            default: buildSystem(); break;
        }
    }

    private void buildHome() {
        content.addView(header());
        content.addView(statusChip());
        content.addView(sectionLabel("INSTAGRAM SHELL"));
        content.addView(toggleRow(Prefs.FLOATING_NAV, "Floating dock",
                "Detached translucent nav pill with live glow ring", "◈"));
        content.addView(toggleRow(Prefs.GLASS, "Glass header",
                "Frosted top bar that follows your theme", "▤"));

        content.addView(sectionLabel("MEDIA KEEPER"));
        content.addView(toggleRow(Prefs.REELS, "Reel keeper", "Save reels in one tap", "▶"));
        content.addView(toggleRow(Prefs.STORIES, "Story keeper", "Save stories and highlights", "◔"));
        content.addView(toggleRow(Prefs.FEED, "Feed keeper", "Save photos, carousels and videos", "▦"));

        content.addView(sectionLabel("GESTURES & MOTION"));
        content.addView(toggleRow(Prefs.LONG_PRESS, "Long-press to save",
                "Hold any photo or reel on screen to pull it down", "✊"));
        content.addView(toggleRow(Prefs.ZEN, "Zen scroll",
                "Floating dock slips away while you scroll down", "≈"));

        content.addView(sectionLabel("RITUAL"));
        content.addView(actionRow("Open Instagram", "Launch the patched host", "↗", v -> launchInstagram()));
        content.addView(actionRow("Attach module", "Copy the LSPosed setup steps", "⧉", v -> showSetup()));
        content.addView(footer());
    }

    private void buildTheme() {
        content.addView(pageTitle("THEME ENGINE", "Five hand-tuned dark palettes. Changes apply live to Instagram."));
        for (int i = 0; i < Themes.PALETTES.length; i++) {
            final int index = i;
            content.addView(themeCard(Themes.PALETTES[i], i == prefs.getInt(Prefs.THEME, 0)));
        }
        content.addView(sectionLabel("ACCENT LAB"));

        Themes.Palette current = Themes.current(this);
        int accent = prefs.getInt(Prefs.ACCENT, 0);
        int secondary = prefs.getInt(Prefs.SECONDARY, 0);

        content.addView(swatchRow("PRIMARY ACCENT", accent == 0 ? current.accent : accent,
                new int[]{0xFF7C5CFF, 0xFF3DF5C2, 0xFFFF3DAB, 0xFFFFB347, 0xFF2EC4FF, 0xFFB6FF3D},
                true));
        content.addView(swatchRow("SECONDARY GLOW", secondary == 0 ? current.secondary : secondary,
                new int[]{0xFF22D3EE, 0xFFFF5C7A, 0xFFF472B6, 0xFF4ADE80, 0xFFFACC15, 0xFF94A3B8},
                false));

        content.addView(actionRow("Reset accent lab", "Return to the build default", "↺",
                v -> {
                    Prefs.commit(this, Prefs.ACCENT, 0);
                    Prefs.commit(this, Prefs.SECONDARY, 0);
                    reloadTheme();
                }));
        content.addView(footer());
    }

    private void buildMedia() {
        int count = MediaStoreX.fileCount(MediaStoreX.folder(this));
        long bytes = MediaStoreX.totalBytes(MediaStoreX.folder(this));
        content.addView(pageTitle("MEDIA VAULT", "Everything saved lands in your gallery and a mirrored Downloads folder."));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(14), 0, dp(6));
        stats.addView(statCard(String.valueOf(count), "FILES", theme.accent), weight());
        stats.addView(statCard(MediaStoreX.humanSize(bytes), "SIZE", theme.secondary), weight());
        content.addView(stats);

        content.addView(sectionLabel("DESTINATION"));
        content.addView(actionRow("Folder name", MediaStoreX.folderName(this), "✎", v -> askFolder()));
        content.addView(actionRow("One-tap cleaner", "Purge every saved file from the vault", "⌫", v -> confirmCleaner()));
        content.addView(actionRow("Open gallery", "Jump to your device media browser", "▦", v -> openGallery()));
        content.addView(actionRow("Scan storage", "Recount files in the vault folder", "⟳", v -> {
            show(TAB_MEDIA);
            Toast.makeText(this, "Recounted", Toast.LENGTH_SHORT).show();
        }));
        content.addView(footer());
    }

    private void buildSystem() {
        content.addView(pageTitle("SYSTEM", "Module wiring, versions and support."));

        content.addView(sectionLabel("MODULE"));
        content.addView(infoRow("Package", getPackageName()));
        content.addView(infoRow("Version", Brand.VERSION + " · " + Brand.VERSION_CODE));
        content.addView(infoRow("Flavor", Brand.FLAVOR));
        content.addView(infoRow("Channel", Brand.CHANNEL));
        content.addView(infoRow("Target", IG));
        content.addView(infoRow("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"));

        content.addView(sectionLabel("ATTACHMENT STATE"));
        boolean igInstalled = isInstalled(IG);
        boolean lsposed = isInstalled("org.lsposed.manager") || isInstalled("de.robv.android.xposed.installer");
        content.addView(stateRow("Instagram", igInstalled));
        content.addView(stateRow("LSPosed manager", lsposed));
        content.addView(stateRow("Hook bridge", hookBridgePresent()));

        content.addView(sectionLabel("TOOLS"));
        content.addView(toggleRow(Prefs.HAPTIC, "Haptic ticks", "Subtle buzz on every tap", "≈"));
        content.addView(toggleRow(Prefs.DEBUG_HUD, "Diagnostics HUD",
                "Overlay in Instagram: palette, armed hooks, last media URL", "⌁"));
        content.addView(actionRow("Setup guide", "Three steps to attach the module", "?", v -> showSetup()));
        content.addView(actionRow("Export settings", "Copy a JSON snapshot to clipboard", "⇪", v -> exportSettings()));
        content.addView(actionRow("Wipe module data", "Reset every toggle and theme", "✕", v -> confirmWipe()));
        content.addView(actionRow("Release notes", "What changed in this build", "◈", v -> showReleaseNotes()));
        if (!TextUtils.isEmpty(Brand.GITHUB) || !TextUtils.isEmpty(Brand.TELEGRAM)) {
            content.addView(sectionLabel("LINKS"));
            if (!TextUtils.isEmpty(Brand.GITHUB)) {
                content.addView(actionRow("Source", Brand.GITHUB, "↗", v -> open(Brand.GITHUB)));
            }
            if (!TextUtils.isEmpty(Brand.TELEGRAM)) {
                content.addView(actionRow("Support", Brand.TELEGRAM, "↗", v -> open(Brand.TELEGRAM)));
            }
        }
        content.addView(footer());
    }

    // ------------------------------------------------------------ pieces
    private View header() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setPadding(0, dp(18), 0, dp(6));

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.nyx_mark);
        wrap.addView(mark, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(14), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(Brand.NAME);
        name.setTextSize(30);
        name.setLetterSpacing(-0.04f);
        name.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        name.setTextColor(theme.text);
        text.addView(name);
        TextView tag = new TextView(this);
        tag.setText(Brand.TAGLINE);
        tag.setTextSize(12);
        tag.setTextColor(theme.muted);
        tag.setPadding(0, dp(2), 0, 0);
        text.addView(tag);
        wrap.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView version = new TextView(this);
        version.setText("v" + Brand.VERSION);
        version.setTextSize(10);
        version.setLetterSpacing(0.1f);
        version.setTextColor(theme.accent);
        version.setPadding(dp(10), dp(6), dp(10), dp(6));
        version.setBackground(rounded(Themes.alpha(theme.accent, 24), 100, 0, 0));
        wrap.addView(version);
        return wrap;
    }

    private View statusChip() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(Themes.alpha(theme.surface, 235), 20, 1, theme.line));

        int active = Prefs.count(prefs);
        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(11);
        dot.setTextColor(active > 0 ? theme.secondary : theme.muted);
        card.addView(dot);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(10), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText(active + " of " + Prefs.FEATURE_KEYS.size() + " rituals armed");
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(theme.text);
        texts.addView(title);
        TextView sub = new TextView(this);
        sub.setText(isInstalled(IG) ? "Instagram detected on this device"
                : getString(R.string.no_instagram));
        sub.setTextSize(11);
        sub.setTextColor(theme.muted);
        sub.setPadding(0, dp(2), 0, 0);
        texts.addView(sub);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView sync = new TextView(this);
        sync.setText("SYNC");
        sync.setTextSize(9);
        sync.setLetterSpacing(0.16f);
        sync.setTextColor(theme.bg);
        sync.setPadding(dp(12), dp(7), dp(12), dp(7));
        sync.setBackground(rounded(theme.accent, 100, 0, 0));
        sync.setOnClickListener(v -> {
            Haptics.heavy(this);
            Prefs.writeMirror(this);
            show(TAB_HOME);
            Toast.makeText(this, "Settings re-read by the hook", Toast.LENGTH_SHORT).show();
        });
        card.addView(sync);

        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setPadding(0, dp(14), 0, 0);
        holder.addView(card);
        return holder;
    }

    private View sectionLabel(String title) {
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextSize(10);
        label.setLetterSpacing(0.22f);
        label.setTextColor(theme.muted);
        label.setPadding(dp(4), dp(22), 0, dp(8));
        return label;
    }

    private View toggleRow(final String key, String title, String desc, String glyph) {
        final boolean on = Prefs.get(prefs, key, false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(14), dp(14));
        row.setBackground(rounded(Themes.alpha(theme.surface, 226), 20, 1, theme.line));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, 0);

        TextView icon = new TextView(this);
        icon.setText(glyph);
        icon.setTextSize(16);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(on ? theme.accent : theme.muted);
        icon.setBackground(rounded(Themes.alpha(on ? theme.accent : theme.line, 34), 14, 0, 0));
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(14), 0, dp(10), 0);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(15);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(theme.text);
        text.addView(titleView);
        TextView descView = new TextView(this);
        descView.setText(desc);
        descView.setTextSize(11.5f);
        descView.setTextColor(theme.muted);
        descView.setPadding(0, dp(2), 0, 0);
        text.addView(descView);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));

        final SwitchView toggle = new SwitchView(this, on, theme);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(48), dp(28)));

        row.setOnClickListener(v -> {
            boolean next = !Prefs.get(prefs, key, false);
            Prefs.commit(MainActivity.this, key, next);
            Haptics.tick(this);
            toggle.animateTo(next);
            icon.setTextColor(next ? theme.accent : theme.muted);
            icon.setBackground(rounded(Themes.alpha(next ? theme.accent : theme.line, 34), 14, 0, 0));
            });
        toggle.setOnClickListener(v -> row.performClick());
        row.setLayoutParams(lp);
        return row;
    }

    private View actionRow(String title, String desc, String glyph, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        row.setBackground(rounded(Themes.alpha(theme.surface, 200), 18, 1, theme.line));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, 0);

        TextView icon = new TextView(this);
        icon.setText(glyph);
        icon.setTextSize(14);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(theme.secondary);
        row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(8), 0, 0, 0);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTextColor(theme.text);
        text.addView(titleView);
        if (desc != null && !desc.isEmpty()) {
            TextView descView = new TextView(this);
            descView.setText(desc);
            descView.setTextSize(11);
            descView.setSingleLine(true);
            descView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            descView.setTextColor(theme.muted);
            descView.setPadding(0, dp(1), 0, 0);
            text.addView(descView);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextSize(18);
        chevron.setTextColor(theme.muted);
        row.addView(chevron);

        row.setOnClickListener(v -> {
            Haptics.tick(this);
            click.onClick(v);
        });
        row.setLayoutParams(lp);
        return row;
    }

    private View infoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(11), dp(16), dp(11));
        TextView key = new TextView(this);
        key.setText(label);
        key.setTextSize(12);
        key.setTextColor(theme.muted);
        row.addView(key, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView val = new TextView(this);
        val.setText(value);
        val.setTextSize(12);
        val.setTextColor(theme.text);
        row.addView(val);
        return row;
    }

    private View stateRow(String label, boolean ok) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(9);
        dot.setTextColor(ok ? theme.secondary : theme.muted);
        row.addView(dot);
        TextView key = new TextView(this);
        key.setText(label);
        key.setTextSize(12);
        key.setTextColor(theme.text);
        key.setPadding(dp(10), 0, 0, 0);
        row.addView(key, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView val = new TextView(this);
        val.setText(ok ? "ready" : "missing");
        val.setTextSize(11);
        val.setTextColor(ok ? theme.secondary : theme.muted);
        row.addView(val);
        return row;
    }

    private View themeCard(Themes.Palette palette, boolean selected) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(palette.surface, 20, selected ? 2 : 1,
                selected ? theme.accent : palette.line));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, 0);

        View swatch = new View(this);
        GradientDrawable sw = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{palette.accent, palette.secondary, palette.bg});
        sw.setCornerRadius(dp(14));
        swatch.setBackground(sw);
        card.addView(swatch, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(14), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(palette.label);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(palette.text);
        text.addView(name);
        TextView meta = new TextView(this);
        meta.setText(palette.id.toUpperCase() + (palette.glow ? " · GLOW" : " · MATTE"));
        meta.setTextSize(9.5f);
        meta.setLetterSpacing(0.14f);
        meta.setTextColor(palette.muted);
        meta.setPadding(0, dp(3), 0, 0);
        text.addView(meta);
        card.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView check = new TextView(this);
        check.setText(selected ? "◉" : "○");
        check.setTextSize(18);
        check.setTextColor(selected ? theme.accent : palette.muted);
        card.addView(check);

        card.setLayoutParams(lp);
        card.setOnClickListener(v -> {
            Prefs.commit(MainActivity.this, Prefs.THEME, Themes.indexOf(palette.id));
            Haptics.tick(this);
            reloadTheme();
        });
        return card;
    }

    private View swatchRow(String label, int current, int[] colors, final boolean primary) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(8), 0, dp(4));
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(10);
        title.setLetterSpacing(0.18f);
        title.setTextColor(theme.muted);
        wrap.addView(title);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        for (final int color : colors) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(color);
            g.setStroke(dp(color == current ? 3 : 1), color == current ? theme.text : theme.line);
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.setMargins(0, 0, dp(12), 0);
            dot.setOnClickListener(v -> {
                Prefs.commit(MainActivity.this, primary ? Prefs.ACCENT : Prefs.SECONDARY, color);
                Haptics.tick(this);
                reloadTheme();
            });
            row.addView(dot, lp);
        }
        scroll.addView(row);
        wrap.addView(scroll);
        return wrap;
    }

    private View statCard(String value, String label, int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(rounded(Themes.alpha(theme.surface, 226), 20, 1, theme.line));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(24);
        v.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        v.setTextColor(color);
        card.addView(v);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(9.5f);
        l.setLetterSpacing(0.2f);
        l.setTextColor(theme.muted);
        l.setPadding(0, dp(3), 0, 0);
        card.addView(l);
        return card;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(0, 0, dp(10), 0);
        return lp;
    }

    private View pageTitle(String title, String sub) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(18), 0, dp(4));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(24);
        t.setLetterSpacing(-0.02f);
        t.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        t.setTextColor(theme.text);
        wrap.addView(t);
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextSize(12);
        s.setTextColor(theme.muted);
        s.setPadding(0, dp(6), 0, 0);
        wrap.addView(s);
        return wrap;
    }

    private View footer() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(0, dp(26), 0, dp(4));
        if (!TextUtils.isEmpty(Brand.SIGNATURE)) {
            TextView sig = new TextView(this);
            sig.setText(Brand.SIGNATURE);
            sig.setTextSize(11);
            sig.setTextColor(Themes.alpha(theme.accent, 190));
            sig.setGravity(Gravity.CENTER);
            wrap.addView(sig);
        }
        TextView meta = new TextView(this);
        meta.setText(Brand.NAME + " · " + Brand.FLAVOR + " · " + Brand.CHANNEL);
        meta.setTextSize(9.5f);
        meta.setLetterSpacing(0.08f);
        meta.setTextColor(theme.muted);
        meta.setGravity(Gravity.CENTER);
        meta.setPadding(0, dp(6), 0, 0);
        wrap.addView(meta);
        return wrap;
    }

    // ------------------------------------------------------------ drawing
    private GradientDrawable rounded(int fill, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    // ------------------------------------------------------------ actions
    private void reloadTheme() {
        theme = Themes.current(this);
        styleSystemBars();
        buildShell();
        show(TAB_THEME);
        Prefs.writeMirror(this);
    }

    private void launchInstagram() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(IG);
        if (launch == null) {
            Toast.makeText(this, R.string.no_instagram, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(launch);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_instagram, Toast.LENGTH_LONG).show();
        }
    }

    private void showSetup() {
        Dialog dialog = new Dialog(this, R.style.Nyx_Dialog);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(24), dp(24), dp(24), dp(18));
        TextView title = new TextView(this);
        title.setText("ATTACH " + Brand.NAME.toUpperCase());
        title.setTextSize(16);
        title.setLetterSpacing(0.08f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(theme.text);
        wrap.addView(title);
        String[] steps = {
                "1 · Rooted device: install LSPosed (Zygisk or KernelSU build) and reboot.",
                "2 · Install " + Brand.NAME + ".apk, open LSPosed → Modules → " + Brand.NAME + " → enable it.",
                "3 · In scope, tick only Instagram (com.instagram.android).",
                "4 · Force-stop Instagram, then reopen it. The dock appears after the first feed load.",
                "5 · No root? Use LSPatch in local mode and embed this APK as a module.",
                "6 · Change any toggle here and tap SYNC — the hook re-reads instantly."
        };
        for (String step : steps) {
            TextView line = new TextView(this);
            line.setText(step);
            line.setTextSize(12.5f);
            line.setTextColor(theme.muted);
            line.setLineSpacing(dp(3), 1f);
            line.setPadding(0, dp(10), 0, 0);
            wrap.addView(line);
        }
        TextView close = new TextView(this);
        close.setText("GOT IT");
        close.setTextSize(11);
        close.setLetterSpacing(0.16f);
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, dp(14), 0, dp(14));
        close.setTextColor(theme.bg);
        close.setBackground(rounded(theme.accent, 14, 0, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(20), 0, 0);
        close.setLayoutParams(lp);
        close.setOnClickListener(v -> dialog.dismiss());
        wrap.addView(close);

        dialog.setContentView(wrap);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void showReleaseNotes() {
        Dialog dialog = new Dialog(this, R.style.Nyx_Dialog);
        ScrollView scroll = new ScrollView(this);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(24), dp(24), dp(24), dp(20));
        TextView title = new TextView(this);
        title.setText("RELEASE NOTES");
        title.setTextSize(16);
        title.setLetterSpacing(0.08f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(theme.text);
        wrap.addView(title);
        String[] notes = {
                "v" + Brand.VERSION + " · " + Brand.CHANNEL,
                "• Faster attach: hooks the tab host the moment it is created.",
                "• Motion-aware floating dock — hides while scrolling when Zen is on.",
                "• Media keeper now streams over TLS and keeps the original quality.",
                "• Story keeper works in highlights and the story viewer.",
                "• Activity-status and typing-presence blockers.",
                "• Theme engine: five palettes, live accent lab.",
                "• Vault cleaner with per-file reporting."
        };
        for (String note : notes) {
            TextView line = new TextView(this);
            line.setText(note);
            line.setTextSize(12.5f);
            line.setTextColor(theme.muted);
            line.setPadding(0, dp(9), 0, 0);
            line.setLineSpacing(dp(2), 1f);
            wrap.addView(line);
        }
        scroll.addView(wrap);
        dialog.setContentView(scroll);
        dialog.show();
    }

    private void confirmCleaner() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Purge vault?");
        b.setMessage("This deletes every file in the " + MediaStoreX.folderName(this) + " folder.");
        b.setPositiveButton("Purge", (d, w) -> {
            File folder = MediaStoreX.folder(this);
            int[] count = {0};
            File[] files = folder.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.delete()) count[0]++;
                }
            }
            Toast.makeText(this, "Deleted " + count[0] + " files", Toast.LENGTH_SHORT).show();
            show(TAB_MEDIA);
        });
        b.setNegativeButton("Keep", null);
        AlertDialog dialog = b.show();
        styleDialog(dialog);
    }

    private void confirmWipe() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Wipe module data?");
        b.setMessage("Every toggle, theme and the folder name return to build defaults.");
        b.setPositiveButton("Wipe", (d, w) -> {
            prefs.edit().clear().commit();
            Prefs.seedIfEmpty(this);
            theme = Themes.current(this);
            styleSystemBars();
            buildShell();
            show(TAB_HOME);
            Toast.makeText(this, "Module state wiped", Toast.LENGTH_SHORT).show();
        });
        b.setNegativeButton("Cancel", null);
        styleDialog(b.show());
    }

    private void askFolder() {
        Dialog dialog = new Dialog(this, R.style.Nyx_Dialog);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(24), dp(24), dp(24), dp(18));
        TextView title = new TextView(this);
        title.setText("VAULT FOLDER");
        title.setTextSize(14);
        title.setLetterSpacing(0.1f);
        title.setTextColor(theme.text);
        wrap.addView(title);
        final EditText input = new EditText(this);
        input.setText(MediaStoreX.folderName(this));
        input.setSingleLine(true);
        input.setTextColor(theme.text);
        input.setHintTextColor(theme.muted);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setPadding(0, dp(12), 0, dp(12));
        wrap.addView(input);
        TextView save = new TextView(this);
        save.setText("SAVE");
        save.setGravity(Gravity.CENTER);
        save.setTextSize(11);
        save.setLetterSpacing(0.16f);
        save.setPadding(0, dp(14), 0, dp(14));
        save.setTextColor(theme.bg);
        save.setBackground(rounded(theme.accent, 14, 0, 0));
        save.setOnClickListener(v -> {
            String value = input.getText().toString().trim().replaceAll("[^A-Za-z0-9 _-]", "");
            if (value.isEmpty()) value = GenSettings.FOLDER;
            Prefs.commit(MainActivity.this, Prefs.FOLDER, value);
            dialog.dismiss();
            show(TAB_MEDIA);
            Toast.makeText(this, "Vault → " + value, Toast.LENGTH_SHORT).show();
        });
        wrap.addView(save);
        dialog.setContentView(wrap);
        dialog.show();
    }

    private void openGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            startActivity(intent);
        } catch (Throwable t) {
            try {
                Intent files = new Intent("android.intent.action.GET_CONTENT");
                files.setType("*/*");
                startActivity(files);
            } catch (Throwable ignored) {
                Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void exportSettings() {
        StringBuilder json = new StringBuilder("{\n");
        json.append("  \"module\": \"").append(Brand.NAME).append("\",\n");
        json.append("  \"version\": \"").append(Brand.VERSION).append("\",\n");
        json.append("  \"theme\": \"").append(theme.id).append("\",\n");
        json.append("  \"accent\": \"").append(Themes.hex(theme.accent)).append("\",\n");
        json.append("  \"secondary\": \"").append(Themes.hex(theme.secondary)).append("\",\n");
        json.append("  \"folder\": \"").append(MediaStoreX.folderName(this)).append("\",\n");
        List<String> flags = new ArrayList<>();
        for (String key : Prefs.FEATURE_KEYS) {
            flags.add("\"" + key + "\": " + Prefs.get(prefs, key, false));
        }
        json.append("  \"features\": {").append(TextUtils.join(", ", flags)).append("}\n}");
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("nyx-settings", json));
        Toast.makeText(this, "Settings JSON copied", Toast.LENGTH_SHORT).show();
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show();
        }
    }

    private void styleDialog(AlertDialog dialog) {
        try {
            dialog.getWindow().setBackgroundDrawable(rounded(0xFF101321, 24, 1, theme.line));
            ((TextView) dialog.findViewById(android.R.id.message)).setTextColor(theme.muted);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------- probes
    private boolean isInstalled(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean hookBridgePresent() {
        try {
            Class.forName("de.robv.android.xposed.XposedBridge");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
