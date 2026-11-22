package android.os;

/**
 * Device build facts reported to the app.
 *
 * VERSION.SDK_INT is DELIBERATELY not `static final`: the virtual phone boots as
 * several different Android versions to prove the APK still runs on old phones.
 * A compile-time constant would be inlined into the app's bytecode and could
 * never be switched. Production Android keeps it final, but the app can only
 * ever read it, so a non-final field behaves identically.
 */
public class Build {
    public static final String MANUFACTURER = "NYX";
    public static final String BRAND = "nyx";
    public static final String MODEL = "VirtualPhone";
    public static final String DEVICE = "vphone";
    public static final String PRODUCT = "vphone";
    public static final String FINGERPRINT = "nyx/vphone/vphone:14/UP1A/1:user/release-keys";
    public static final String ID = "UP1A";
    public static final String HARDWARE = "vphone";
    public static final String TAGS = "release-keys";
    public static final String TYPE = "user";

    public static class VERSION {
        /** 21 = Android 5.0 ... 35 = Android 15. Set by VPhone at boot. */
        public static int SDK_INT = 34;
        public static String RELEASE = "14";
        public static String CODENAME = "REL";
        public static final int PREVIEW_SDK_INT = 0;

        /** Boots this process as a different Android version. */
        public static void setSdk(int sdk) {
            SDK_INT = sdk;
            RELEASE = releaseNameOf(sdk);
        }

        private static String releaseNameOf(int sdk) {
            switch (sdk) {
                case 21: return "5.0";
                case 22: return "5.1";
                case 23: return "6.0";
                case 24: return "7.0";
                case 25: return "7.1";
                case 26: return "8.0";
                case 27: return "8.1";
                case 28: return "9";
                case 29: return "10";
                case 30: return "11";
                case 31: return "12";
                case 32: return "12L";
                case 33: return "13";
                case 34: return "14";
                case 35: return "15";
                case 36: return "16";
                default: return "unknown";
            }
        }
    }

    public static class VERSION_CODES {
        public static final int LOLLIPOP = 21;
        public static final int LOLLIPOP_MR1 = 22;
        public static final int M = 23;
        public static final int N = 24;
        public static final int N_MR1 = 25;
        public static final int O = 26;
        public static final int O_MR1 = 27;
        public static final int P = 28;
        public static final int Q = 29;
        public static final int R = 30;
        public static final int S = 31;
        public static final int S_V2 = 32;
        public static final int TIRAMISU = 33;
        public static final int UPSIDE_DOWN_CAKE = 34;
        public static final int VANILLA_ICE_CREAM = 35;
        public static final int BAKLAVA = 36;
    }
}
