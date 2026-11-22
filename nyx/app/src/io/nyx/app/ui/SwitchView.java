package io.nyx.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import io.nyx.app.core.Themes;

/**
 * Two things the console needs and the framework lacks in a dark aesthetic:
 * a soft aurora backdrop that tints to the active palette, and a custom
 * animated switch (the platform switch cannot be recoloured reliably).
 */
final class AuroraView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path glowPath = new Path();
    private final Themes.Palette palette;

    AuroraView(Context context, Themes.Palette palette) {
        super(context);
        this.palette = palette;
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        // A zero-sized view makes RadialGradient throw IllegalArgumentException —
        // this is the classic "crash on first frame" and it must never happen.
        if (w <= 0 || h <= 0) return;
        canvas.drawColor(palette.bg);

        paint.reset();
        paint.setAntiAlias(true);
        paint.setShader(new RadialGradient(
                w * 0.82f, h * 0.02f, Math.max(w, h) * 0.62f,
                new int[]{Themes.alpha(palette.accent, palette.glow ? 64 : 26), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setShader(new RadialGradient(
                w * 0.06f, h * 0.62f, Math.max(w, h) * 0.55f,
                new int[]{Themes.alpha(palette.secondary, palette.glow ? 34 : 16), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        // faint horizon grid: 24 columns, 2px gaps
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(Themes.alpha(palette.line, palette.glow ? 60 : 40));
        float step = getResources().getDisplayMetrics().density * 26;
        for (float x = 0; x < w; x += step) canvas.drawLine(x, 0, x, h, paint);
        for (float y = 0; y < h; y += step) canvas.drawLine(0, y, w, y, paint);
    }
}

/** Animated pill switch, themed from the palette. */
final class SwitchView extends TextView {

    private final Themes.Palette palette;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress;

    SwitchView(Context context, boolean on, Themes.Palette palette) {
        super(context);
        this.palette = palette;
        this.progress = on ? 1f : 0f;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setClickable(true);
    }

    void animateTo(boolean on) {
        setProgress(on ? 1f : 0f, 180);
    }

    private void setProgress(float target, long ms) {
        final float start = progress;
        final long end = System.currentTimeMillis() + ms;
        final Runnable[] step = new Runnable[1];
        step[0] = () -> {
            float t = Math.min(1f, (ms - (end - System.currentTimeMillis())) / (float) ms);
            if (t < 0) t = 0;
            progress = start + (target - start) * t;
            invalidate();
            if (t < 1f) postDelayed(step[0], 16);
        };
        step[0].run();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;

        int offTrack = Themes.alpha(palette.line, 255);
        int onTrack = palette.accent;
        float p = Math.max(0f, Math.min(1f, progress));
        track.setColor(Themes.blend(offTrack, onTrack, p));
        canvas.drawRoundRect(0, 0, w, h, r, r, track);

        if (p > 0.01f) {
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(getResources().getDisplayMetrics().density * 1.4f);
            track.setColor(Themes.alpha(palette.accent, (int) (110 * p)));
            canvas.drawRoundRect(0.7f, 0.7f, w - 0.7f, h - 0.7f, r, r, track);
            track.setStyle(Paint.Style.FILL);
        }

        float inset = h * 0.14f;
        float kr = r - inset;
        if (kr <= 0f) return; // collapsed layout: nothing sensible to draw
        float cx = inset + kr + (w - (inset + kr) * 2f) * p;
        knob.setColor(p > 0.5f ? palette.bg : palette.muted);
        knob.setShadowLayer(kr * 0.7f, 0f, kr * 0.15f, Themes.alpha(Color.BLACK, 120));
        canvas.drawCircle(cx, h / 2f, kr, knob);
    }
}
