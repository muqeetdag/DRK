package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * TextView with real text measurement: font metrics, letter spacing, wrapping,
 * ellipsis and gravity. Layout is measured the way Android does it, so heights
 * and offsets in the render match a device closely.
 */
public class TextView extends View {

    private CharSequence text = "";
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private float textSize = 16f * android.test.Env.densityDpi / 160f;
    private int textColor = 0xFF000000;
    private int gravity = Gravity.TOP | Gravity.START;
    private int maxLines = Integer.MAX_VALUE;
    private float lineSpacingExtra;
    private float lineSpacingMultiplier = 1f;
    private TextUtils.TruncateAt ellipsize;
    private boolean singleLine;
    private int inputType;
    private CharSequence hint;
    private int hintColor = 0xFF888888;
    private int compoundDrawablePadding;
    private boolean allCaps;
    private Layout.Alignment alignment;
    private int lines = 1;

    public TextView(Context context) {
        super(context);
        paint.setColor(textColor);
        paint.setTextSize(textSize);
    }

    public TextView(Context context, android.util.AttributeSet attrs) { this(context); }

    // ------------------------------------------------------------------ text
    public void setText(CharSequence text) {
        this.text = text == null ? "" : text;
        requestLayout();
        invalidate();
    }

    public void setText(int resid) {
        setText(android.content.ResBridge.string(resid));
    }

    public void setText(CharSequence text, BufferType type) { setText(text); }

    public enum BufferType { NORMAL, SPANNABLE, EDITABLE }

    public CharSequence getText() { return text; }

    public int length() { return text.length(); }

    public void append(CharSequence extra) { setText(text.toString() + extra); }

    public void setHint(CharSequence hint) { this.hint = hint; }

    public void setHintTextColor(int color) { hintColor = color; }

    public void setHintTextColor(android.content.res.ColorStateList colors) {
        if (colors != null) hintColor = colors.getDefaultColor();
    }

    public CharSequence getHint() { return hint; }

    public void setTextColor(int color) {
        textColor = color;
        paint.setColor(color);
        invalidate();
    }

    public void setTextColor(android.content.res.ColorStateList colors) {
        if (colors != null) setTextColor(colors.getDefaultColor());
    }

    public final int getCurrentTextColor() { return textColor; }

    public void setTextSize(float sizeSp) { setTextSize(2, sizeSp); }

    public void setTextSize(int unit, float size) {
        float density = android.test.Env.densityDpi / 160f;
        textSize = unit == 2 ? size * density : size * density;
        paint.setTextSize(textSize);
        requestLayout();
        invalidate();
    }

    public float getTextSize() { return textSize; }

    public TextPaint getPaint() { return paint; }

    public void setTypeface(Typeface tf) {
        paint.setTypeface(tf);
        requestLayout();
        invalidate();
    }

    public void setTypeface(Typeface tf, int style) {
        setTypeface(Typeface.create(tf, style));
    }

    public Typeface getTypeface() { return paint.getTypeface(); }

    public void setLetterSpacing(float spacing) {
        paint.setLetterSpacing(spacing);
        requestLayout();
        invalidate();
    }

    public float getLetterSpacing() { return paint.getLetterSpacing(); }

    public void setLineSpacing(float add, float mult) {
        lineSpacingExtra = add;
        lineSpacingMultiplier = mult;
        requestLayout();
    }

    public void setAllCaps(boolean caps) { allCaps = caps; }

    public void setGravity(int gravity) {
        this.gravity = gravity;
        invalidate();
    }

    public int getGravity() { return gravity; }

    public void setMaxLines(int maxLines) { this.maxLines = maxLines; requestLayout(); }

    public int getMaxLines() { return maxLines; }

    public void setLines(int lines) { this.lines = lines; requestLayout(); }

    public void setSingleLine() { singleLine = true; maxLines = 1; }

    public void setSingleLine(boolean single) { if (single) setSingleLine(); }

    public void setEllipsize(TextUtils.TruncateAt where) { ellipsize = where; invalidate(); }

    public void setTextAlignment(int alignment) {}

    public void setInputType(int type) { inputType = type; }

    public void setRawInputType(int type) { inputType = type; }

    public void setImeOptions(int options) {}

    public void setShadowLayer(float radius, float dx, float dy, int color) {
        paint.setShadowLayer(radius, dx, dy, color);
    }

    public void setPaintFlags(int flags) {}

    public void setCompoundDrawablesWithIntrinsicBounds(int l, int t, int r, int b) {}

    public void setCompoundDrawablesWithIntrinsicBounds(android.graphics.drawable.Drawable l,
                                                        android.graphics.drawable.Drawable t,
                                                        android.graphics.drawable.Drawable r,
                                                        android.graphics.drawable.Drawable b) {}

    public void setCompoundDrawablePadding(int pad) { compoundDrawablePadding = pad; }

    public void setIncludeFontPadding(boolean include) {}

    public void setBreakStrategy(int strategy) {}

    public void setHyphenationFrequency(int frequency) {}

    public void setTextScaleX(float scale) { paint.setTextScaleX(scale); }

    public void setFreezesText(boolean freeze) {}

    public void setSelectAllOnFocus(boolean select) {}

    public void setMovementMethod(Object method) {}

    public void setTransformationMethod(Object method) {}

    public void setMarqueeRepeatLimit(int limit) {}

    public void setHorizontallyScrolling(boolean scrolling) {}

    public void setEms(int ems) {}

    public void setMinEms(int ems) {}

    public void setMaxEms(int ems) {}

    // -------------------------------------------------------------- measuring
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int wMode = MeasureSpec.getMode(widthMeasureSpec);
        int wSize = MeasureSpec.getSize(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int hSize = MeasureSpec.getSize(heightMeasureSpec);

        int availableWidth = wMode == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE
                : Math.max(0, wSize - getPaddingLeft() - getPaddingRight());

        String content = displayText();
        List<String> lines = wrap(content, availableWidth);
        if (singleLine || maxLines == 1) lines = lines.subList(0, Math.min(1, lines.size()));

        float lineHeight = paint.font().getSize2D() * 1.15f * lineSpacingMultiplier
                + lineSpacingExtra;
        int height = (int) Math.ceil(lineHeight * Math.max(1, lines.size()))
                + getPaddingTop() + getPaddingBottom();

        int width;
        if (wMode == MeasureSpec.EXACTLY) {
            width = wSize;
        } else {
            float widest = 0f;
            for (String line : lines) widest = Math.max(widest, paint.measureText(line));
            width = (int) Math.ceil(widest) + getPaddingLeft() + getPaddingRight();
            if (wMode == MeasureSpec.AT_MOST) width = Math.min(width, wSize);
        }
        if (hMode == MeasureSpec.EXACTLY) height = hSize;
        else if (hMode == MeasureSpec.AT_MOST) height = Math.min(height, hSize);
        setMeasuredDimension(width, height);
    }

    protected void setMeasuredDimension(int w, int h) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }

    private String displayText() {
        String content = text == null ? "" : text.toString();
        if (allCaps) content = content.toUpperCase();
        return content;
    }

    /** Greedy word wrap; a single long word is broken at the character level. */
    private List<String> wrap(String content, int availableWidth) {
        List<String> lines = new ArrayList<>();
        if (content.isEmpty()) {
            lines.add("");
            return lines;
        }
        String[] paragraphs = content.split("\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            String[] words = paragraph.split(" ");
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                String candidate = current.length() == 0 ? words[i] : current + " " + words[i];
                if (availableWidth == Integer.MAX_VALUE
                        || paint.measureText(candidate) <= availableWidth
                        || current.length() == 0) {
                    if (current.length() != 0
                            && paint.measureText(candidate) > availableWidth
                            && availableWidth > 0) {
                        // Long single word: break it.
                        String word = words[i];
                        while (paint.measureText(word) > availableWidth && word.length() > 1) {
                            int cut = word.length();
                            while (cut > 1 && paint.measureText(word.substring(0, cut)) > availableWidth) {
                                cut--;
                            }
                            lines.add(word.substring(0, cut));
                            word = word.substring(cut);
                        }
                        current.setLength(0);
                        current.append(word);
                    } else {
                        current.setLength(0);
                        current.append(candidate);
                    }
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(words[i]);
                }
            }
            if (current.length() > 0) lines.add(current.toString());
        }
        if (lines.isEmpty()) lines.add("");
        if (ellipsize != null && lines.size() > maxLines) {
            List<String> kept = new ArrayList<>(lines.subList(0, Math.max(1, maxLines)));
            String last = kept.get(kept.size() - 1);
            while (!last.isEmpty() && paint.measureText(last + "…") > availableWidth) {
                last = last.substring(0, last.length() - 1);
            }
            kept.set(kept.size() - 1, last + "…");
            lines = kept;
        }
        return lines;
    }

    // ---------------------------------------------------------------- drawing
    @Override
    protected void onDraw(Canvas canvas) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        String content = displayText();
        if (content.isEmpty() && hint != null) content = hint.toString();
        if (content.isEmpty()) return;

        int available = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        List<String> lines = wrap(content, available);
        if (singleLine || maxLines == 1) lines = lines.subList(0, Math.min(1, lines.size()));

        float lineHeight = paint.font().getSize2D() * 1.15f * lineSpacingMultiplier
                + lineSpacingExtra;
        int contentHeight = (int) (lineHeight * lines.size());
        int top = getPaddingTop();
        if ((gravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
            top = getHeight() - getPaddingBottom() - contentHeight;
        } else if ((gravity & Gravity.CENTER_VERTICAL) != 0) {
            top = (getHeight() - contentHeight) / 2;
        }
        top += getPaddingTop() * 0;

        paint.setColor(text == null || text.length() == 0 && hint != null ? hintColor : textColor);
        // Baseline sits ~ 0.8*lineHeight into the line box.
        float baseline = top + paint.font().getSize2D() * 0.98f;

        for (String line : lines) {
            float x = getPaddingLeft();
            int horizontal = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
            float width = paint.measureText(line);
            if (horizontal == Gravity.CENTER_HORIZONTAL) {
                x = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight() - width) / 2f;
            } else if (horizontal == Gravity.RIGHT) {
                x = getWidth() - getPaddingRight() - width;
            }
            canvas.drawText(line, x, baseline, paint);
            baseline += lineHeight;
        }
    }

    @Override
    public String toString() { return "TextView\"" + text + "\""; }
}
