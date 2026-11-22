package io.nyx.app.core;

/** White-label branding and palette baked in at build time. */
public final class Brand {
    public static final String NAME = "__APP_NAME__";
    public static final String TAGLINE = "__TAGLINE__";
    public static final String VERSION = "__VERSION__";
    public static final int VERSION_CODE = __VERSION_CODE__;
    public static final String FLAVOR = "__FLAVOR__";
    public static final String CHANNEL = "__CHANNEL__";
    public static final String SIGNATURE = "__SIGNATURE__";
    public static final String TELEGRAM = "__TELEGRAM__";
    public static final String GITHUB = "__GITHUB__";
    public static final int ACCENT = 0x__ACCENT_HEX__;
    public static final int SECONDARY = 0x__SECONDARY_HEX__;

    private Brand() {}
}
