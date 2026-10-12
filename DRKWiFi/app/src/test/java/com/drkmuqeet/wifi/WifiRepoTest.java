package com.drkmuqeet.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * Parser tests driven by output captured from real Android builds. None of these touch
 * su, the filesystem or the framework, so they run on the JVM in CI.
 */
public class WifiRepoTest {

    private static final String STATUS_CONNECTED =
            "Connected to 3c:37:86:aa:bb:cc (on wlan0)\n"
                    + "\tSSID: HomeNet_5G\n"
                    + "\tfreq: 5745\n"
                    + "\tsignal: -58 dBm\n"
                    + "\ttx bitrate: 433.3 MBit/s\n"
                    + "#IW\n"
                    + "mWifiInfo SSID: HomeNet_5G, BSSID: 3c:37:86:aa:bb:cc, MAC: 02:00:00:00:00:00, "
                    + "IP: /192.168.1.42, Security type: 2, Supplicant state: COMPLETED, "
                    + "RSSI: -58, Link speed: 433Mbps, Frequency: 5745MHz, Wifi score: 60\n"
                    + "#DUMPSYS\n"
                    + "    inet 192.168.1.42/24 brd 192.168.1.255 scope global wlan0\n"
                    + "#IP\n"
                    + "default via 192.168.1.1 dev wlan0 proto static\n"
                    + "192.168.1.0/24 dev wlan0 proto kernel scope link src 192.168.1.42\n"
                    + "#ROUTE\n"
                    + "1\n"
                    + "#RADIO\n"
                    + "Power save: on\n"
                    + "#PS\n"
                    + "02:00:00:00:00:00\n"
                    + "#MAC\n"
                    + "NetworkAgentInfo DnsAddresses: [8.8.8.8, 1.1.1.1]\n"
                    + "#DNS\n";

    private static final String STATUS_OFF =
            "Not connected\n"
                    + "#IW\n"
                    + "mWifiInfo SSID: <unknown ssid>, BSSID: <none>, MAC: 02:00:00:00:00:00, "
                    + "IP: /0.0.0.0, Security type: 0, Supplicant state: DISCONNECTED, Wifi score: 0\n"
                    + "#DUMPSYS\n"
                    + "#IP\n"
                    + "default via 10.44.1.1 dev rmnet0 proto static\n"
                    + "#ROUTE\n"
                    + "0\n"
                    + "#RADIO\n"
                    + "#PS\n"
                    + "02:00:00:00:00:00\n"
                    + "#MAC\n"
                    + "DnsAddresses: [100.64.0.1]\n"
                    + "#DNS\n";

    @Test
    public void sectionsSplitOnTagsNotOnContent() {
        Map<String, String> section = WifiRepo.sections(STATUS_CONNECTED);
        assertTrue(section.containsKey("IW"));
        assertTrue(section.containsKey("ROUTE"));
        assertEquals("-58", firstSignal(section.get("IW")));
    }

    private String firstSignal(String iw) {
        for (String line : iw.split("\n")) {
            if (line.trim().startsWith("signal: ")) {
                return line.trim().substring(8).replace(" dBm", "");
            }
        }
        return "";
    }

    @Test
    public void connectedStatusReadsEveryField() {
        WifiStatus s = WifiRepo.parseStatus(STATUS_CONNECTED);
        assertTrue(s.radioOn);
        assertTrue(s.hasLink);
        assertTrue(s.connected());
        assertEquals("HomeNet_5G", s.ssid);
        assertEquals("3c:37:86:aa:bb:cc", s.bssid);
        assertEquals("COMPLETED", s.state);
        assertEquals("192.168.1.42", s.ip);
        assertEquals("192.168.1.1", s.gateway);
        assertEquals("8.8.8.8,1.1.1.1", s.dns);
        assertEquals("02:00:00:00:00:00", s.mac);
        assertEquals("WPA/WPA2-PSK", s.security);
        assertEquals(5745, s.frequency);
        assertEquals(-58, s.rssi);
        assertEquals(149, s.channel);
        assertEquals(433, s.linkSpeedMbps);
        assertEquals(84, s.quality());
        assertTrue(s.powerSaveKnown);
        assertTrue(s.powerSave);
    }

    /**
     * The cellular trap: with Wi-Fi off, the phone still has a default route and a DNS
     * server. Neither may appear in a Wi-Fi readout.
     */
    @Test
    public void cellularRouteIsNeverReportedAsWifi() {
        WifiStatus s = WifiRepo.parseStatus(STATUS_OFF);
        assertFalse(s.radioOn);
        assertFalse(s.hasLink);
        assertFalse(s.connected());
        assertEquals("", s.gateway);
        assertEquals("", s.dns);
        assertEquals("", s.ip);
        assertEquals(-1, s.quality());
        assertFalse(s.powerSaveKnown);
    }

    @Test
    public void dumpsysNeverOverwritesValuesIwAlreadyProvided() {
        WifiStatus s = new WifiStatus();
        WifiRepo.parseIwLink(s, STATUS_CONNECTED.split("#IW")[0]);
        assertEquals(-58, s.rssi);
        WifiRepo.parseDumpsys(s, "mWifiInfo SSID: Other, BSSID: 11:22:33:44:55:66, RSSI: -90");
        assertEquals(-58, s.rssi);
        assertEquals("3c:37:86:aa:bb:cc", s.bssid);
    }

    @Test
    public void unknownSsidIsNotAConnection() {
        WifiStatus s = WifiRepo.parseStatus(STATUS_OFF);
        assertFalse(s.connected());
        assertEquals("<unknown ssid>", s.ssid);
    }

    @Test
    public void scanLineIsParsedWithItsQuotedSsid() {
        List<AccessPoint> aps = WifiRepo.parseCmScan(
                "SSID: \"Neighbour_2G\", BSSID: 12:34:56:78:9a:bd, "
                        + "capabilities: [WPA2-PSK-CCMP][ESS], level: -45, frequency: 2437");
        assertEquals(1, aps.size());
        assertEquals("Neighbour_2G", aps.get(0).ssid);
        assertEquals("12:34:56:78:9a:bd", aps.get(0).bssid);
        assertEquals(-45, aps.get(0).rssi);
        assertEquals("WPA2", aps.get(0).security);
        assertEquals(6, WifiMath.channelOf(aps.get(0).frequency));
    }

    /**
     * "BSSID: " contains "SSID: ", so a parser that does not require the opening quote
     * reads the MAC address as the network name. This is the regression guard.
     */
    @Test
    public void bssidIsNeverMistakenForAnSsid() {
        List<AccessPoint> aps = WifiRepo.parseCmScan(
                "BSSID: 12:34:56:78:9a:bd, level: -45, frequency: 2437");
        assertTrue(aps.isEmpty());
    }

    @Test
    public void ssidContainingACommaSurvives() {
        List<AccessPoint> aps = WifiRepo.parseCmScan(
                "SSID: \"Cafe, Bar & Grill\", BSSID: aa:bb:cc:dd:ee:ff, "
                        + "capabilities: [ESS], level: -70, frequency: 2412");
        assertEquals("Cafe, Bar & Grill", aps.get(0).ssid);
    }

    @Test
    public void hiddenNetworksAreDroppedNotNamed() {
        assertTrue(WifiRepo.parseCmScan(
                "SSID: \"<unknown ssid>\", BSSID: 00:00:00:00:00:00, level: -90, frequency: 2462")
                .isEmpty());
    }

    @Test
    public void securityLabelsFollowTheCapabilityFlags() {
        assertEquals("WPA3", securityOf("[WPA2-PSK+SAE-CCMP][ESS]"));
        assertEquals("OWE", securityOf("[RSN-OWE-CCMP][ESS]"));
        assertEquals("WPA2", securityOf("[WPA2-PSK-CCMP][ESS]"));
        assertEquals("WPA", securityOf("[WPA-PSK-TKIP][ESS]"));
        assertEquals("WEP", securityOf("[WEP][ESS]"));
        assertEquals("", securityOf("[ESS]"));
    }

    private String securityOf(String caps) {
        List<AccessPoint> aps = WifiRepo.parseCmScan(
                "SSID: \"x\", BSSID: 11:22:33:44:55:66, capabilities: " + caps
                        + ", level: -50, frequency: 2412");
        return aps.get(0).security;
    }

    @Test
    public void nl80211ScanBlocksAreRead() {
        List<AccessPoint> aps = WifiRepo.parseIwScan(
                "BSS 3c:37:86:aa:bb:cc(on wlan0)\n"
                        + "\tfreq: 5745\n"
                        + "\tsignal: -52.00 dBm\n"
                        + "\tcapability: ESS Privacy (0x0411)\n"
                        + "\tSSID: HomeNet_5G\n"
                        + "BSS 12:34:56:78:9a:bd(on wlan0)\n"
                        + "\tfreq: 2437\n"
                        + "\tsignal: -45.00 dBm\n"
                        + "\tSSID: Neighbour_2G\n");
        assertEquals(2, aps.size());
        assertEquals("HomeNet_5G", aps.get(0).ssid);
        assertEquals(-52, aps.get(0).rssi);
        assertEquals(5745, aps.get(0).frequency);
        assertEquals("Neighbour_2G", aps.get(1).ssid);
        assertEquals(-45, aps.get(1).rssi);
    }

    /**
     * nl80211 announces WPA2 inside an "RSN:" block, and the same block says "Privacy".
     * Without the ordering fix in securityFromCaps every WPA2 network reads as WEP.
     */
    @Test
    public void nl80211RsnIsWpa2NotWep() {
        List<AccessPoint> aps = WifiRepo.parseIwScan(
                "BSS 3c:37:86:aa:bb:cc(on wlan0)\n"
                        + "\tfreq: 5745\n"
                        + "\tsignal: -52.00 dBm\n"
                        + "\tcapability: ESS Privacy (0x0411)\n"
                        + "\tRSN:\t * Version: 1\n"
                        + "\t\t * Authentication suites: PSK\n"
                        + "\tSSID: HomeNet_5G\n");
        assertEquals("WPA2", aps.get(0).security);
    }

    @Test
    public void roamingDuplicateCollapsesToTheStrongestRadio() {
        List<AccessPoint> aps = WifiRepo.strongestPerSsid(List.of(
                new AccessPoint("Neighbour_2G", "12:34:56:78:9a:bc", "WPA2", -71, 2412),
                new AccessPoint("Neighbour_2G", "12:34:56:78:9a:bd", "WPA2", -45, 2437),
                new AccessPoint("OpenCafe", "aa:bb:cc:dd:ee:ff", "", -84, 2412)));
        assertEquals(2, aps.size());
        assertEquals("Neighbour_2G", aps.get(0).ssid);
        assertEquals("12:34:56:78:9a:bd", aps.get(0).bssid);
        assertEquals(-45, aps.get(0).rssi);
        assertEquals("OpenCafe", aps.get(1).ssid);
    }

    @Test
    public void channelLoadCountsOnlyTwentyFourHundred() {
        int[] load = WifiRepo.channelLoad(List.of(
                new AccessPoint("a", "", "", -50, 2412),
                new AccessPoint("b", "", "", -60, 2412),
                new AccessPoint("c", "", "", -70, 2437),
                new AccessPoint("d", "", "", -40, 5745)));
        assertEquals(2, load[1]);
        assertEquals(1, load[6]);
        assertEquals(0, load[11]);
    }

    @Test
    public void fiveGhzCountIgnoresTwoPointFour() {
        List<AccessPoint> aps = List.of(
                new AccessPoint("a", "", "", -50, 2412),
                new AccessPoint("b", "", "", -50, 5180),
                new AccessPoint("c", "", "", -50, 5745));
        assertEquals(2, WifiRepo.countOn5Ghz(aps));
    }

    @Test
    public void savedNetworksUseTheNetworkIdPrefix() {
        List<SavedNetwork> saved = WifiRepo.parseSaved(
                "Network Id: 0, SSID: \"HomeNet_5G\"\n"
                        + "Network Id: 3, SSID: \"Campus_Guest\"\n"
                        + "SSID: \"not a saved network line\"\n");
        assertEquals(2, saved.size());
        assertEquals(0, saved.get(0).id);
        assertEquals("HomeNet_5G", saved.get(0).ssid);
        assertEquals(3, saved.get(1).id);
        assertEquals("Campus_Guest", saved.get(1).ssid);
    }

    @Test
    public void bandAndChannelMaths() {
        assertEquals("2.4 GHz", WifiMath.bandName(2412));
        assertEquals("5 GHz", WifiMath.bandName(5745));
        assertEquals("6 GHz", WifiMath.bandName(6135));
        assertEquals("—", WifiMath.bandName(0));
        assertEquals(1, WifiMath.channelOf(2412));
        assertEquals(6, WifiMath.channelOf(2437));
        assertEquals(11, WifiMath.channelOf(2462));
        assertEquals(14, WifiMath.channelOf(2484));
        assertEquals(36, WifiMath.channelOf(5180));
        assertEquals(149, WifiMath.channelOf(5745));
        assertEquals("ch 6 · 2.4 GHz", WifiMath.channelLabel(2437));
    }

    @Test
    public void qualityAndBarsCoverBothEnds() {
        assertEquals(0, WifiMath.quality(-100));
        assertEquals(100, WifiMath.quality(-30));
        assertEquals(50, WifiMath.quality(-75));
        assertEquals(-1, WifiMath.quality(Integer.MIN_VALUE));
        assertEquals(0, WifiMath.bars(-1));
        assertEquals(1, WifiMath.bars(10));
        assertEquals(2, WifiMath.bars(26));
        assertEquals(4, WifiMath.bars(90));
    }

    @Test
    public void junkInputDoesNotThrow() {
        assertEquals("", WifiRepo.parseStatus("").gateway);
        assertTrue(WifiRepo.parseCmScan("garbage\n\n").isEmpty());
        assertTrue(WifiRepo.parseSaved("garbage").isEmpty());
        assertTrue(WifiRepo.parseIwScan("").isEmpty());
    }
}
