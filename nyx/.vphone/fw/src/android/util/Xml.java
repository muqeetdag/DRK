package android.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

/** Reads SharedPreferences XML written by either Android or this emulator. */
public class Xml {

    public final Map<String, Object> values = new LinkedHashMap<>();

    public void parse(File file) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        Element root = doc.getDocumentElement();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element)) continue;
            Element el = (Element) node;
            String tag = el.getTagName();
            String name = el.getAttribute("name");
            if (name.isEmpty()) continue;
            switch (tag) {
                case "boolean":
                    values.put(name, Boolean.parseBoolean(el.getAttribute("value")));
                    break;
                case "int":
                    values.put(name, parseInt(el.getAttribute("value"), 0));
                    break;
                case "long":
                    values.put(name, (long) parseLong(el.getAttribute("value"), 0L));
                    break;
                case "float":
                    values.put(name, parseFloat(el.getAttribute("value")));
                    break;
                case "string":
                    values.put(name, el.getTextContent());
                    break;
                default:
                    break;
            }
        }
    }

    private static int parseInt(String raw, int def) {
        try { return Integer.parseInt(raw.trim()); } catch (Throwable t) { return def; }
    }

    private static long parseLong(String raw, long def) {
        try { return Long.parseLong(raw.trim()); } catch (Throwable t) { return def; }
    }

    private static float parseFloat(String raw) {
        try { return Float.parseFloat(raw.trim()); } catch (Throwable t) { return 0f; }
    }
}
