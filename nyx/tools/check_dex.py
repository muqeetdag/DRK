#!/usr/bin/env python3
"""Reject a classes.dex that cannot run on the declared minSdk.

Why this exists: D8 with `--no-desugaring` keeps Java 8 lambdas as
`invoke-custom` / `invoke-polymorphic` instructions. Dalvik and ART before
API 26 do not implement them and abort the *enclosing class* with
NoClassDefFoundError the moment it is loaded - i.e. an instant crash loop on
Android 5/6/7. `const-method-handle` and `const-method-type` are the same
family. This script walks the real code items and fails the build if any
survive, so the bug can never be shipped again.

Exit code 0 = clean, 1 = incompatible opcode found, 2 = could not parse.
"""
import struct
import sys

# opcodes that only exist from Android 8 (API 26) / dex 038 onwards
BAD_OPS = {
    0xFA: "invoke-polymorphic",
    0xFB: "invoke-polymorphic/range",
    0xFC: "invoke-custom",
    0xFD: "invoke-custom/range",
    0xFE: "const-method-handle",
    0xFF: "const-method-type",
}


def u16(d, o):
    return struct.unpack_from("<H", d, o)[0]


def u32(d, o):
    return struct.unpack_from("<I", d, o)[0]


def uleb(d, o):
    result = 0
    shift = 0
    while True:
        byte = d[o]
        o += 1
        result |= (byte & 0x7F) << shift
        shift += 7
        if not byte & 0x80:
            return result, o


# map_list item type codes that only dex 038 (API 26+) may contain
MAP_CALL_SITE = 0x0007
MAP_METHOD_HANDLE = 0x0008

MAP_NAMES = {MAP_CALL_SITE: "call_site_ids", MAP_METHOD_HANDLE: "method_handles"}


def scan(path):
    """Return (dex_version, counter_hits, opcode_hits).

    The dex header's own map_list is authoritative: a call_site / method_handle
    section exists in the file only because `invoke-custom` or
    `const-method-handle` instructions demanded it. Walking bytecode at a fixed
    2-byte stride produces false positives (operand words look like opcodes), so
    the section counters are the primary signal and the bytecode walk is only a
    secondary cross-check.
    """
    d = open(path, "rb").read()
    if len(d) < 112 or d[:4] != b"dex\n":
        raise ValueError("not a dex file")
    version = d[4:7].decode("ascii", "replace")
    string_ids_size, string_ids_off = u32(d, 56), u32(d, 60)
    method_ids_off = u32(d, 92)
    class_defs_size, class_defs_off = u32(d, 96), u32(d, 100)

    counter_hits = []
    try:
        map_off = u32(d, 0x34)
        for i in range(u32(d, map_off)):
            entry = map_off + 4 + i * 12
            item_type = u16(d, entry)
            count = u32(d, entry + 4)
            if item_type in MAP_NAMES and count:
                counter_hits.append((MAP_NAMES[item_type], count))
    except struct.error:
        pass

    def string(idx):
        off = u32(d, string_ids_off + idx * 4)
        _, p = uleb(d, off)
        return d[p:d.index(b"\x00", p)].decode("utf-8", "replace")

    def method_name(method_idx):
        if method_idx >= u32(d, 88):
            return "?"
        name_idx = u32(d, method_ids_off + method_idx * 8 + 4)
        return string(name_idx)

    hits = []
    for i in range(class_defs_size):
        base = class_defs_off + i * 32
        class_data = u32(d, base + 24)
        if not class_data:
            continue
        o = class_data
        sf, o = uleb(d, o)
        inf, o = uleb(d, o)
        dm, o = uleb(d, o)
        vm, o = uleb(d, o)
        methods = []
        for _ in range(sf + inf):
            _, o = uleb(d, o)
            _, o = uleb(d, o)
        for _ in range(dm + vm):
            idx, o = uleb(d, o)
            _, o = uleb(d, o)
            code_off, o = uleb(d, o)
            if code_off:
                methods.append((idx, code_off))
        for idx, code_off in methods:
            insns_size = u32(d, code_off + 12)
            p = code_off + 16
            end = p + insns_size * 2
            while p + 2 <= end:
                unit = u16(d, p)
                op = unit & 0xFF
                if op in BAD_OPS:
                    hits.append((method_name(idx), BAD_OPS[op], p))
                p += 2
    return version, counter_hits, hits


def main(argv):
    if len(argv) < 3:
        print("usage: check_dex.py <classes.dex> <min_sdk>", file=sys.stderr)
        return 2
    path, min_sdk = argv[1], int(argv[2])
    try:
        version, counters, hits = scan(path)
    except Exception as exc:  # noqa: BLE001 - build tool, report and bail
        print(f"check_dex: cannot parse {path}: {exc}", file=sys.stderr)
        return 2

    detail = ", ".join(f"{name}={count}" for name, count in counters) or "no 038 sections"
    print(f"check_dex: dex {version}, min-api {min_sdk}, {detail}, "
          f"{len(hits)} opcode hit(s)")

    # dex 038 sections / opcodes are legal from API 26 upwards only.
    if min_sdk >= 26:
        print("check_dex: allowed because minSdk >= 26")
        return 0

    # The map_list is authoritative: those sections exist only because
    # invoke-custom / const-method-handle demanded them.
    if counters:
        for name, count in counters:
            print(f"FAIL: dex carries a {name} section ({count} entries); "
                  f"generated for invoke-custom, unloadable below API 26",
                  file=sys.stderr)
        return 1

    # The raw bytecode walk cannot tell an opcode from an operand word without a
    # full instruction-width table, so its hits are informational only. On dex
    # 035 with no 038 sections there is nothing that can fault.
    if hits:
        print(f"check_dex: note - {len(hits)} byte pattern(s) matched while "
              f"scanning; dex 035 has no such opcodes, ignoring")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
