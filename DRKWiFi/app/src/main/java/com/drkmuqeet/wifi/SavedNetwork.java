package com.drkmuqeet.wifi;

/** A network this device remembers, keyed by the id the supplicant knows it by. */
final class SavedNetwork {

    final int id;
    final String ssid;

    SavedNetwork(int id, String ssid) {
        this.id = id;
        this.ssid = ssid;
    }
}
