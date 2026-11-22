package io.nyx.app.hook;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Passcode gate for Instagram (and optionally just the DM inbox).
 *
 * A full-screen overlay is added to the activity's content root the moment it
 * resumes, and removed only after the right code is typed. The overlay carries
 * FLAG_SECURE so it cannot be screenshotted, and it is built entirely from
 * framework views - no resources, no theme lookups - so it cannot fail to
 * inflate on an unknown Instagram build.
 */
final class AppLock {

    private static final Map<Activity, Boolean> UNLOCKED = new WeakHashMap<>();

    private AppLock() {}

    static void install(ClassLoader loader) {
        boolean lockAll = PrefsReader.get(PrefsKeys.LOCK_APP, false);
        boolean lockDm = PrefsReader.get(PrefsKeys.LOCK_DM, false);
        if (!lockAll && !lockDm) return;
        if (TextUtils.isEmpty(PrefsReader.get(PrefsKeys.LOCK_CODE, ""))) {
            HookBridge.log("lock: enabled but no passcode set - skipping");
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                    Class.forName("android.app.Activity", false, loader),
                    "onResume", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Activity activity = (Activity) param.thisObject;
                                if (activity == null || !isHost(activity)) return;
                                if (!needsLock(activity)) return;
                                if (Boolean.TRUE.equals(UNLOCKED.get(activity))) return;
                                show(activity);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            HookBridge.log("lock: passcode gate armed");
        } catch (Throwable t) {
            HookBridge.warn("app lock", t);
        }
    }

    private static boolean isHost(Activity activity) {
        try {
            return activity.getClass().getName().startsWith("com.instagram.android");
        } catch (Throwable t) {
            return false;
        }
    }

    /** Lock everything, or only the screens whose name looks like the inbox. */
    private static boolean needsLock(Activity activity) {
        try {
            if (PrefsReader.get(PrefsKeys.LOCK_APP, false)) return true;
            if (!PrefsReader.get(PrefsKeys.LOCK_DM, false)) return false;
            String name = activity.getClass().getSimpleName().toLowerCase();
            return name.contains("direct") || name.contains("inbox")
                    || name.contains("thread");
        } catch (Throwable t) {
            return false;
        }
    }

    private static void show(Activity activity) {
        try {
            View content = activity.findViewById(android.R.id.content);
            if (!(content instanceof ViewGroup)) {
                // Some hosts expose the decor view directly; fall back to it.
                View decor = activity.getWindow() == null ? null
                        : activity.getWindow().getDecorView();
                content = decor;
            }
            if (!(content instanceof ViewGroup)) return;
            ViewGroup root = (ViewGroup) content;
            // Already showing?
            if (root.findViewWithTag("nyx-lock") != null) return;

            Context ctx = activity;
            FrameLayout overlay = new FrameLayout(ctx);
            overlay.setTag("nyx-lock");
            overlay.setBackgroundColor(0xFF06070B);
            overlay.setClickable(true);
            overlay.setFocusable(true);

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            int pad = dp(ctx, 28);
            card.setPadding(pad, pad, pad, pad);

            TextView title = new TextView(ctx);
            title.setText("NYX LOCK");
            title.setTextSize(13);
            title.setLetterSpacing(0.22f);
            title.setTextColor(0xFF8A93AD);
            title.setGravity(Gravity.CENTER);
            card.addView(title);

            TextView handle = new TextView(ctx);
            handle.setText("Enter your passcode");
            handle.setTextSize(22);
            handle.setTypeface(Typeface.DEFAULT_BOLD);
            handle.setTextColor(0xFFF4F6FF);
            handle.setGravity(Gravity.CENTER);
            handle.setPadding(0, dp(ctx, 10), 0, dp(ctx, 22));
            card.addView(handle);

            final EditText input = new EditText(ctx);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            input.setGravity(Gravity.CENTER);
            input.setTextSize(26);
            input.setTextColor(0xFFF4F6FF);
            input.setHintTextColor(0xFF5A6280);
            GradientDrawable field = new GradientDrawable();
            field.setColor(0xFF141826);
            field.setCornerRadius(dp(ctx, 16));
            field.setStroke(dp(ctx, 1), 0xFF2A3350);
            input.setBackground(field);
            input.setPadding(dp(ctx, 18), dp(ctx, 12), dp(ctx, 18), dp(ctx, 12));
            LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            card.addView(input, inputLp);

            final TextView error = new TextView(ctx);
            error.setTextColor(0xFFFF5C7A);
            error.setTextSize(12);
            error.setGravity(Gravity.CENTER);
            error.setPadding(0, dp(ctx, 10), 0, 0);
            card.addView(error);

            final Button unlock = new Button(ctx);
            unlock.setText("UNLOCK");
            unlock.setAllCaps(true);
            GradientDrawable buttonBg = new GradientDrawable();
            buttonBg.setColor(0xFF7C5CFF);
            buttonBg.setCornerRadius(dp(ctx, 14));
            unlock.setBackground(buttonBg);
            unlock.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 50));
            btnLp.setMargins(0, dp(ctx, 18), 0, 0);
            card.addView(unlock, btnLp);

            unlock.setOnClickListener(v -> {
                try {
                    String expected = PrefsReader.get(PrefsKeys.LOCK_CODE, "");
                    String typed = String.valueOf(input.getText()).trim();
                    if (!TextUtils.isEmpty(expected) && expected.equals(typed)) {
                        UNLOCKED.put(activity, Boolean.TRUE);
                        root.removeView(overlay);
                    } else {
                        error.setText("Wrong passcode");
                        input.setText("");
                    }
                } catch (Throwable ignored) {
                }
            });

            overlay.addView(card, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
            root.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            try {
                activity.getWindow().addFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE);
            } catch (Throwable ignored) {
            }
            input.requestFocus();
            HookBridge.log("lock: overlay shown on " + activity.getClass().getSimpleName());
        } catch (Throwable t) {
            HookBridge.warn("lock overlay", t);
        }
    }

    private static int dp(Context ctx, float value) {
        try {
            return (int) (value * ctx.getResources().getDisplayMetrics().density);
        } catch (Throwable t) {
            return (int) value;
        }
    }
}
