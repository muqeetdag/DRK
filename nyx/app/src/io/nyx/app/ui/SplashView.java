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

import io.nyx.app.core.Brand;
import io.nyx.app.core.Themes;

/**
 * Cold-start brand moment: a ring draws itself behind the wordmark, then the
 * console replaces it. Drawn on a canvas so it needs no animation XML.
 */
final class SplashView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ring = new Path();
    private final long born = System.currentTimeMillis();
    private final float density;

    SplashView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        Themes.Palette palette = Themes.current(context);
        text.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(palette.text);
        text.setTextSize(34 * density);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.6f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(palette.accent);
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float t = Math.min(1f, (System.currentTimeMillis() - born) / 900f);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return; // zero-size frame guard (see AuroraView)
        Themes.Palette palette = Themes.current(getContext());

        canvas.drawColor(palette.bg);
        paint.setShader(new RadialGradient(w * 0.5f, h * 0.42f, Math.max(w, h) * 0.46f,
                new int[]{Themes.alpha(palette.accent, 60), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        float radius = 52 * density;
        float arc = 330f * ease(t);
        paint.setStyle(Paint.Style.STROKE);
        canvas.save();
        canvas.translate(w / 2f, h * 0.42f);
        canvas.rotate(-96f);
        android.graphics.RectF box = new android.graphics.RectF(-radius, -radius, radius, radius);
        canvas.drawArc(box, 0f, arc, false, paint);
        paint.setColor(palette.secondary);
        paint.setStrokeWidth(1.4f * density);
        canvas.drawArc(box, 180f, arc * 0.55f, false, paint);
        canvas.restore();

        text.setAlpha((int) (255 * Math.min(1f, t * 1.6f)));
        canvas.drawText(Brand.NAME, w / 2f, h * 0.42f + 12 * density, text);
        text.setTextSize(10 * density);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setAlpha((int) (200 * Math.min(1f, t * 1.4f)));
        canvas.drawText(Brand.TAGLINE.toUpperCase(), w / 2f, h * 0.42f + 44 * density, text);
        if (t < 1f) postInvalidateOnAnimation();
    }

    private static float ease(float t) {
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }
}
