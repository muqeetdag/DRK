package android.net;

import java.io.File;

/** URI with the builders the app needs. */
public class Uri {

    private final String raw;

    private Uri(String raw) { this.raw = raw; }

    public static Uri parse(String uriString) {
        return new Uri(uriString == null ? "" : uriString);
    }

    public static Uri fromFile(File file) {
        return new Uri("file://" + file.getAbsolutePath());
    }

    public static Uri fromParts(String scheme, String ssp, String fragment) {
        return new Uri(scheme + ":" + ssp + (fragment == null ? "" : "#" + fragment));
    }

    public static Uri withAppendedPath(Uri baseUri, String pathSegment) {
        return new Uri(baseUri.raw + "/" + pathSegment);
    }

    public String toString() { return raw; }

    public String getScheme() {
        int index = raw.indexOf(':');
        return index < 0 ? null : raw.substring(0, index);
    }

    public String getHost() {
        String rest = raw;
        int scheme = rest.indexOf("://");
        if (scheme >= 0) rest = rest.substring(scheme + 3);
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    public String getPath() {
        int scheme = raw.indexOf("://");
        if (scheme < 0) return raw;
        int slash = raw.indexOf('/', scheme + 3);
        return slash < 0 ? "" : raw.substring(slash);
    }

    public String getLastPathSegment() {
        String path = getPath();
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    public String getQueryParameter(String key) {
        int q = raw.indexOf('?');
        if (q < 0) return null;
        for (String pair : raw.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) return pair.substring(eq + 1);
        }
        return null;
    }

    public Builder buildUpon() { return new Builder(raw); }

    public static class Builder {
        private String raw;
        public Builder() { raw = ""; }
        public Builder(String raw) { this.raw = raw; }
        public Builder scheme(String scheme) { raw = scheme + ":" + raw.replaceFirst("^[a-z]+:", ""); return this; }
        public Builder authority(String authority) { raw = raw + "//" + authority; return this; }
        public Builder path(String path) { raw = raw + path; return this; }
        public Builder appendPath(String segment) { raw = raw + "/" + segment; return this; }
        public Builder appendQueryParameter(String key, String value) {
            raw = raw + (raw.contains("?") ? "&" : "?") + key + "=" + value;
            return this;
        }
        public Uri build() { return new Uri(raw); }
    }
}
