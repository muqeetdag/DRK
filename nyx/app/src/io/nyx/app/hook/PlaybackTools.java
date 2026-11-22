package io.nyx.app.hook;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Reels and story playback: speed, seek bar, auto-scroll, scroll lock and
 * hold-to-slow-motion.
 *
 * Instagram plays media through MediaPlayer/ExoPlayer instances held by the
 * player views. We hook the setter calls that the app makes right after
 * preparing a clip, so we never hold our own copy of the player and never fight
 * the app for state.
 */
final class PlaybackTools {

    /** 0..4 → 0.5x, 1x, 1.5x, 2x, 3x */
    private static final float[] SPEEDS = {0.5f, 1f, 1.5f, 2f, 3f};
    private static final java.util.Map<Object, Boolean> ATTACHED = new WeakHashMap<>();

    private PlaybackTools() {}

    static void install(ClassLoader loader) {
        applySpeed(loader);
        forceSeekBar(loader);
        autoScroll(loader);
        scrollLock(loader);
        holdSlowMotion(loader);
    }

    // ------------------------------------------------------------ playback speed
    private static void applySpeed(ClassLoader loader) {
        int index = clamp(PrefsReader.getInt(PrefsKeys.SPEED_VALUE, 1), 0, SPEEDS.length - 1);
        final float speed = SPEEDS[index];
        if (speed == 1f) return;

        // Both player families expose setPlaybackSpeed / setSpeed.
        for (String cls : new String[]{
                "android.media.MediaPlayer",
                "com.google.android.exoplayer2.ExoPlayer",
                "com.google.android.exoplayer2.SimpleExoPlayer"}) {
            Class<?> type;
            try {
                type = Class.forName(cls, false, loader);
            } catch (Throwable ignored) {
                continue;
            }
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                if (!"setPlaybackSpeed".equals(name) && !"setSpeed".equals(name)
                        && !"setPlaybackParameters".equals(name)) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args == null || param.args.length == 0) return;
                                float wanted = PrefsReader.getInt(PrefsKeys.SPEED_VALUE, 1) == 1
                                        ? 1f : speed;
                                if (param.args[0] instanceof Float) {
                                    param.args[0] = wanted;
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }
        HookBridge.log("playback: speed → " + speed + "x");
    }

    // ------------------------------------------------------ always show seekbar
    private static void forceSeekBar(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.ALWAYS_SEEKBAR, false)) return;
        hookPlayerView(loader, "always seekbar", view -> {
            // Views expose the controls through a "show/hide" pair; calling show
            // on each attach is enough and harmless if it blinks.
            callNoArg(view, "showControls");
            callNoArg(view, "setAlwaysShowControls");
        });
    }

    // ------------------------------------------------------------- auto scroll
    private static void autoScroll(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.REELS_AUTOSCROLL, false)) return;
        hookPlayerView(loader, "auto scroll", view -> {
            // A finished clip asks the pager to advance; the app does this itself
            // when the "loop" flag is off, so we make that flag read false.
            callNoArg(view, "setLooping", false);
        });
    }

    // -------------------------------------------------------------- scroll lock
    private static void scrollLock(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.REELS_SCROLL_LOCK, false)) return;
        hookPlayerView(loader, "scroll lock", view -> {
            View cursor = view;
            for (int i = 0; i < 8 && cursor != null; i++) {
                try {
                    if (cursor instanceof ViewGroup) {
                        ViewGroup group = (ViewGroup) cursor;
                        for (int c = 0; c < group.getChildCount(); c++) {
                            installTapGuard(group.getChildAt(c));
                        }
                    }
                } catch (Throwable ignored) {
                }
                Object parent = cursor.getParent();
                cursor = parent instanceof View ? (View) parent : null;
            }
        });
    }

    /**
     * Swallows vertical flings on the pager while still allowing taps: the touch
     * never reaches the view pager, so no accidental swipe changes the reel.
     */
    private static void installTapGuard(View view) {
        if (view == null || Boolean.TRUE.equals(ATTACHED.get(view))) return;
        ATTACHED.put(view, Boolean.TRUE);
        final float[] down = new float[2];
        view.setOnTouchListener((v, event) -> {
            try {
                if (!PrefsReader.get(PrefsKeys.REELS_SCROLL_LOCK, false)) return false;
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    down[0] = event.getX();
                    down[1] = event.getY();
                    return false;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float dy = Math.abs(event.getY() - down[1]);
                    float dx = Math.abs(event.getX() - down[0]);
                    if (dy > 24 && dy > dx) return true; // eat the swipe
                }
            } catch (Throwable ignored) {
            }
            return false;
        });
    }

    // --------------------------------------------------------- hold slow motion
    private static void holdSlowMotion(ClassLoader loader) {
        if (!PrefsReader.get(PrefsKeys.HOLD_SLOWMO, false)) return;
        hookPlayerView(loader, "hold slow motion", view -> {
            final android.view.View target = view;
            final float[] before = new float[1];
            target.setOnLongClickListener(v -> {
                try {
                    before[0] = PrefsReader.getInt(PrefsKeys.SPEED_VALUE, 1) == 1
                            ? 1f : SPEEDS[clamp(PrefsReader.getInt(PrefsKeys.SPEED_VALUE, 1),
                            0, SPEEDS.length - 1)];
                    setSpeedOn(target, 0.5f);
                } catch (Throwable ignored) {
                }
                return true;
            });
            target.setOnTouchListener((v2, event) -> {
                try {
                    if (event.getActionMasked() == MotionEvent.ACTION_UP
                            || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        setSpeedOn(v2, before[0] == 0f ? 1f : before[0]);
                    }
                } catch (Throwable ignored) {
                }
                return false;
            });
        });
    }

    private static void setSpeedOn(View view, float speed) {
        try {
            Object player = readField(view, "mPlayer");
            if (player == null) return;
            for (String name : new String[]{"setPlaybackSpeed", "setSpeed"}) {
                try {
                    player.getClass().getMethod(name, float.class).invoke(player, speed);
                    return;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object readField(Object owner, String name) {
        for (Class<?> cursor = owner.getClass(); cursor != null; cursor = cursor.getSuperclass()) {
            try {
                java.lang.reflect.Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ utils
    private interface ViewAction {
        void run(View view);
    }

    /** Runs an action on every Instagram player view that is attached. */
    private static void hookPlayerView(ClassLoader loader, String label, ViewAction action) {
        hookAttach(loader, action);
        HookBridge.log("playback: " + label + " armed");
    }

    private static void hookAttach(ClassLoader loader, ViewAction action) {
        try {
            XposedHelpers.findAndHookMethod(
                    Class.forName("android.view.View", false, loader),
                    "onAttachedToWindow", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                View view = (View) param.thisObject;
                                if (view == null) return;
                                String name = view.getClass().getName().toLowerCase();
                                if (!name.contains("player") && !name.contains("video")
                                        && !name.contains("clips")) return;
                                action.run(view);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable t) {
            HookBridge.warn("player attach", t);
        }
    }

    private static void callNoArg(Object target, String methodName) {
        callNoArg(target, methodName, null);
    }

    private static void callNoArg(Object target, String methodName, Object argument) {
        try {
            if (target == null) return;
            if (argument == null) {
                target.getClass().getMethod(methodName).invoke(target);
            } else if (argument instanceof Boolean) {
                target.getClass().getMethod(methodName, boolean.class)
                        .invoke(target, ((Boolean) argument).booleanValue());
            } else {
                target.getClass().getMethod(methodName, argument.getClass()).invoke(target, argument);
            }
        } catch (Throwable ignored) {
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
