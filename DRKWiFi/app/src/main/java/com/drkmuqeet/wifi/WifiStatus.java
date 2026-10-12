package com.drkmuqeet.wifi;

/** The live state of this device's own radio. */
final class WifiStatus {

    boolean radioOn;
    boolean hasLink;
    String ssid = "";
    String bssid = "";
    String state = "";
    String ip = "";
    String gateway = "";
    String dns = "";
    String mac = "";
    String security = "";
    int frequency;
    int rssi = Integer.MIN_VALUE;
    int linkSpeedMbps;
    int channel;

    boolean powerSave;
    boolean powerSaveKnown;

    int quality() {
        return WifiMath.quality(rssi);
    }

    boolean connected() {
        return hasLink && !ssid.isEmpty() && !"<unknown ssid>".equals(ssid);
    }
}
