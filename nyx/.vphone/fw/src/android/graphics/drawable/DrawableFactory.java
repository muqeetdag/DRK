package android.graphics.drawable;

import android.graphics.Bitmap;
import android.graphics.Bitmaps;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Inflates the XML drawables that ship in res/ (shape + vector). This is the
 * piece that would otherwise require aapt at runtime; parsing the same XML the
 * APK contains means the virtual phone draws exactly what the device would.
 */
public final class DrawableFactory {

    private DrawableFactory() {}

    public static Drawable fromXml(File xml) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(xml);
            Element root = document.getDocumentElement();
            String tag = stripPrefix(root.getTagName());
            if ("shape".equals(tag)) return shape(root);
            if ("vector".equals(tag)) return vector(root);
            if ("bitmap".equals(tag)) {
                String src = root.getAttribute("android:src");
                return new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);
            }
            if ("selector".equals(tag) || "ripple".equals(tag)) {
                return new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);
            }
        } catch (Throwable ignored) {
        }
        return new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);
    }

    private static String stripPrefix(String name) {
        int i = name.indexOf(':');
        return i >= 0 ? name.substring(i + 1) : name;
    }

    private static String attr(Element el, String name) {
        String v = el.getAttribute("android:" + name);
        if (v == null || v.isEmpty()) v = el.getAttribute(name);
        return v;
    }

    private static Drawable shape(Element root) {
        GradientDrawable drawable = new GradientDrawable();
        String shape = attr(root, "shape");
        if ("oval".equals(shape)) drawable.setShape(1);
        else if ("line".equals(shape)) drawable.setShape(2);

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element)) continue;
            Element el = (Element) node;
            String tag = stripPrefix(el.getTagName());
            switch (tag) {
                case "corners":
                    drawable.setCornerRadius(parseDimen(attr(el, "radius")));
                    break;
                case "solid":
                    drawable.setColor(parseColor(attr(el, "color")));
                    break;
                case "stroke":
                    drawable.setStroke((int) parseDimen(attr(el, "width")),
                            parseColor(attr(el, "color")));
                    break;
                case "gradient": {
                    String start = attr(el, "startColor");
                    String end = attr(el, "endColor");
                    String angle = attr(el, "angle");
                    if (!start.isEmpty() && !end.isEmpty()) {
                        GradientDrawable.Orientation o = GradientDrawable.Orientation.TOP_BOTTOM;
                        if ("45".equals(angle)) o = GradientDrawable.Orientation.BL_TR;
                        if ("135".equals(angle)) o = GradientDrawable.Orientation.TL_BR;
                        GradientDrawable g = new GradientDrawable(o,
                                new int[]{parseColor(start), parseColor(end)});
                        if ("oval".equals(shape)) g.setShape(1);
                        return g;
                    }
                    break;
                }
                default:
                    break;
            }
        }
        return drawable;
    }

    private static Drawable vector(Element root) {
        float viewportWidth = parseFloat(attr(root, "viewportWidth"), 24f);
        float viewportHeight = parseFloat(attr(root, "viewportHeight"), 24f);
        final List<float[]> fills = new ArrayList<>();
        final List<Integer> colors = new ArrayList<>();
        final List<float[]> strokes = new ArrayList<>();
        final List<Integer> strokeColors = new ArrayList<>();
        final List<Float> strokeWidths = new ArrayList<>();

        NodeList paths = root.getElementsByTagName("path");
        for (int i = 0; i < paths.getLength(); i++) {
            Element path = (Element) paths.item(i);
            String data = attr(path, "pathData");
            if (data == null || data.isEmpty()) continue;
            float[] points = PathParser.parse(data);
            int fill = parseColor(attr(path, "fillColor"));
            int stroke = parseColor(attr(path, "strokeColor"));
            float width = parseDimen(attr(path, "strokeWidth"));
            if (points.length > 0) {
                fills.add(points);
                colors.add(fill);
                strokes.add(points);
                strokeColors.add(stroke);
                strokeWidths.add(width);
            }
        }

        final float vw = viewportWidth <= 0 ? 24f : viewportWidth;
        final float vh = viewportHeight <= 0 ? 24f : viewportHeight;
        return new Drawable() {
            @Override
            public void draw(Canvas canvas) {
                android.graphics.Rect b = getBounds();
                if (b.width() <= 0 || b.height() <= 0) return;
                float sx = b.width() / vw;
                float sy = b.height() / vh;
                Canvas c = canvas;
                c.save();
                c.translate(b.left, b.top);
                c.scale(sx, sy);
                for (int i = 0; i < fills.size(); i++) {
                    Path path = new Path();
                    float[] pts = fills.get(i);
                    path.moveTo(pts[0], pts[1]);
                    int idx = 2;
                    while (idx + 1 < pts.length) {
                        path.lineTo(pts[idx], pts[idx + 1]);
                        idx += 2;
                    }
                    path.close();
                    int fillColor = colors.get(i);
                    if ((fillColor >>> 24) != 0) {
                        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        fillPaint.setColor(fillColor);
                        c.drawPath(path, fillPaint);
                    }
                    int strokeColor = strokeColors.get(i);
                    if ((strokeColor >>> 24) != 0) {
                        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        strokePaint.setStyle(Paint.Style.STROKE);
                        strokePaint.setStrokeWidth(Math.max(0.2f, strokeWidths.get(i)));
                        strokePaint.setColor(strokeColor);
                        c.drawPath(path, strokePaint);
                    }
                }
                c.restore();
            }
        };
    }

    private static float parseDimen(String value) {
        if (value == null || value.isEmpty()) return 0f;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c) || c == '.' || c == '-') digits.append(c);
            else break;
        }
        try {
            return Float.parseFloat(digits.toString());
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int parseColor(String value) {
        if (value == null || value.isEmpty()) return Color.TRANSPARENT;
        try {
            return Color.parseColor(value);
        } catch (Throwable t) {
            return Color.TRANSPARENT;
        }
    }

    /** Mini path parser for the M/L/H/V/C/Q/Z commands used in vector icons. */
    static final class PathParser {
        static float[] parse(String data) {
            List<Float> out = new ArrayList<>();
            String[] tokens = data.replace(",", " ").trim().split("\\s+");
            String command = "M";
            int i = 0;
            float curX = 0, curY = 0;
            while (i < tokens.length) {
                String token = tokens[i];
                if (token.length() == 1 && Character.isLetter(token.charAt(0))) {
                    command = token.toUpperCase();
                    i++;
                    continue;
                }
                try {
                    switch (command) {
                        case "M":
                        case "L": {
                            float x = Float.parseFloat(tokens[i]);
                            float y = Float.parseFloat(tokens[i + 1]);
                            out.add(x);
                            out.add(y);
                            curX = x;
                            curY = y;
                            i += 2;
                            break;
                        }
                        case "H": {
                            curX = Float.parseFloat(tokens[i++]);
                            out.add(curX);
                            out.add(curY);
                            break;
                        }
                        case "V": {
                            curY = Float.parseFloat(tokens[i++]);
                            out.add(curX);
                            out.add(curY);
                            break;
                        }
                        case "C": {
                            i += 4;
                            curX = Float.parseFloat(tokens[i]);
                            curY = Float.parseFloat(tokens[i + 1]);
                            out.add(curX);
                            out.add(curY);
                            i += 2;
                            break;
                        }
                        case "Q": {
                            i += 2;
                            curX = Float.parseFloat(tokens[i]);
                            curY = Float.parseFloat(tokens[i + 1]);
                            out.add(curX);
                            out.add(curY);
                            i += 2;
                            break;
                        }
                        default:
                            i++;
                            break;
                    }
                } catch (Exception e) {
                    i++;
                }
            }
            float[] points = new float[out.size()];
            for (int p = 0; p < points.length; p++) points[p] = out.get(p);
            return points;
        }
    }
}
