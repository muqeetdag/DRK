package com.drkmuqeet.wifi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * 2.4 GHz channel occupancy. Channels 1, 6 and 11 are drawn in the accent colour because
 * they are the three that do not overlap each other.
 */
public class ChannelChartView extends View {

    private static final int FIRST_CHANNEL = 1;
    private static final int LAST_CHANNEL = 13;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private int[] counts = new int[15];

    public ChannelChartView(Context context) {
        this(context, null);
    }

    public ChannelChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChannelChartView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        density = getResources().getDisplayMetrics().density;

        countPaint.setColor(ContextCompat.getColor(context, R.color.text));
        countPaint.setTextSize(sp(9));
        countPaint.setTextAlign(Paint.Align.CENTER);
        countPaint.setTypeface(Typeface.MONOSPACE);

        labelPaint.setColor(ContextCompat.getColor(context, R.color.muted));
        labelPaint.setTextSize(sp(9));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        rulePaint.setColor(ContextCompat.getColor(context, R.color.line));
        rulePaint.setStrokeWidth(density);
    }

    public void setCounts(int[] values) {
        int[] next = new int[15];
        if (values != null) {
            System.arraycopy(values, 0, next, 0, Math.min(values.length, next.length));
        }
        counts = next;
        invalidate();
    }

    private float sp(int value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        int slots = LAST_CHANNEL - FIRST_CHANNEL + 1;
        float labelBand = sp(12);
        float countBand = sp(12);
        float baseline = height - labelBand;
        float plotTop = countBand;
        float plotHeight = baseline - plotTop;
        if (plotHeight <= 0) {
            return;
        }

        canvas.drawLine(0, baseline, width, baseline, rulePaint);

        int peak = 1;
        for (int i = FIRST_CHANNEL; i <= LAST_CHANNEL; i++) {
            peak = Math.max(peak, counts[i]);
        }

        float slot = width / slots;
        float barWidth = Math.min(slot * 0.62f, 26f * density);

        for (int i = 0; i < slots; i++) {
            int channel = FIRST_CHANNEL + i;
            int count = counts[channel];
            float centre = slot * i + slot / 2f;

            labelPaint.setColor(channel == 1 || channel == 6 || channel == 11
                    ? ContextCompat.getColor(getContext(), R.color.lime)
                    : ContextCompat.getColor(getContext(), R.color.muted));
            canvas.drawText(String.valueOf(channel), centre, height - sp(2), labelPaint);

            if (count <= 0) {
                continue;
            }

            float barHeight = Math.max(3f * density, plotHeight * (count / (float) peak));
            boolean clearChannel = channel == 1 || channel == 6 || channel == 11;
            barPaint.setColor(ContextCompat.getColor(getContext(),
                    clearChannel ? R.color.lime : R.color.track));
            float left = centre - barWidth / 2f;
            canvas.drawRoundRect(left, baseline - barHeight, left + barWidth, baseline,
                    barWidth / 3f, barWidth / 3f, barPaint);

            canvas.drawText(String.valueOf(count), centre, baseline - barHeight - sp(3), countPaint);
        }
    }
}
