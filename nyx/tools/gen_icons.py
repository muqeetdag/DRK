#!/usr/bin/env python3
"""NYX icon renderer — futuristic dark launcher icons from white-label.json.

Generates every density bucket plus the adaptive-icon foreground/background
PNGs straight into the work source tree, so the app icon changes with the
config. Falls back to a procedural glyph when no image asset is supplied.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter


def load(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


def radial(size, inner, outer, center=(0.5, 0.42), power=1.6):
    img = Image.new("RGB", (size, size))
    px = img.load()
    cx, cy = center[0] * size, center[1] * size
    maxd = math.hypot(size * 0.85, size * 0.85)
    for y in range(size):
        for x in range(size):
            d = min(1.0, math.hypot(x - cx, y - cy) / maxd) ** power
            px[x, y] = tuple(int(inner[i] * (1 - d) + outer[i] * d) for i in range(3))
    return img


def glow(size, color, radius_ratio, center=(0.5, 0.5)):
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    cx, cy = center[0] * size, center[1] * size
    r = size * radius_ratio
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (255,))
    return layer.filter(ImageFilter.GaussianBlur(size / 6))


def glyph_orbit(draw, size, accent, secondary, width):
    """Ringed planet: tilted ellipse, glowing core, satellite node."""
    cx, cy = size * 0.5, size * 0.5
    rx, ry = size * 0.315, size * 0.105
    angle = math.radians(-26)
    pts = []
    for i in range(140):
        t = 2 * math.pi * i / 140
        x, y = rx * math.cos(t), ry * math.sin(t)
        xr = x * math.cos(angle) - y * math.sin(angle)
        yr = x * math.sin(angle) + y * math.cos(angle)
        pts.append((cx + xr, cy + yr))
    draw.line(pts + [pts[0]], fill=secondary + (230,), width=max(2, width - 1), joint="curve")

    core = size * 0.155
    draw.ellipse([cx - core, cy - core, cx + core, cy + core], fill=accent + (255,))
    hole = core * 0.42
    draw.ellipse([cx - hole, cy - hole * 1.05, cx + hole, cy + hole * 0.95], fill=(7, 8, 13, 255))

    # satellite riding the ring
    sx, sy = pts[int(len(pts) * 0.13)]
    sat = size * 0.048
    draw.ellipse([sx - sat, sy - sat, sx + sat, sy + sat], fill=secondary + (255,))


def glyph_bolt(draw, size, accent, secondary, width):
    """Hex circuit with lightning core."""
    cx, cy = size * 0.5, size * 0.5
    r = size * 0.335
    hexa = [(cx + r * math.cos(math.radians(60 * i - 90)),
             cy + r * math.sin(math.radians(60 * i - 90))) for i in range(6)]
    draw.polygon(hexa, outline=secondary + (150,), width=max(2, width // 2))

    s = size * 0.26
    draw.line(
        [(cx + s * 0.42, cy - s), (cx - s * 0.30, cy + s * 0.10),
         (cx + s * 0.10, cy + s * 0.10), (cx - s * 0.34, cy + s * 1.05)],
        fill=accent + (255,), width=width, joint="curve",
    )
    for px, py in (hexa[0], hexa[2], hexa[4]):
        d = size * 0.024
        draw.ellipse([px - d, py - d, px + d, py + d], fill=accent + (220,))


def glyph_prism(draw, size, accent, secondary, width):
    """Triangle refracting a beam into a fan."""
    cx, cy = size * 0.5, size * 0.5
    r = size * 0.28
    top, left, right = (cx, cy - r), (cx - r * 0.93, cy + r * 0.66), (cx + r * 0.93, cy + r * 0.66)
    draw.polygon([top, right, left], outline=accent + (255,), width=width)
    beam_y = cy + r * 0.30
    draw.line([(cx - size * 0.40, beam_y), (cx - r * 0.52, beam_y)],
              fill=secondary + (170,), width=max(2, width // 4))
    for i, spread in enumerate((-0.045, 0.0, 0.045)):
        ex = cx + size * 0.38
        ey = beam_y + size * spread
        draw.line([(cx + r * 0.52, beam_y + size * 0.004), (ex, ey)],
                  fill=((accent if i != 1 else secondary)) + (170,),
                  width=max(2, width // 5))


def glyph_wave(draw, size, accent, secondary, width):
    """Three frequency bands sweeping across a dark field."""
    cx, cy = size * 0.5, size * 0.5
    palette = (secondary + (210,), accent + (255,), secondary + (150,))
    for band, (colour, amp, off) in enumerate(
        ((0.0, 0.17, -0.10), (0.55, 0.21, 0.05), (1.25, 0.13, 0.19))
    ):
        pts = []
        for i in range(100):
            t = i / 99
            x = size * (0.13 + 0.74 * t)
            y = cy + size * off + math.sin(t * math.tau * 1.15 + band) * size * amp * math.sin(t * math.pi)
            pts.append((x, y))
        draw.line(pts, fill=palette[band], width=max(2, width - band), joint="curve")
    for edge in (0.13, 0.87):
        dot = size * 0.022
        draw.ellipse([size * edge - dot, cy - dot, size * edge + dot, cy + dot], fill=accent + (220,))


GLYPHS = {
    "orbit": glyph_orbit,
    "bolt": glyph_bolt,
    "prism": glyph_prism,
    "wave": glyph_wave,
}


def render_icon(size, cfg):
    accent = hex_rgb(cfg.get("accent", "#7C5CFF"))
    secondary = hex_rgb(cfg.get("secondary", "#22D3EE"))
    base = radial(size, (13, 14, 21), (4, 4, 8))
    glow_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow_layer.alpha_composite(glow(size, accent, 0.30, (0.30, 0.24)))
    glow_layer.alpha_composite(glow(size, secondary, 0.22, (0.78, 0.82)))
    canvas = Image.alpha_composite(base.convert("RGBA"), glow_layer)

    # faint tech grid
    grid = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grid)
    step = max(6, size // 14)
    for i in range(step, size, step):
        gd.line([(i, 0), (i, size)], fill=(255, 255, 255, 10), width=1)
        gd.line([(0, i), (size, i)], fill=(255, 255, 255, 10), width=1)
    canvas = Image.alpha_composite(canvas, grid)

    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    glyph = GLYPHS.get(cfg.get("glyph", "orbit"), glyph_orbit)
    glyph(draw, size, accent, secondary, max(3, int(size * 0.045)))
    canvas = Image.alpha_composite(canvas, layer)

    # vignette
    vign = radial(size, (0, 0, 0), (0, 0, 0), power=1.0)
    mask = Image.new("L", (size, size), 0)
    mdraw = ImageDraw.Draw(mask)
    mdraw.ellipse([-size * 0.25, -size * 0.25, size * 1.25, size * 1.25], fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(size / 8))
    dark = Image.new("RGBA", (size, size), (2, 2, 5, 190))
    canvas = Image.composite(canvas, Image.alpha_composite(canvas, dark), mask)
    del vign
    return canvas


def render_adaptive_foreground(size, cfg):
    accent = hex_rgb(cfg.get("accent", "#7C5CFF"))
    secondary = hex_rgb(cfg.get("secondary", "#22D3EE"))
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    glyph = GLYPHS.get(cfg.get("glyph", "orbit"), glyph_orbit)
    # Adaptive foregrounds are cropped to ~66% — shrink the glyph accordingly.
    inset = int(size * 0.14)
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    glyph(draw, size - inset * 2, accent, secondary, max(3, int(size * 0.042)))
    layer = layer.resize((size - inset * 2, size - inset * 2), Image.LANCZOS)
    canvas.alpha_composite(layer, (inset, inset))
    return canvas


def render_adaptive_background(size, cfg):
    accent = hex_rgb(cfg.get("accent", "#7C5CFF"))
    base = radial(size, (13, 14, 21), (4, 4, 8))
    glow_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow_layer.alpha_composite(glow(size, accent, 0.42, (0.34, 0.20)))
    return Image.alpha_composite(base.convert("RGBA"), glow_layer).convert("RGB")


def main():
    if len(sys.argv) != 3:
        print("usage: gen_icons.py <work-dir> <repo-root>", file=sys.stderr)
        sys.exit(2)
    work, repo = sys.argv[1], sys.argv[2]
    cfg = load(os.path.join(work, "config.json"))
    res = os.path.join(work, "app-src", "res")
    if not os.path.isdir(res):
        print("no resource dir yet — icons skipped", file=sys.stderr)
        return

    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for bucket, px in densities.items():
        folder = os.path.join(res, f"mipmap-{bucket}")
        os.makedirs(folder, exist_ok=True)
        render_icon(px, cfg).save(os.path.join(folder, "ic_launcher.png"))
        # Legacy round icon: same art, circular crop via alpha mask.
        icon = render_icon(px, cfg)
        mask = Image.new("L", (px, px), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, px - 1, px - 1], fill=255)
        round_icon = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        round_icon.paste(icon, (0, 0), mask)
        round_icon.save(os.path.join(folder, "ic_launcher_round.png"))

    fg_size, bg_size = 432, 432
    fg_folder = os.path.join(res, "mipmap-xxxhdpi")
    os.makedirs(fg_folder, exist_ok=True)
    render_adaptive_foreground(fg_size, cfg).save(os.path.join(fg_folder, "ic_launcher_foreground.png"))
    render_adaptive_background(bg_size, cfg).save(os.path.join(fg_folder, "ic_launcher_background.png"))

    # Monochrome (themed-icon) layer for Android 13+
    mono = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(mono)
    glyph = GLYPHS.get(cfg.get("glyph", "orbit"), glyph_orbit)
    glyph(draw, fg_size, (255, 255, 255), (255, 255, 255), max(5, int(fg_size * 0.04)))
    mono.save(os.path.join(fg_folder, "ic_launcher_monochrome.png"))

    # Notification / status-bar mark
    drawable = os.path.join(res, "drawable")
    os.makedirs(drawable, exist_ok=True)
    small = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
    glyph(ImageDraw.Draw(small), 96, (255, 255, 255), (255, 255, 255), 5)
    small.save(os.path.join(drawable, "ic_nyx_notification.png"))

    # In-app mark for the companion UI (rounded card art)
    art = render_icon(256, cfg)
    art.save(os.path.join(drawable, "nyx_mark.png"))
    # Splash / about banner
    banner_size = 512
    banner = render_adaptive_background(banner_size, cfg)
    art_small = render_adaptive_foreground(banner_size, cfg)
    banner = Image.alpha_composite(banner.convert("RGBA"), art_small)
    banner.save(os.path.join(drawable, "nyx_banner.png"))
    print(f"  icons rendered for {len(densities)} density buckets + adaptive layers")


if __name__ == "__main__":
    main()
