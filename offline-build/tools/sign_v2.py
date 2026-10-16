#!/usr/bin/env python3
"""Adds an APK Signature Scheme v2 block to an already v1-signed, aligned APK,
and independently verifies it.

Why both schemes?
  * v1 (JAR) is broadly compatible and sufficient for targetSdkVersion <= 29,
    but it only covers zip *entry contents*. Some devices/ROMs and tooling
    complain about v1-only APKs ("signature failed" style errors).
  * v2 is the whole-APK signature modern Android prefers (supported since
    API 24 — exactly our minSdk). Keeping both gives every verifier its
    favorite flavor.

File layout after signing:
    [ZIP entries][APK Signing Block][ZIP Central Directory][EOCD']
EOCD'.cdOffset points at the (shifted) Central Directory. Digest sections for
v2 are: bytes [0, blockStart), the Central Directory bytes, and the EOCD whose
cd offset field is treated as pointing at blockStart.

Usage:
    sign_v2.py <in.apk> <out.apk> <key.pem> <cert.pem>
    sign_v2.py --verify <signed.apk>
"""
import base64
import hashlib
import os
import struct
import subprocess
import sys
import tempfile

V2_ID = 0x7109871A            # APK Signature Scheme v2 block id
MAGIC = b"APK Sig Block 42"

# Signature algorithms (AOSP SignatureAlgorithm ids). We sign with BOTH so any
# verifier's "best algorithm" selection finds a genuinely-valid signature:
# strict apksig ranks PSS above PKCS#1; some platform forks insist on PSS.
ALGO_RSA_PKCS1_SHA256 = 0x0101   # SHA256withRSA  (RSASSA-PKCS1-v1_5)
ALGO_RSA_PSS_SHA256 = 0x0103     # SHA256withRSA/PSS (MGF1-SHA256, salt=32)


def u32(n):
    return struct.pack("<I", n)


def u64(n):
    return struct.pack("<Q", n)


def lp(blob):
    """Length-prefix a blob per the apkspec wire format."""
    return u32(len(blob)) + blob


def find_eocd(data):
    idx = data.rfind(b"PK\x05\x06")
    if idx < 0:
        raise SystemExit("EOCD not found — not a zip?")
    return idx


def parse_layout(data):
    eocd = find_eocd(data)
    cd_off = struct.unpack_from("<I", data, eocd + 16)[0]
    cd_size = struct.unpack_from("<I", data, eocd + 12)[0]
    return eocd, cd_off, cd_size


def content_digest(sections):
    """SHA-256 content digest over the three digest sections (1 MB chunks)."""
    chunk_digests = b""
    count = 0
    for s in sections:
        for i in range(0, len(s), 1 << 20):
            chunk = s[i:i + (1 << 20)]
            chunk_digests += hashlib.sha256(b"\xa5" + u32(len(chunk)) + chunk).digest()
            count += 1
    return hashlib.sha256(b"\x5a" + u32(count) + chunk_digests).digest()


def run(cmd):
    r = subprocess.run(cmd, capture_output=True)
    if r.returncode != 0:
        raise SystemExit("command failed: %s\n%s" % (" ".join(cmd), r.stderr.decode()))
    return r.stdout


def encode_pairs(pairs):
    """AOSP ApkSigningBlockUtilsLite
       .encodeAsSequenceOfLengthPrefixedPairsOfIntAndLengthPrefixedBytes:
       each record = lp( [algoID u32][valueLen u32][value] ), concatenated."""
    out = b""
    for algo, value in pairs:
        out += u32(8 + len(value)) + u32(algo) + u32(len(value)) + value
    return out


def build_v2_value(data, key_pem, cert_pem):
    """Wire format, byte-exact vs AOSP V2SchemeSigner.generateSignerBlock:

    value          = lp( SEQ-of-signers );  SEQ-of-signers = lp(signer)…
    signer         = lp(signedData) + lp(signaturesField) + lp(publicKey SPKI)
    signedData     = lp(digestsField) + lp(certsField) + lp("") + lp("")
    digestsField   = per-digest: lp( [algoID][len][digest] )   (record-lp'd)
    certsField     = per-cert:   lp( X.509 DER )
    signaturesField= per-sig:    lp( [algoID][len][signature] )
    RSA signature  = SHA256withRSA (PKCS#1 v1.5) over signedData bytes
    """
    eocd, cd_off, cd_size = parse_layout(data)
    # Section 3 per spec: EOCD bytes with cd-offset treated as the sig block
    # start — here it already equals cd_off because the block is inserted
    # exactly where the Central Directory began.
    s1 = data[:cd_off]
    s2 = data[cd_off:cd_off + cd_size]
    s3 = bytearray(data[eocd:])
    struct.pack_into("<I", s3, 16, cd_off)
    digest = content_digest([s1, s2, bytes(s3)])

    der_cert = run(["openssl", "x509", "-in", cert_pem, "-outform", "DER"])
    spki = run(["openssl", "pkey", "-in", key_pem, "-pubout", "-outform", "DER"])

    # Digest records carry one entry per signature algorithm (order must match
    # the signatures records exactly — apksig compares the lists).
    algo_order = [ALGO_RSA_PSS_SHA256, ALGO_RSA_PKCS1_SHA256]
    digests_field = encode_pairs([(a, digest) for a in algo_order])
    signed_data = (lp(digests_field)
                   + lp(lp(der_cert))
                   + lp(b"")            # additional attributes: none
                   + lp(b""))           # trailing empty element (AOSP emits this)

    # Sign signed_data with BOTH RSA algos:
    #   0x0103  RSASSA-PSS  (SHA-256, MGF1-SHA256, salt = 32 = digest len)
    #   0x0101  RSASSA-PKCS1-v1_5 with SHA-256
    tmp = tempfile.mkdtemp(prefix="signv2-")
    sd_path = os.path.join(tmp, "sd.bin")
    with open(sd_path, "wb") as f:
        f.write(signed_data)

    sig_pss_path = os.path.join(tmp, "sig-pss.bin")
    run(["openssl", "dgst", "-sha256", "-sign", key_pem,
         "-sigopt", "rsa_padding_mode:pss",
         "-sigopt", "rsa_mgf1_md:sha256",
         "-sigopt", "rsa_pss_saltlen:digest",
         "-out", sig_pss_path, sd_path])
    sig_pss = open(sig_pss_path, "rb").read()

    sig_pkcs1_path = os.path.join(tmp, "sig-pkcs1.bin")
    run(["openssl", "dgst", "-sha256", "-sign", key_pem,
         "-sigopt", "rsa_padding_mode:pkcs1",
         "-out", sig_pkcs1_path, sd_path])
    sig_pkcs1 = open(sig_pkcs1_path, "rb").read()

    sigs_field = encode_pairs([
        (ALGO_RSA_PSS_SHA256, sig_pss),
        (ALGO_RSA_PKCS1_SHA256, sig_pkcs1),
    ])
    signer = (lp(signed_data)
              + lp(sigs_field)
              + lp(spki))
    # value = lp( SEQ-of-signers ), each signer itself length-prefixed
    return lp(lp(signer))


def sign(in_apk, out_apk, key_pem, cert_pem):
    data = open(in_apk, "rb").read()
    eocd, cd_off, _ = parse_layout(data)
    value = build_v2_value(data, key_pem, cert_pem)

    pair = u64(4 + len(value)) + u32(V2_ID) + value
    block_size = 8 + len(pair) + 16   # covers: this size field excluded, rest included
    block = u64(block_size) + pair + u64(block_size) + MAGIC

    out = bytearray()
    out += data[:cd_off]
    out += block
    out += data[cd_off:eocd]
    eocd_new = bytearray(data[eocd:])
    struct.pack_into("<I", eocd_new, 16, cd_off + len(block))
    out += eocd_new
    with open(out_apk, "wb") as f:
        f.write(bytes(out))
    print("v2 signed: %s (signing block %d bytes)" % (out_apk, block_size + 8))


def _rd(buf, off):
    n = struct.unpack_from("<I", buf, off)[0]
    return buf[off + 4:off + 4 + n], off + 4 + n


def verify(apk, expect_cert_pem=None):
    data = open(apk, "rb").read()
    eocd, cd_off, cd_size = parse_layout(data)

    # The signing block sits immediately before the Central Directory.
    magic_off = cd_off - 16
    assert data[magic_off:cd_off] == MAGIC, "v2: block magic not found"
    size2 = struct.unpack_from("<Q", data, magic_off - 8)[0]
    block_start = cd_off - (size2 + 8)
    assert block_start >= 0, "v2: negative block start"
    size1 = struct.unpack_from("<Q", data, block_start)[0]
    assert size1 == size2, "v2: size fields disagree"

    found = None
    p = block_start + 8
    end = magic_off - 8
    while p < end:
        plen, pid = struct.unpack_from("<QI", data, p)
        if pid == V2_ID:
            found = data[p + 12:p + 12 + plen - 4]
            break
        p += 8 + plen
    assert found is not None, "v2: scheme id 0x7109871a not present"

    # value = lp(SEQ-of-signers); each signer itself length-prefixed (AOSP spec)
    signers, _ = _rd(found, 0)
    signer, _ = _rd(signers, 0)
    signed_data, off = _rd(signer, 0)
    signatures_field, off = _rd(signer, off)
    spki, _ = _rd(signer, off)

    # signedData = lp(digestsField) + lp(certsField) [+ optional tails]
    digests_field, off = _rd(signed_data, 0)
    certs_field, _ = _rd(signed_data, off)

    def read_records(field):
        """AOSP record format: repeated lp( [algoID u32][valueLen u32][value] )."""
        recs = []
        off = 0
        while off < len(field):
            rec, off = _rd(field, off)
            assert len(rec) >= 8, "v2: short record"
            algo = struct.unpack_from("<I", rec, 0)[0]
            assert struct.unpack_from("<I", rec, 4)[0] == len(rec) - 8, "v2: bad record len"
            recs.append((algo, rec[8:]))
        return recs

    digest_records = read_records(digests_field)
    sig_records = read_records(signatures_field)

    # cert record = lp(X.509 DER)
    cert_der, _ = _rd(certs_field, 0)

    # apksig compares the algo id lists (in order) between the two records
    assert [a for a, _ in digest_records] == [a for a, _ in sig_records], \
        "v2: SIG_ALG list mismatch between digests and signatures records"

    # 1) recompute the whole-APK digest from scratch; every digest record must match
    s1 = data[:block_start]
    s2 = data[cd_off:cd_off + cd_size]
    s3 = bytearray(data[eocd:])
    struct.pack_into("<I", s3, 16, block_start)
    calc = content_digest([s1, s2, bytes(s3)])
    for algo, stored in digest_records:
        assert calc == stored, "v2: CONTENT DIGEST MISMATCH (algo 0x%04x)" % algo

    # 2) verify EVERY RSA signature record over signed_data with its own scheme
    tmp = tempfile.mkdtemp(prefix="signv2-verify-")
    sd_p = os.path.join(tmp, "sd")
    pk_p = os.path.join(tmp, "pk.pem")
    cd_p = os.path.join(tmp, "cert.der")
    open(sd_p, "wb").write(signed_data)
    open(cd_p, "wb").write(cert_der)
    open(pk_p, "wb").write(
        b"-----BEGIN PUBLIC KEY-----\n"
        + base64.encodebytes(spki).replace(b"\n", b"")
        + b"\n-----END PUBLIC KEY-----\n"
    )
    scheme_opts = {
        ALGO_RSA_PKCS1_SHA256: ["-sigopt", "rsa_padding_mode:pkcs1"],
        ALGO_RSA_PSS_SHA256: ["-sigopt", "rsa_padding_mode:pss",
                              "-sigopt", "rsa_mgf1_md:sha256",
                              "-sigopt", "rsa_pss_saltlen:digest"],
    }
    for algo, sig in sig_records:
        opts = scheme_opts.get(algo)
        assert opts is not None, "v2: unknown signature algo 0x%04x" % algo
        sig_p = os.path.join(tmp, "sig-%04x" % algo)
        open(sig_p, "wb").write(sig)
        r = subprocess.run(["openssl", "dgst", "-sha256", "-verify", pk_p]
                           + opts + ["-signature", sig_p, sd_p],
                           capture_output=True)
        assert r.returncode == 0 and b"Verified OK" in r.stdout, \
            "v2: SIGNATURE INVALID (algo 0x%04x): %s" % (algo, r.stderr.decode())
        print("  signature 0x%04x (%s): Verified OK" %
              (algo, "RSASSA-PSS/SHA-256" if algo == ALGO_RSA_PSS_SHA256
                     else "RSASSA-PKCS1-v1_5/SHA-256"))

    # 3) SPKI embedded as 'publicKey' must be the cert's own key
    cert_spki = subprocess.run(
        ["openssl", "x509", "-inform", "DER", "-in", cd_p, "-pubkey", "-noout"],
        capture_output=True, check=True)
    assert cert_spki.returncode == 0
    spki_der_from_cert = subprocess.run(
        ["openssl", "pkey", "-pubin", "-outform", "DER"],
        capture_output=True, input=cert_spki.stdout).stdout
    assert spki_der_from_cert == spki, "v2: signer key does not match certificate"

    # 4) STORED entries must still be 4-aligned (mmap-ability) after the splice
    p = cd_off
    count = struct.unpack_from("<H", data, eocd + 10)[0]
    for _ in range(count):
        assert data[p:p + 4] == b"PK\x01\x02", "v2: corrupt central directory"
        method = struct.unpack_from("<H", data, p + 10)[0]
        nlen = struct.unpack_from("<H", data, p + 28)[0]
        elen = struct.unpack_from("<H", data, p + 30)[0]
        clen = struct.unpack_from("<H", data, p + 32)[0]
        lho = struct.unpack_from("<I", data, p + 42)[0]
        if method == 0:
            lnlen = struct.unpack_from("<H", data, lho + 26)[0]
            lelen = struct.unpack_from("<H", data, lho + 28)[0]
            assert (lho + 30 + lnlen + lelen) % 4 == 0, "v2: alignment broken"
        p += 46 + nlen + elen + clen

    fp = hashlib.sha256(cert_der).hexdigest()
    print("v2 verify: PASS (digest + RSA signature + key/cert match + alignment)")
    print("signer cert SHA-256:", fp)
    if expect_cert_pem:
        want = subprocess.run(["openssl", "x509", "-in", expect_cert_pem,
                               "-outform", "DER"], capture_output=True, check=True).stdout
        assert hashlib.sha256(want).hexdigest() == fp, "v2: unexpected signer cert!"
        print("signer cert matches expected release cert")


if __name__ == "__main__":
    if sys.argv[1] == "--verify":
        verify(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
    else:
        sign(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4])
