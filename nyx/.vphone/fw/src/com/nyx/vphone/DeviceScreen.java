package com.nyx.vphone;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The virtual phone's display: a 1080x2400 canvas with a status bar, the
 * activity content, an optional dialog overlay, and a gesture nav bar.
 *
 * Drawing goes through the same android.graphics.Canvas the app uses, so what
 * lands in the PNG is what the view hierarchy actually rendered.
 */
public final class DeviceScreen {

    public static final int WIDTH = 1080;
    public static final int HEIGHT = 2400;
    public static final int STATUS_BAR = 92;
    public static final int NAV_BAR = 84;

    private final BufferedImage framebuffer;
    private final Context context;
    private View content;
    private int statusBarColor = 0xFF06070B;
    private int navigationBarColor = 0xFF06070B;
    private boolean measured;
    private final List<String> overlays = new ArrayList<>();

    public DeviceScreen(Context context) {
        this.context = context;
        this.framebuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        // Attach the live content view immediately. Callers that re-attach after a
        // rebuild still win; this just removes the "empty screenshot" footgun.
        if (context instanceof android.app.Activity) {
            this.content = ((android.app.Activity) context).getContentRoot();
        }
    }

    public void setContent(View view) { this.content = view; }

    public View content() { return content; }

    public void setSystemBarColors(int status, int navigation) {
        statusBarColor = status;
        navigationBarColor = navigation;
    }

    public void addOverlayText(String text) { overlays.add(text); }

    public void clearOverlays() { overlays.clear(); }

    // ------------------------------------------------------------ measure/layout
    /**
     * Android's ViewRootImpl performs repeated traversals until the tree stops
     * asking for layout. Doing the same here is what makes cascaded WRAP_CONTENT
     * sizes settle; a single pass leaves deeply nested containers at 0x0.
     */
    public void measureAndLayout() {
        if (content == null) return;
        int contentHeight = HEIGHT - STATUS_BAR - NAV_BAR;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(contentHeight, View.MeasureSpec.EXACTLY);
        long lastSignature = -1;
        for (int pass = 0; pass < 12; pass++) {
            content.measure(widthSpec, heightSpec);
            content.layout(0, STATUS_BAR, WIDTH, STATUS_BAR + contentHeight);
            if (System.getProperty("vphone.trace") != null) {
                System.out.println("[pass " + pass + "] content="
                        + content.getMeasuredWidth() + "x" + content.getMeasuredHeight()
                        + " children=" + ((ViewGroup) content).getChildCount()
                        + " scroll=" + dumpTree(content));
            }
            long signature = signature(content, 17);
            if (signature == lastSignature) break;
            lastSignature = signature;
        }
        measured = true;
    }

    private String dumpTree(View view) {
        StringBuilder sb = new StringBuilder();
        dumpInto(view, sb);
        sb.append(" sizes=[");
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                sb.append(child.getClass().getSimpleName()).append(':')
                        .append(child.getWidth()).append('x').append(child.getHeight()).append(' ');
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private void dumpInto(View view, StringBuilder sb) {
        sb.append(view.getClass().getSimpleName()).append(' ');
    }

    /** Cheap structural fingerprint used to detect a settled layout. */
    private long signature(View view, int depth) {
        long hash = view.getLeft() * 31L + view.getTop() * 17L
                + view.getWidth() * 13L + view.getHeight() * 7L + depth;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                hash = hash * 31L + signature(group.getChildAt(i), depth + 1);
            }
        }
        return hash;
    }

    public boolean isMeasured() { return measured; }

    // -------------------------------------------------------------------- draw
    public Bitmap render() {
        measureAndLayout();
        Graphics2D g = framebuffer.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(Color.BLACK));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // App content area.
        if (content != null) {
            Canvas canvas = new Canvas(framebuffer);
            content.draw(canvas);
        }

        // Dialog overlay, centred with a scrim.
        Dialog dialog = android.test.Services.visibleDialog();
        if (dialog != null) {
            drawDialog(g, dialog);
        }

        drawStatusBar(g);
        drawNavBar(g);
        drawOverlays(g);
        g.dispose();
        return new Bitmap(framebuffer, false);
    }

    private void drawDialog(Graphics2D g, Dialog dialog) {
        View decor = dialog.getDecorView();
        int dialogWidth = (int) (WIDTH * 0.88f);
        int dialogX = (WIDTH - dialogWidth) / 2;
        decor.measure(View.MeasureSpec.makeMeasureSpec(dialogWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int dialogHeight = Math.min(decor.getMeasuredHeight(), (int) (HEIGHT * 0.7f));
        int dialogY = (int) (HEIGHT * 0.34f);

        g.setColor(new java.awt.Color(0, 0, 0, 150));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        Window window = dialog.getWindow();
        Drawable background = window == null ? null : window.getBackgroundDrawable();
        if (background != null) {
            background.setBounds(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight);
            Canvas canvas = new Canvas(framebuffer);
            background.draw(canvas);
        }
        decor.layout(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight);
        Canvas canvas = new Canvas(framebuffer);
        canvas.save();
        canvas.clipRect(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight);
        decor.draw(canvas);
        canvas.restore();
    }

    private void drawStatusBar(Graphics2D g) {
        g.setColor(new java.awt.Color(statusBarColor, true));
        g.fillRect(0, 0, WIDTH, STATUS_BAR);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        g.setColor(new java.awt.Color(0xFFF4F6FF));
        g.drawString("2:15", 42, 58);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 30));
        // Signal / battery cluster on the right.
        String right = "LTE   58%";
        int width = g.getFontMetrics().stringWidth(right);
        g.drawString(right, WIDTH - width - 46, 58);
        g.fillRoundRect(WIDTH - 40, 38, 16, 30, 6, 6);
        g.setColor(new java.awt.Color(0xFF22D3EE));
        g.fillRoundRect(WIDTH - 37, 44, 10, 20, 4, 4);
    }

    private void drawNavBar(Graphics2D g) {
        g.setColor(new java.awt.Color(navigationBarColor, true));
        g.fillRect(0, HEIGHT - NAV_BAR, WIDTH, HEIGHT);
        g.setColor(new java.awt.Color(0xFFE8ECFF));
        g.setStroke(new java.awt.BasicStroke(7f, java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
        int cy = HEIGHT - NAV_BAR + NAV_BAR / 2;

        // back: a small chevron
        int bx = 120;
        g.drawLine(bx, cy - 18, bx - 20, cy);
        g.drawLine(bx - 20, cy, bx, cy + 18);

        // home: a pill, exactly like Android 13 gesture nav
        g.drawLine(WIDTH / 2 - 44, cy, WIDTH / 2 + 44, cy);

        // recents: two rounded bars
        int rx = WIDTH - 120;
        g.drawRoundRect(rx - 20, cy - 16, 40, 32, 8, 8);
        g.drawLine(rx - 20, cy + 3, rx + 20, cy + 3);
    }

    /** System-bar hit testing: 0 = back, 1 = home, 2 = recents, -1 = none. */
    private int navButtonAt(int x, int y) {
        if (y < HEIGHT - NAV_BAR) return -1;
        if (x < WIDTH / 3) return 0;
        if (x > WIDTH * 2 / 3) return 2;
        return 1;
    }

    private void drawOverlays(Graphics2D g) {
        if (overlays.isEmpty()) return;
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 26));
        int y = HEIGHT - NAV_BAR - 20;
        for (int i = overlays.size() - 1; i >= 0; i--) {
            String line = overlays.get(i);
            g.setColor(new java.awt.Color(0, 0, 0, 170));
            int w = g.getFontMetrics().stringWidth(line);
            g.fillRoundRect(24, y - 30, w + 24, 42, 12, 12);
            g.setColor(new java.awt.Color(0xFFB6FF3D));
            g.drawString(line, 36, y);
            y -= 48;
        }
    }

    // -------------------------------------------------------------------- input
    /** Handles taps on the system navigation bar. */
    public String tapSystem(int x, int y) {
        int button = navButtonAt(x, y);
        if (button < 0) return null;
        if (button == 0) {
            android.app.Activity top = android.test.Services.top();
            if (top != null) top.dispatchBackPressed();
            return "back";
        }
        if (button == 1) {
            android.test.Services.homePressed();
            return "home";
        }
        return "recents";
    }

    /** Dispatches a tap in screen coordinates (status-bar offset handled). */
    public boolean tap(int x, int y) {
        View target = findClickable(content, x, y - STATUS_BAR);
        if (target == null) return false;
        target.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,
                x - target.getLeft(), y - STATUS_BAR - target.getTop()));
        target.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP,
                x - target.getLeft(), y - STATUS_BAR - target.getTop()));
        return true;
    }

    public boolean tapOn(View view) {
        if (view == null) return false;
        int x = (view.getLeft() + view.getRight()) / 2;
        int y = (view.getTop() + view.getBottom()) / 2;
        return tap(x, y);
    }

    private View findClickable(View view, int x, int y) {
        if (view == null || view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                if (x >= child.getLeft() && x < child.getRight()
                        && y >= child.getTop() && y < child.getBottom()) {
                    View nested = findClickable(child, x, y);
                    if (nested != null) return nested;
                }
            }
        }
        if (x >= view.getLeft() && x < view.getRight()
                && y >= view.getTop() && y < view.getBottom() && view.hasOnClickListeners()) {
            return view;
        }
        return null;
    }

    /** Scrolls the first ScrollView found by the given amount. */
    public boolean scrollBy(int dy) {
        View scroll = findScrollView(content);
        if (!(scroll instanceof android.widget.ScrollView)) return false;
        android.widget.ScrollView sv = (android.widget.ScrollView) scroll;
        sv.scrollTo(0, (int) sv.scrollY + dy);
        measureAndLayout();
        return true;
    }

    public View findScrollView(View view) {
        if (view instanceof android.widget.ScrollView) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findScrollView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
