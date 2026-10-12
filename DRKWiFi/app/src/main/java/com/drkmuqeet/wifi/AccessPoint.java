package com.drkmuqeet.wifi;

/** One access point seen by a scan. */
final class AccessPoint {

    final String ssid;
    final String bssid;
    final String security;
    final int rssi;
    final int frequency;

    AccessPoint(String ssid, String bssid, String security, int rssi, int frequency) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.security = security;
        this.rssi = rssi;
        this.frequency = frequency;
    }

    int quality() {
        return WifiMath.quality(rssi);
    }

    String detail() {
        StringBuilder sb = new StringBuilder(WifiMath.channelLabel(frequency));
        if (!security.isEmpty()) {
            sb.append(" · ").append(security);
        }
        if (!bssid.isEmpty()) {
            sb.append(" · ").append(bssid);
        }
        return sb.toString();
    }
}
