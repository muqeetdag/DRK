package io.nyx.app.hook;

import android.content.Context;
import android.graphics.Color;

/**
 * Hook-side palette resolver. Duplicated (intentionally, tiny) so the hooked
 * process never needs to load the companion activity graph. Keeps the same
 * ids as core/Themes.
 */
public final class ThemesMirror {

    public static final class Palette {
        public final String id;
        public final int bg, surface, surfaceAlt, line, text, muted, accent, secondary;
        public final boolean glow;

        Palette(String id, int bg, int surface, int surfaceAlt, int line, int text, int muted,
                int accent, int secondary, boolean glow) {
            this.id = id;
            this.bg = bg;
            this.surface = surface;
            this.surfaceAlt = surfaceAlt;
            this.line = line;
            this.text = text;
            this.muted = muted;
            this.accent = accent;
            this.secondary = secondary;
            this.glow = glow;
        }
    }

    public static final String[] IDS = {"obsidian", "void", "synthwave", "neon", "mono"};

    private static final int[][] RAW = {
            {0xFF06070B, 0xFF111320, 0xFF1A1E2F, 0xFF2A3350, 0xFFF4F6FF, 0xFF8A93AD},
            {0xFF04050A, 0xFF0D1018, 0xFF161B28, 0xFF232A3D, 0xFFE8ECFF, 0xFF7C86A3},
            {0xFF0B0516, 0xFF170A2A, 0xFF241039, 0xFF4A2364, 0xFFFFEAFB, 0xFFB58ED0},
            {0xFF05080A, 0xFF0C1418, 0xFF132026, 0xFF1F3A42, 0xFFECFFF9, 0xFF7EA8A0},
            {0xFF070708, 0xFF111113, 0xFF1B1B1E, 0xFF2E2E33, 0xFFF7F7FA, 0xFF909098},
    };

    private static final int[][] ACCENTS = {
            {0xFF7C5CFF, 0xFF22D3EE},
            {0xFF9D8CFF, 0xFF3DF5C2},
            {0xFFFF3DAB, 0xFFFFB347},
            {0xFFB6FF3D, 0xFF2EF2C4},
            {0xFFEDEDF2, 0xFF8E8E99},
    };

    private ThemesMirror() {}

    public static Palette resolve(Context ctx) {
        int index = 0;
        try {
            index = PrefsReader.getInt("theme_id", 0);
        } catch (Throwable ignored) {
        }
        index = Math.max(0, Math.min(index, RAW.length - 1));
        int accent = 0;
        int secondary = 0;
        try {
            accent = PrefsReader.getInt("accent_color", 0);
            secondary = PrefsReader.getInt("secondary_color", 0);
        } catch (Throwable ignored) {
        }
        return new Palette(IDS[index], RAW[index][0], RAW[index][1], RAW[index][2], RAW[index][3],
                RAW[index][4], RAW[index][5],
                accent == 0 ? ACCENTS[index][0] : accent,
                secondary == 0 ? ACCENTS[index][1] : secondary,
                index != 4);
    }

    public static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    public static int blend(int a, int b, float t) {
        return Color.argb(
                Math.round(Color.alpha(a) * (1 - t) + Color.alpha(b) * t),
                Math.round(Color.red(a) * (1 - t) + Color.red(b) * t),
                Math.round(Color.green(a) * (1 - t) + Color.green(b) * t),
                Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }
}
