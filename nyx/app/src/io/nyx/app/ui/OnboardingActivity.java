package io.nyx.app.ui;

import io.nyx.app.core.SafeUi;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import io.nyx.app.R;
import io.nyx.app.core.Brand;
import io.nyx.app.core.SafePrefs;
import io.nyx.app.core.Themes;

/**
 * First-run chooser. The app is a single APK for both worlds, but the setup
 * steps differ, so we ask once: rooted (LSPosed) or non-root (LSPatch).
 * Also carries the developer credit.
 */
public class OnboardingActivity extends Activity {

    private Themes.Palette theme;
    private int density;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            density = (int) getResources().getDisplayMetrics().density;
            theme = Themes.current(this);
            SafeUi.statusBar(getWindow(), theme.bg);
            setContentView(build());
        } catch (Throwable t) {
            // last-resort: never let onboarding itself crash
            Intent next = new Intent(this, MainActivity.class);
            next.putExtra(MainActivity.EXTRA_SKIP_ONBOARDING, true);
            startActivity(next);
            finish();
        }
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                theme.bg, Themes.blend(theme.bg, theme.accent, 0.08f), theme.bg}));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setFitsSystemWindows(true);
        body.setPadding(dp(26), dp(48), dp(26), dp(36));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        TextView kicker = new TextView(this);
        kicker.setText("WELCOME TO");
        kicker.setTextSize(10);
        SafeUi.letterSpacing(kicker, 0.3f);
        kicker.setTextColor(theme.accent);
        body.addView(kicker);

        TextView title = new TextView(this);
        title.setText(Brand.NAME);
        title.setTextSize(44);
        SafeUi.letterSpacing(title, -0.05f);
        SafeUi.typeface(title, "sans-serif-black", Typeface.BOLD);
        title.setTextColor(theme.text);
        title.setPadding(0, dp(4), 0, 0);
        body.addView(title);

        TextView tagline = new TextView(this);
        tagline.setText(Brand.TAGLINE);
        tagline.setTextSize(14);
        tagline.setTextColor(theme.muted);
        tagline.setPadding(0, dp(8), 0, 0);
        body.addView(tagline);

        TextView question = new TextView(this);
        question.setText("How is this device set up?");
        question.setTextSize(17);
        question.setTypeface(Typeface.DEFAULT_BOLD);
        question.setTextColor(theme.text);
        question.setPadding(0, dp(34), 0, dp(4));
        body.addView(question);

        TextView hint = new TextView(this);
        hint.setText("Both modes use the same APK — only the attach steps differ.");
        hint.setTextSize(12);
        hint.setTextColor(theme.muted);
        hint.setPadding(0, dp(4), 0, dp(14));
        body.addView(hint);

        body.addView(modeCard(
                "ROOTED · LSPOSED",
                "Magisk / KernelSU / APatch device. Hooks attach to Instagram directly.",
                "◈", new int[]{theme.accent, theme.secondary}, SafePrefs.MODE_ROOT));

        body.addView(modeCard(
                "NON-ROOT · LSPATCH",
                "No root. You patch Instagram once with LSPatch (local mode) and embed this module.",
                "◇", new int[]{theme.secondary, theme.accent}, SafePrefs.MODE_NON_ROOT));

        body.addView(credit());

        return root;
    }

    private View modeCard(String title, String body, String glyph, int[] colors, final int mode) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        bg.setCornerRadius(dp(24));
        bg.setAlpha(30);
        GradientDrawable wrapper = new GradientDrawable();
        wrapper.setCornerRadius(dp(24));
        wrapper.setColor(theme.surface);
        wrapper.setStroke(dp(1), theme.line);
        card.setBackground(wrapper);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(10));
        card.setLayoutParams(lp);

        TextView head = new TextView(this);
        head.setText(glyph + "  " + title);
        head.setTextSize(15);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setTextColor(theme.accent);
        card.addView(head);

        TextView desc = new TextView(this);
        desc.setText(body);
        desc.setTextSize(12.5f);
        desc.setTextColor(theme.muted);
        desc.setLineSpacing(dp(3), 1f);
        desc.setPadding(0, dp(8), 0, 0);
        card.addView(desc);

        TextView cta = new TextView(this);
        cta.setText("CONTINUE  →");
        cta.setTextSize(11);
        SafeUi.letterSpacing(cta, 0.16f);
        cta.setGravity(Gravity.CENTER);
        cta.setTypeface(Typeface.DEFAULT_BOLD);
        cta.setPadding(0, dp(12), 0, dp(12));
        cta.setTextColor(theme.bg);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(14));
        pill.setColor(theme.accent);
        cta.setBackground(pill);
        LinearLayout.LayoutParams ctaLp = new LinearLayout.LayoutParams(-1, -2);
        ctaLp.setMargins(0, dp(16), 0, 0);
        cta.setLayoutParams(ctaLp);
        card.addView(cta);

        View.OnClickListener click = v -> pick(mode);
        card.setOnClickListener(click);
        cta.setOnClickListener(click);
        return card;
    }

    private View credit() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(0, dp(30), 0, 0);

        TextView made = new TextView(this);
        made.setText("APP MADE BY");
        made.setTextSize(9.5f);
        SafeUi.letterSpacing(made, 0.24f);
        made.setTextColor(theme.muted);
        made.setGravity(Gravity.CENTER);
        wrap.addView(made);

        TextView handle = new TextView(this);
        handle.setText(TextUtils.isEmpty(Brand.CREDIT_HANDLE) ? "@DRKMUQEETT" : Brand.CREDIT_HANDLE);
        handle.setTextSize(20);
        SafeUi.typeface(handle, "sans-serif-black", Typeface.BOLD);
        handle.setTextColor(theme.text);
        handle.setGravity(Gravity.CENTER);
        handle.setPadding(0, dp(6), 0, 0);
        wrap.addView(handle);

        TextView dev = new TextView(this);
        dev.setText(Brand.CREDIT_DEV);
        dev.setTextSize(11);
        dev.setTextColor(theme.muted);
        dev.setPadding(0, dp(4), 0, 0);
        wrap.addView(dev);

        TextView tg = new TextView(this);
        tg.setText("OPEN TELEGRAM ↗");
        tg.setTextSize(10);
        SafeUi.letterSpacing(tg, 0.14f);
        tg.setGravity(Gravity.CENTER);
        tg.setTextColor(theme.secondary);
        tg.setPadding(dp(16), dp(9), dp(16), dp(9));
        GradientDrawable ring = new GradientDrawable();
        // A pill radius larger than half the height renders as a circle/ellipse on
        // Android; using the measured height keeps it a rounded rectangle.
        ring.setCornerRadius(dp(11));
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(1), theme.line);
        tg.setBackground(ring);
        LinearLayout.LayoutParams tgLp = new LinearLayout.LayoutParams(-2, -2);
        tgLp.setMargins(0, dp(14), 0, 0);
        tg.setLayoutParams(tgLp);
        tg.setOnClickListener(v -> openTelegram());
        wrap.addView(tg);
        return wrap;
    }

    private void pick(int mode) {
        try {
            SafePrefs.set(this, SafePrefs.INSTALL_MODE, mode);
            SafePrefs.set(this, SafePrefs.ONBOARDED, true);
            SafePrefs.set(this, "attach_verified", false);
        } catch (Throwable ignored) {
        }
        Intent next = new Intent(this, MainActivity.class);
        next.putExtra(MainActivity.EXTRA_FROM_ONBOARDING, true);
        startActivity(next);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void openTelegram() {
        String url = TextUtils.isEmpty(Brand.TELEGRAM) ? "https://t.me/DRKMUQEETT" : Brand.TELEGRAM;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No Telegram app or browser found", Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // stay on the chooser: a mode must be picked before the console opens
        moveTaskToBack(true);
    }
}
