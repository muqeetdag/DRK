#!/usr/bin/env python3
"""Injects classes.dex into an aapt2-linked APK, applies a JAR (v1) signature
with openssl, and re-aligns uncompressed STORED entries to 4-byte boundaries.

v1 signatures are sufficient for APKs with targetSdkVersion <= 29, which is
why the offline build targets API 29. The signature covers entry *contents*
(rebuilt digests live in MANIFEST.MF), so re-aligning zip offsets afterwards
does not invalidate it — the same reason zipalign-after-signing works.

Usage: signalign.py <unsigned.apk> <classes.dex> <key.pem> <cert.pem> <out.apk>
"""
import base64
import hashlib
import os
import struct
import subprocess
import sys
import tempfile
import zipfile
import zlib

EOCD = b"PK\x05\x06"
LOC = 0x04034B50
CEN = 0x02014B50


def b64sha256(b):
    return base64.b64encode(hashlib.sha256(b).digest()).decode("ascii")


def jar_line(text):
    """Encode one JAR-manifest logical line with 72-byte UTF-8 wrapping."""
    raw = text.encode("utf-8")
    out = bytearray()
    first = True
    limit = 70
    while True:
        take, rest = raw[:limit], raw[limit:]
        while take and (take[-1] & 0xC0) == 0x80:  # don't split UTF-8 sequences
            rest = take[-1:] + rest
            take = take[:-1]
        out += take + b"\r\n"
        if not rest:
            break
        out += b" "
        raw = rest
        first = False
        limit = 69
    return bytes(out)


def main():
    unsigned_apk, dex_path, key_pem, cert_pem, out_apk = sys.argv[1:6]

    # ---- collect entries (original order preserved) ----
    entries = []  # (name, data, stored?)
    with zipfile.ZipFile(unsigned_apk) as z:
        for info in z.infolist():
            name = info.filename
            if name.startswith("META-INF/"):
                continue  # drop any pre-existing signatures
            data = z.read(name)
            entries.append([name, data, info.compress_type == zipfile.ZIP_STORED])
    with open(dex_path, "rb") as f:
        entries.append(["classes.dex", f.read(), False])

    # ---- MANIFEST.MF ----
    mf_sections = []
    for name, data, _ in entries:
        sec = jar_line("Name: " + name) + jar_line("SHA-256-Digest: " + b64sha256(data)) + b"\r\n"
        mf_sections.append((name, sec))
    manifest = (jar_line("Manifest-Version: 1.0")
                + jar_line("Created-By: 1.0 (DRK offline build)")
                + b"\r\n" + b"".join(s for _, s in mf_sections))

    # ---- CERT.SF ----
    sf_parts = [jar_line("Signature-Version: 1.0"),
                jar_line("Created-By: 1.0 (DRK offline build)"),
                jar_line("SHA-256-Digest-Manifest: " + b64sha256(manifest)),
                b"\r\n"]
    for name, sec in mf_sections:
        sf_parts.append(jar_line("Name: " + name))
        sf_parts.append(jar_line("SHA-256-Digest: " + b64sha256(sec)))
        sf_parts.append(b"\r\n")
    cert_sf = b"".join(sf_parts)

    # ---- PKCS#7 signature block (contents embedded, no signing attributes) ----
    # -noattr is REQUIRED for Android: apksig only supports a small whitelist of
    # PKCS#9 attributes (contentType, signingTime, messageDigest, ...) and rejects
    # the SMIMECapabilities attribute that OpenSSL >= 3 adds by default with
    # "PKCS9 SMIMECapability attribute not supported" / NO_CERTIFICATES.
    tmpdir = tempfile.mkdtemp(prefix="signalign-")
    sf_path = os.path.join(tmpdir, "CERT.SF")
    rsa_path = os.path.join(tmpdir, "CERT.RSA")
    with open(sf_path, "wb") as f:
        f.write(cert_sf)
    subprocess.run([
        "openssl", "cms", "-sign", "-binary", "-noattr",
        "-in", sf_path, "-signer", cert_pem, "-inkey", key_pem,
        "-outform", "DER", "-out", rsa_path, "-md", "sha256",
    ], check=True)
    with open(rsa_path, "rb") as f:
        cert_rsa = f.read()

    all_entries = ([["META-INF/MANIFEST.MF", manifest, False],
                    ["META-INF/CERT.SF", cert_sf, False],
                    ["META-INF/CERT.RSA", cert_rsa, False]]
                   + entries)

    # ---- write the final zip by hand (needed for alignment padding) ----
    locals_and_data = []
    central = []
    offset = 0
    for name, data, stored in all_entries:
        name_b = name.encode("utf-8")
        crc = zlib.crc32(data) & 0xFFFFFFFF
        if stored:
            comp = data
            method = 0
        else:
            co = zlib.compressobj(9, zlib.DEFLATED, -15)
            comp = co.compress(data) + co.flush()
            method = 8
        # alignment: STORED entries must start data at offset % 4 == 0
        extra = b""
        if stored:
            base = offset + 30 + len(name_b)
            pad = (4 - (base % 4)) % 4
            if pad >= 4:
                extra = struct.pack("<HH", 0xFFFF, pad - 4) + b"\x00" * (pad - 4)
            elif pad > 0:
                extra = b"\x00" * pad  # tiny orphan padding (extra-len bytes are never parsed)
        lh = struct.pack(
            "<IHHHHHIIIHH", LOC, 20, 0, method, 0, 0x21,
            crc, len(comp), len(data),
            len(name_b), len(extra)
        )
        locals_and_data.append(lh + name_b + extra + comp)
        central.append(struct.pack(
            "<IHHHHHHIIIHHHHHII", CEN, 20, 20, 0, method, 0, 0x21,
            crc, len(comp), len(data),
            len(name_b), 0, 0, 0, 0, 0, offset
        ) + name_b)
        offset += len(lh) + len(name_b) + len(extra) + len(comp)

    cd = b"".join(central)
    eocd = struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, len(central), len(central),
                       len(cd), offset, 0)
    with open(out_apk, "wb") as f:
        for blob in locals_and_data:
            f.write(blob)
        f.write(cd)
        f.write(eocd)

    # ---- self-verify: digests in MANIFEST.MF match rebuilt file contents ----
    with zipfile.ZipFile(out_apk) as z:
        for name, data, _ in entries:
            actual = b64sha256(z.read(name))
            assert actual in manifest.decode("utf-8"), "digest mismatch: " + name
        # alignment check: walk the central directory
        raw = open(out_apk, "rb").read()
        eocd = raw.rfind(EOCD)
        assert eocd > 0, "EOCD not found"
        cd_off = struct.unpack("<I", raw[eocd + 16:eocd + 20])[0]
        count = struct.unpack("<H", raw[eocd + 10:eocd + 12])[0]
        p = cd_off
        for _ in range(count):
            assert raw[p:p + 4] == b"PK\x01\x02", "bad central entry"
            method = struct.unpack("<H", raw[p + 10:p + 12])[0]
            nlen = struct.unpack("<H", raw[p + 28:p + 30])[0]
            elen = struct.unpack("<H", raw[p + 30:p + 32])[0]
            clen = struct.unpack("<H", raw[p + 32:p + 34])[0]
            lho = struct.unpack("<I", raw[p + 42:p + 46])[0]
            name = raw[p + 46:p + 46 + nlen].decode("utf-8")
            if method == 0:  # STORED
                assert raw[lho:lho + 4] == b"PK\x03\x04", "local header missing: " + name
                lnlen = struct.unpack("<H", raw[lho + 26:lho + 28])[0]
                lelen = struct.unpack("<H", raw[lho + 28:lho + 30])[0]
                data_off = lho + 30 + lnlen + lelen
                assert data_off % 4 == 0, "stored entry not 4-byte aligned: " + name
            p += 46 + nlen + elen + clen
    print("signed + aligned:", out_apk, "(%d entries)" % len(all_entries))


if __name__ == "__main__":
    main()
