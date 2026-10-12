package com.drkmuqeet.wifi;

/** Signal, band and channel maths shared by the repository, the adapters and the views. */
final class WifiMath {

    private WifiMath() {
    }

    /** Maps dBm onto the 0-100 scale Android reports, clamped at both ends. */
    static int quality(int rssi) {
        if (rssi == Integer.MIN_VALUE) {
            return -1;
        }
        return Math.max(0, Math.min(100, (rssi + 100) * 2));
    }

    /** 0-4, for the four-bar meter. */
    static int bars(int quality) {
        if (quality < 0) {
            return 0;
        }
        return Math.max(1, Math.min(4, (int) Math.ceil(quality / 25.0)));
    }

    static String bandName(int frequencyMhz) {
        if (frequencyMhz <= 0) {
            return "—";
        }
        if (frequencyMhz < 2500) {
            return "2.4 GHz";
        }
        if (frequencyMhz < 5925) {
            return "5 GHz";
        }
        return "6 GHz";
    }

    static int channelOf(int frequencyMhz) {
        if (frequencyMhz == 2484) {
            return 14;
        }
        if (frequencyMhz >= 2412 && frequencyMhz <= 2472) {
            return (frequencyMhz - 2407) / 5;
        }
        if (frequencyMhz >= 5000 && frequencyMhz < 5925) {
            return (frequencyMhz - 5000) / 5;
        }
        if (frequencyMhz >= 5955) {
            return (frequencyMhz - 5950) / 5;
        }
        return 0;
    }

    /** "ch 6 · 2.4 GHz", or just the band when the channel cannot be derived. */
    static String channelLabel(int frequencyMhz) {
        int channel = channelOf(frequencyMhz);
        String band = bandName(frequencyMhz);
        return channel > 0 ? "ch " + channel + " · " + band : band;
    }
}
