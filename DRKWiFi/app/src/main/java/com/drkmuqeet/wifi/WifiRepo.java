package com.drkmuqeet.wifi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The command layer. Everything is read through su, parsed here, and handed to the UI as
 * plain objects. Commands are fixed strings; the only interpolated value anywhere in this
 * class is a network id that has already been parsed into an {@code int}.
 */
final class WifiRepo {

    private static final long T_PROBE = 60000;
    private static final long T_READ = 20000;
    private static final long T_SCAN = 30000;
    private static final long T_ACTION = 20000;

    private static final String IFACE = "wlan0";

    /**
     * One su round trip for the whole status read, split into marked sections.
     * Reading {@code dumpsys connectivity} is filtered at the source so the app never
     * pulls a megabyte of dump text across the pipe.
     */
    private static final String STATUS_CMD =
            "iw dev " + IFACE + " link 2>/dev/null; echo '#IW'; "
                    + "dumpsys wifi 2>/dev/null | grep -m1 'mWifiInfo SSID:'; echo '#DUMPSYS'; "
                    + "ip -4 addr show " + IFACE + " 2>/dev/null; echo '#IP'; "
                    + "ip route 2>/dev/null; echo '#ROUTE'; "
                    + "settings get global wifi_on; echo '#RADIO'; "
                    + "iw dev " + IFACE + " get power_save 2>/dev/null; echo '#PS'; "
                    + "cat /sys/class/net/" + IFACE + "/address 2>/dev/null; echo '#MAC'; "
                    + "dumpsys connectivity 2>/dev/null | grep -m1 'DnsAddresses'; echo '#DNS'";

    private static final Pattern SECTION = Pattern.compile("#[A-Z]+");
    private static final Pattern IW_BSSID = Pattern.compile("Connected to ([0-9a-fA-F:]{17})");
    private static final Pattern IW_SSID = Pattern.compile("^\\s*SSID: (.+)$");
    private static final Pattern IW_FREQ = Pattern.compile("^\\s*freq: (\\d+)");
    private static final Pattern IW_SIGNAL = Pattern.compile("^\\s*signal: (-?\\d+)");
    private static final Pattern IW_BITRATE = Pattern.compile("^\\s*tx bitrate: ([\\d.]+)");
    private static final Pattern IP_INET = Pattern.compile("inet (\\d+\\.\\d+\\.\\d+\\.\\d+)");
    private static final Pattern ROUTE_DEFAULT = Pattern.compile("^default via (\\d+\\.\\d+\\.\\d+\\.\\d+) dev " + IFACE);
    private static final Pattern DNS_LIST = Pattern.compile("DnsAddresses: \\[([^\\]]*)\\]");
    private static final Pattern PS_STATE = Pattern.compile("Power save: (\\w+)");

    /**
     * The SSID is matched only when quoted, because "BSSID: " contains the substring
     * "SSID: " and an unanchored pattern would happily read a MAC address as a name.
     */
    private static final Pattern CM_SCAN = Pattern.compile(
            "^SSID: \"(.*?)\", BSSID: ([0-9a-fA-F:]{17}).*?level: (-?\\d+).*?frequency: (\\d+)");
    private static final Pattern CM_CAPS = Pattern.compile("capabilities: \\[([^\\]]*)\\]");
    private static final Pattern CM_SAVED = Pattern.compile("^Network Id: (\\d+), SSID: \"(.*)\"");

    private WifiRepo() {
    }

    // ---------------------------------------------------------------- capability

    /** Long timeout: the first call is the one Magisk shows its grant prompt for. */
    static boolean hasRoot() {
        RootShell.Result r = RootShell.root("id", T_PROBE);
        return r.out.contains("uid=0");
    }

    // ---------------------------------------------------------------- status

    static WifiStatus status() {
        return parseStatus(RootShell.root(STATUS_CMD, T_READ).out);
    }

    /** Pure: raw marked output in, status out. No process is spawned, which is what
     *  makes the whole status parser reachable from a unit test. */
    static WifiStatus parseStatus(String raw) {
        WifiStatus s = new WifiStatus();
        Map<String, String> section = sections(raw);

        String radio = section.getOrDefault("RADIO", "").trim();
        s.radioOn = "1".equals(radio);

        String mac = section.getOrDefault("MAC", "").trim();
        if (!mac.isEmpty()) {
            s.mac = mac;
        }

        parseIwLink(s, section.getOrDefault("IW", ""));
        parseDumpsys(s, section.getOrDefault("DUMPSYS", ""));

        Matcher ip = IP_INET.matcher(section.getOrDefault("IP", ""));
        s.ip = ip.find() ? ip.group(1) : "";

        Matcher route = ROUTE_DEFAULT.matcher(section.getOrDefault("ROUTE", ""));
        s.gateway = route.find() ? route.group(1) : "";

        // DNS is only reported when Wi-Fi owns the default route, so a cellular resolver
        // can never appear inside a Wi-Fi readout.
        if (!s.gateway.isEmpty()) {
            Matcher dns = DNS_LIST.matcher(section.getOrDefault("DNS", ""));
            if (dns.find()) {
                s.dns = dns.group(1).replace(" ", "");
            }
        }

        Matcher ps = PS_STATE.matcher(section.getOrDefault("PS", ""));
        if (ps.find()) {
            s.powerSaveKnown = true;
            s.powerSave = "on".equals(ps.group(1));
        }

        s.channel = WifiMath.channelOf(s.frequency);
        return s;
    }

    static void parseIwLink(WifiStatus s, String iw) {
        Matcher bssid = IW_BSSID.matcher(iw);
        if (bssid.find()) {
            s.hasLink = true;
            s.bssid = bssid.group(1);
        }
        for (String line : iw.split("\n")) {
            Matcher m = IW_SSID.matcher(line);
            if (m.find()) {
                s.ssid = m.group(1).trim();
                continue;
            }
            m = IW_FREQ.matcher(line);
            if (m.find()) {
                s.frequency = parseInt(m.group(1));
                continue;
            }
            m = IW_SIGNAL.matcher(line);
            if (m.find()) {
                s.rssi = parseInt(m.group(1));
                continue;
            }
            m = IW_BITRATE.matcher(line);
            if (m.find()) {
                s.linkSpeedMbps = (int) parseDouble(m.group(1));
            }
        }
    }

    /** Fills anything {@code iw} could not provide. Keys are split on ", " first. */
    static void parseDumpsys(WifiStatus s, String dumpsys) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String part : dumpsys.split(", ")) {
            int colon = part.indexOf(": ");
            if (colon > 0) {
                fields.put(part.substring(0, colon).trim(), part.substring(colon + 2).trim());
            }
        }
        if (fields.isEmpty()) {
            return;
        }

        if (s.ssid.isEmpty()) {
            s.ssid = stripQuotes(fields.getOrDefault("SSID", ""));
        }
        if (s.bssid.isEmpty()) {
            s.bssid = stripQuotes(fields.getOrDefault("BSSID", ""));
        }
        s.state = fields.getOrDefault("Supplicant state", "");
        if (s.mac.isEmpty()) {
            s.mac = stripQuotes(fields.getOrDefault("MAC", ""));
        }
        s.security = securityName(fields.getOrDefault("Security type", ""));
        if (s.rssi == Integer.MIN_VALUE) {
            s.rssi = parseInt(fields.getOrDefault("RSSI", ""));
        }
        if (s.frequency == 0) {
            s.frequency = digits(fields.getOrDefault("Frequency", ""));
        }
        if (s.linkSpeedMbps == 0) {
            s.linkSpeedMbps = digits(fields.getOrDefault("Link speed", ""));
        }
        if (s.ip.isEmpty()) {
            String ip = stripQuotes(fields.getOrDefault("IP", "")).replace("/", "");
            if (!ip.isEmpty() && !"0.0.0.0".equals(ip)) {
                s.ip = ip;
            }
        }
    }

    /**
     * {@code Security type} is a supplicant enum, not a string: 0 open, 1 WEP,
     * 2 WPA/WPA2 personal, 3 802.1X, 4 SAE, 5 OWE.
     */
    private static String securityName(String raw) {
        switch (digits(raw)) {
            case 0: return "Open";
            case 1: return "WEP";
            case 2: return "WPA/WPA2-PSK";
            case 3: return "802.1X/EAP";
            case 4: return "WPA3-SAE";
            case 5: return "OWE";
            case 8: return "OWE-Transition";
            default: return raw.isEmpty() || "-1".equals(raw) ? "" : "type " + raw;
        }
    }

    // ---------------------------------------------------------------- scan

    static List<AccessPoint> scan() {
        RootShell.Result cm = RootShell.root(
                "cmd wifi start-scan >/dev/null 2>&1; sleep 1; cmd wifi list-scan-results 2>/dev/null",
                T_SCAN);
        List<AccessPoint> found = parseCmScan(cm.out);
        if (found.isEmpty()) {
            // ROMs without the cmd wifi surface, or a scan that reported nothing, get the
            // nl80211 path instead. Root can read it; a shell cannot.
            found = parseIwScan(RootShell.root("iw dev " + IFACE + " scan 2>/dev/null", T_SCAN).out);
        }
        return strongestPerSsid(found);
    }

    static List<AccessPoint> parseCmScan(String raw) {
        List<AccessPoint> out = new ArrayList<>();
        for (String line : raw.split("\n")) {
            Matcher m = CM_SCAN.matcher(line);
            if (!m.find()) {
                continue;
            }
            String ssid = m.group(1).trim();
            if (ssid.isEmpty() || "<unknown ssid>".equals(ssid)) {
                continue;
            }
            Matcher caps = CM_CAPS.matcher(line);
            out.add(new AccessPoint(ssid, m.group(2), securityFromCaps(caps.find() ? caps.group(1) : ""),
                    parseInt(m.group(3)), parseInt(m.group(4))));
        }
        return out;
    }

    /** Walks nl80211 scan output: one "BSS" block per access point. */
    static List<AccessPoint> parseIwScan(String raw) {
        List<AccessPoint> out = new ArrayList<>();
        String bssid = "";
        String ssid = "";
        String caps = "";
        int freq = 0;
        int signal = Integer.MIN_VALUE;

        for (String line : raw.split("\n")) {
            if (line.startsWith("BSS ")) {
                if (!ssid.isEmpty()) {
                    out.add(new AccessPoint(ssid, bssid, "", signal, freq));
                }
                ssid = "";
                caps = "";
                freq = 0;
                signal = Integer.MIN_VALUE;
                Matcher m = Pattern.compile("^BSS ([0-9a-fA-F:]{17})").matcher(line);
                bssid = m.find() ? m.group(1) : "";
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("SSID: ")) {
                ssid = trimmed.substring(6).trim();
            } else if (trimmed.startsWith("freq: ")) {
                freq = parseInt(trimmed.substring(6));
            } else if (trimmed.startsWith("signal: ")) {
                signal = (int) parseDouble(trimmed.substring(8).replace("dBm", "").trim());
            } else if (trimmed.startsWith("capability: ") || trimmed.startsWith("RSN:") || trimmed.startsWith("WPA:")) {
                caps += trimmed + " ";
            }
        }
        if (!ssid.isEmpty() && !"<unknown ssid>".equals(ssid)) {
            out.add(new AccessPoint(ssid, bssid, securityFromCaps(caps), signal, freq));
        }
        return out;
    }

    /**
     * Collapses roaming duplicates into one row per network name, keeping whichever BSSID
     * answers strongest, then sorts by signal.
     */
    static List<AccessPoint> strongestPerSsid(List<AccessPoint> in) {
        Map<String, AccessPoint> best = new LinkedHashMap<>();
        for (AccessPoint ap : in) {
            String key = ap.ssid.toLowerCase(Locale.ROOT);
            AccessPoint seen = best.get(key);
            if (seen == null || ap.rssi > seen.rssi) {
                best.put(key, ap);
            }
        }
        List<AccessPoint> out = new ArrayList<>(best.values());
        out.sort(Comparator.comparingInt((AccessPoint ap) -> ap.rssi).reversed());
        return out;
    }

    private static String securityFromCaps(String caps) {
        String c = caps.toUpperCase(Locale.ROOT);
        if (c.contains("SAE")) {
            return "WPA3";
        }
        if (c.contains("OWE")) {
            return "OWE";
        }
        // nl80211 blocks announce WPA2 as an RSN block; the literal string "WPA2" never
        // appears there, so a WEP check running first would mislabel every WPA2 network.
        if (c.contains("WPA2") || c.contains("RSN")) {
            return "WPA2";
        }
        if (c.contains("WPA")) {
            return "WPA";
        }
        if (c.contains("WEP") || c.contains("PRIVACY")) {
            return "WEP";
        }
        return "";
    }

    // ---------------------------------------------------------------- saved networks

    static List<SavedNetwork> saved() {
        return parseSaved(RootShell.root("cmd wifi list-networks 2>/dev/null", T_READ).out);
    }

    static List<SavedNetwork> parseSaved(String raw) {
        List<SavedNetwork> out = new ArrayList<>();
        for (String line : raw.split("\n")) {
            Matcher m = CM_SAVED.matcher(line.trim());
            if (m.find()) {
                out.add(new SavedNetwork(parseInt(m.group(1)), m.group(2)));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- actions

    static RootShell.Result setRadio(boolean on) {
        RootShell.Result r = RootShell.root("svc wifi " + (on ? "enable" : "disable"), T_ACTION);
        RootShell.Result verify = RootShell.root("settings get global wifi_on", T_READ);
        boolean nowOn = "1".equals(verify.out.trim());
        if (nowOn != on) {
            // Some ROMs only honour the newer surface; ask it before reporting failure.
            r = RootShell.root("cmd wifi set-wifi-enabled " + (on ? "enabled" : "disabled"), T_ACTION);
        }
        return r;
    }

    static RootShell.Result forget(int networkId) {
        return RootShell.root("cmd wifi forget-network " + networkId, T_ACTION);
    }

    static RootShell.Result setPowerSave(boolean on) {
        return RootShell.root("iw dev " + IFACE + " set power_save " + (on ? "on" : "off"), T_ACTION);
    }

    /** Every 2.4 GHz channel count, indexed by channel number, for the load chart. */
    static int[] channelLoad(List<AccessPoint> aps) {
        int[] counts = new int[15];
        for (AccessPoint ap : aps) {
            if (ap.frequency >= 2400 && ap.frequency < 2500) {
                int ch = WifiMath.channelOf(ap.frequency);
                if (ch >= 1 && ch <= 14) {
                    counts[ch]++;
                }
            }
        }
        return counts;
    }

    static int countOn5Ghz(List<AccessPoint> aps) {
        int n = 0;
        for (AccessPoint ap : aps) {
            if (ap.frequency >= 5000) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- parsing helpers

    /** Splits marked output into sections: each "#TAG" line starts the next one. */
    static Map<String, String> sections(String out) {
        Map<String, String> map = new LinkedHashMap<>();
        String key = "head";
        StringBuilder sb = new StringBuilder();
        for (String line : out.split("\n")) {
            if (SECTION.matcher(line.trim()).matches()) {
                map.put(key, sb.toString());
                key = line.trim().substring(1);
                sb = new StringBuilder();
            } else {
                sb.append(line).append('\n');
            }
        }
        map.put(key, sb.toString());
        return map;
    }

    private static String stripQuotes(String s) {
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s == null ? "" : s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s == null ? "" : s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Keeps the digits of a value like "433Mbps" or "5745MHz". */
    private static int digits(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        return parseInt(sb.toString());
    }
}
