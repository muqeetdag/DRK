package io.nyx.app.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * Registry of the futuristic dark palettes. Each theme only changes values the
 * Instagram hook actually applies (dock fill, stroke, header tint, text).
 */
public final class Themes {

    public static final class Palette {
        public final String id;
        public final String label;
        public final int bg;
        public final int surface;
        public final int surfaceAlt;
        public final int line;
        public final int text;
        public final int muted;
        public final int accent;
        public final int secondary;
        public final boolean glow;

        Palette(String id, String label, int bg, int surface, int surfaceAlt, int line,
                int text, int muted, int accent, int secondary, boolean glow) {
            this.id = id;
            this.label = label;
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

    public static final Palette[] PALETTES = new Palette[]{
            new Palette("obsidian", "Obsidian",
                    0xFF06070B, 0xFF111320, 0xFF1A1E2F, 0xFF2A3350,
                    0xFFF4F6FF, 0xFF8A93AD, Brand.ACCENT, Brand.SECONDARY, true),
            new Palette("void", "Void",
                    0xFF04050A, 0xFF0D1018, 0xFF161B28, 0xFF232A3D,
                    0xFFE8ECFF, 0xFF7C86A3, 0xFF9D8CFF, 0xFF3DF5C2, true),
            new Palette("synthwave", "Synthwave",
                    0xFF0B0516, 0xFF170A2A, 0xFF241039, 0xFF4A2364,
                    0xFFFFEAFB, 0xFFB58ED0, 0xFFFF3DAB, 0xFFFFB347, true),
            new Palette("neon", "Neon acid",
                    0xFF05080A, 0xFF0C1418, 0xFF132026, 0xFF1F3A42,
                    0xFFECFFF9, 0xFF7EA8A0, 0xFFB6FF3D, 0xFF2EF2C4, true),
            new Palette("mono", "Monochrome",
                    0xFF070708, 0xFF111113, 0xFF1B1B1E, 0xFF2E2E33,
                    0xFFF7F7FA, 0xFF909098, 0xFFEDEDF2, 0xFF8E8E99, false),
    };

    private Themes() {}

    public static Palette byId(String id) {
        if (id != null) {
            for (Palette p : PALETTES) if (p.id.equals(id)) return p;
        }
        return PALETTES[0];
    }

    public static Palette current(Context ctx) {
        SharedPreferences sp = Prefs.open(ctx);
        try {
            int index = sp.getInt(Prefs.THEME, 0);
            Palette base = PALETTES[Math.max(0, Math.min(index, PALETTES.length - 1))];
            int accent = sp.getInt(Prefs.ACCENT, 0);
            int secondary = sp.getInt(Prefs.SECONDARY, 0);
            if (accent == 0 && secondary == 0) return base;
            return new Palette(base.id, base.label, base.bg, base.surface, base.surfaceAlt,
                    base.line, base.text, base.muted,
                    accent == 0 ? base.accent : accent,
                    secondary == 0 ? base.secondary : secondary,
                    base.glow);
        } catch (Throwable t) {
            return PALETTES[0];
        }
    }

    public static Palette withOverrides(Palette base, int accent, int secondary) {
        if (accent == 0 && secondary == 0) return base;
        return new Palette(base.id, base.label, base.bg, base.surface, base.surfaceAlt,
                base.line, base.text, base.muted,
                accent == 0 ? base.accent : accent,
                secondary == 0 ? base.secondary : secondary, base.glow);
    }

    public static int indexOf(String id) {
        for (int i = 0; i < PALETTES.length; i++) if (PALETTES[i].id.equals(id)) return i;
        return 0;
    }

    public static int blend(int a, int b, float t) {
        return Color.argb(
                Math.round(Color.alpha(a) * (1 - t) + Color.alpha(b) * t),
                Math.round(Color.red(a) * (1 - t) + Color.red(b) * t),
                Math.round(Color.green(a) * (1 - t) + Color.green(b) * t),
                Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }

    public static int alpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static String hex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }
}
