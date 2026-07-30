#!/usr/bin/env python3
"""Generates the DRK "camera lens" launcher icon as PNG mipmaps.

Pure standard library — writes RGBA PNGs with zlib.
Usage: make_icons.py <res_dir>
Produces: res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png
"""
import math
import os
import struct
import sys
import zlib

SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

BG = (0x0D, 0x11, 0x17, 0xFF)
RING = (0x4D, 0xA3, 0xFF, 0xFF)
LENS_DARK = (0x17, 0x1B, 0x21, 0xFF)
BLADE_A = (0x2E, 0x3A, 0x48, 0xFF)
BLADE_B = (0x22, 0x2A, 0x35, 0xFF)
PUPIL = (0x4D, 0xA3, 0xFF, 0xFF)
GLINT = (0x57, 0xD9, 0xA3, 0xFF)


def shade(px, py, cx, cy, r_unit):
    """Return the RGBA color for pixel at (px,py) on a 4x supersampled canvas."""
    x = (px + 0.5 - cx) / r_unit
    y = (py + 0.5 - cy) / r_unit
    rr = math.hypot(x, y)
    if rr > 1.02:
        return BG
    # outer accent ring
    if 0.88 <= rr <= 1.0:
        return RING
    if rr > 0.86:
        return BG
    # lens body
    if rr > 0.82:
        return LENS_DARK
    ang = math.atan2(y, x) + math.pi
    # aperture: 6 blades with slight swirl (angle offset grows with radius)
    swirl = 0.9 * (1.0 - rr)
    sector = int(((ang + swirl) / (math.pi / 3.0))) % 2
    body = BLADE_A if sector == 0 else BLADE_B
    # pupil
    if rr < 0.30:
        return PUPIL
    if rr < 0.36:
        return LENS_DARK
    # green glint near top-right of the lens
    gx, gy = 0.44, -0.44
    if math.hypot(x - gx, y - gy) < 0.14:
        return GLINT
    return body


def downsample(img_ss, ss, size):
    f = ss // size
    out = bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            acc = [0, 0, 0, 0]
            for dy in range(f):
                row = (y * f + dy) * ss
                for dx in range(f):
                    i = (row + x * f + dx) * 4
                    for c in range(4):
                        acc[c] += img_ss[i + c]
            o = (y * size + x) * 4
            n = f * f
            for c in range(4):
                out[o + c] = acc[c] // n
    return bytes(out)


def write_png(path, size, rgba):
    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    raw = bytearray()
    stride = size * 4
    for y in range(size):
        raw.append(0)
        raw += rgba[y * stride:(y + 1) * stride]
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


def main():
    res_dir = sys.argv[1]
    supersample = 4
    for density, size in SIZES.items():
        ss = size * supersample
        cx = cy = ss / 2.0
        r_unit = ss / 2.0 * 0.96
        img = bytearray(ss * ss * 4)
        for y in range(ss):
            row = y * ss * 4
            for x in range(ss):
                r, g, b, a = shade(x, y, cx, cy, r_unit)
                i = row + x * 4
                img[i:i + 4] = bytes((r, g, b, a))
        rgba = downsample(img, ss, size)
        out_dir = os.path.join(res_dir, "mipmap-%s" % density)
        os.makedirs(out_dir, exist_ok=True)
        write_png(os.path.join(out_dir, "ic_launcher.png"), size, rgba)
        print("wrote", os.path.join(out_dir, "ic_launcher.png"), "%dx%d" % (size, size))


if __name__ == "__main__":
    main()
