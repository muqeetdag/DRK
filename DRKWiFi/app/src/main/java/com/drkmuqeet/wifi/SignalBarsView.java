package com.drkmuqeet.wifi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/** Four-bar signal meter. Level 0 renders the meter unlit. */
public class SignalBarsView extends View {

    private static final int BARS = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int litColor;
    private final int dimColor;
    private int level;

    public SignalBarsView(Context context) {
        this(context, null);
    }

    public SignalBarsView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalBarsView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        litColor = ContextCompat.getColor(context, R.color.lime);
        dimColor = ContextCompat.getColor(context, R.color.track);
    }

    public void setLevel(int value) {
        int clamped = Math.max(0, Math.min(BARS, value));
        if (clamped != level) {
            level = clamped;
            invalidate();
        }
    }

    public void setQuality(int quality) {
        setLevel(WifiMath.bars(quality));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        float gap = width * 0.16f;
        float barWidth = (width - gap * (BARS - 1)) / BARS;
        float radius = barWidth / 2f;

        for (int i = 0; i < BARS; i++) {
            float fraction = 0.30f + 0.2334f * i;
            float barHeight = height * fraction;
            float left = i * (barWidth + gap);
            paint.setColor(i < level ? litColor : dimColor);
            canvas.drawRoundRect(left, height - barHeight, left + barWidth, height, radius, radius, paint);
        }
    }
}
