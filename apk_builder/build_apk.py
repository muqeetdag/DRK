#!/usr/bin/env python3
"""
Build APK from classes.dex and AndroidManifest.xml with v1 signing
"""
import os, hashlib, base64, zipfile, struct, subprocess, tempfile, pathlib

WORKDIR = pathlib.Path('/home/user/DRK/apk_builder')
DEX = WORKDIR / 'classes.dex'
MANIFEST_BIN = WORKDIR / 'AndroidManifest.xml'
OUT_APK = WORKDIR / 'drk-app-unsigned.apk'
OUT_SIGNED = WORKDIR / 'DRK-Device-Tuning.apk'

# Prepare files dict
files_to_include = {
    'AndroidManifest.xml': MANIFEST_BIN.read_bytes(),
    'classes.dex': DEX.read_bytes(),
}

# Create unsigned APK (zip)
def create_unsigned_apk():
    # Ensure directory exists
    with zipfile.ZipFile(OUT_APK, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        for name, data in files_to_include.items():
            # Use ZipInfo with some date
            zi = zipfile.ZipInfo(name)
            zi.compress_type = zipfile.ZIP_DEFLATED
            # Set external attr to give some perms?
            z.writestr(zi, data)
    print(f"Created unsigned APK {OUT_APK} size {OUT_APK.stat().st_size}")

create_unsigned_apk()

# Now create META-INF files for v1 signing

# Helper to b64 sha256
def b64_sha256(data_bytes):
    digest = hashlib.sha256(data_bytes).digest()
    return base64.b64encode(digest).decode()

# Build MANIFEST.MF
# According to JAR spec, manifest file must have Manifest-Version and then entries sorted? We'll order as files_to_include keys sorted?
# For simplicity, use order as dict insertion (AndroidManifest.xml, classes.dex)

manifest_lines = []
manifest_lines.append("Manifest-Version: 1.0")
manifest_lines.append("Created-By: DRK Builder")
manifest_lines.append("")  # blank

# Keep sections for later digest for CERT.SF
manifest_sections = {}  # filename -> section bytes (including Name and digest and blank line)

for name in files_to_include:
    digest_b64 = b64_sha256(files_to_include[name])
    section = f"Name: {name}\r\nSHA-256-Digest: {digest_b64}\r\n\r\n"
    # Actually JAR uses \r\n? Usually \r\n. Example uses \r\n.
    manifest_sections[name] = section.encode('utf-8')
    manifest_lines.append(f"Name: {name}")
    manifest_lines.append(f"SHA-256-Digest: {digest_b64}")
    manifest_lines.append("")

manifest_content = "\r\n".join(manifest_lines).encode('utf-8') + b"\r\n"  # ensure ends with newline? Actually join already includes blank lines
# The above join: manifest_lines contains blank strings representing empty line, but join with \r\n will produce \r\n\r\n for blank? Let's build manually:
# We'll rebuild correctly per spec: Manifest-Version line, blank line, then each entry Name, Digest, blank line

# Let's redo more precisely per spec:
manifest_content = b"Manifest-Version: 1.0\r\nCreated-By: DRK Builder\r\n\r\n"
for name in files_to_include:
    digest_b64 = b64_sha256(files_to_include[name])
    entry = f"Name: {name}\r\nSHA-256-Digest: {digest_b64}\r\n\r\n"
    manifest_content += entry.encode('utf-8')

print("MANIFEST.MF content size", len(manifest_content))
print(manifest_content.decode()[:500])

# Now CERT.SF
# SHA-256 of manifest
manifest_digest_b64 = b64_sha256(manifest_content)

sf_lines = []
sf_content = b"Signature-Version: 1.0\r\nCreated-By: DRK Builder\r\n"
sf_content += f"SHA-256-Digest-Manifest: {manifest_digest_b64}\r\n\r\n".encode()

for name in files_to_include:
    # digest of manifest section for this file
    section_bytes = manifest_sections[name]  # this is the entry section we created for manifest? Actually manifest_sections we built as section string, but we need exact bytes as they appear in manifest_content
    # Let's extract the exact section bytes from manifest_content to be safe
    # Our manifest_content is built as: header + entry1 + entry2...
    # Each entry is f"Name: {name}\r\nSHA-256-Digest: {digest}\r\n\r\n"
    # So section is that entry
    sec = f"Name: {name}\r\nSHA-256-Digest: {b64_sha256(files_to_include[name])}\r\n\r\n".encode()
    digest_sec_b64 = b64_sha256(sec)
    sf_content += f"Name: {name}\r\nSHA-256-Digest: {digest_sec_b64}\r\n\r\n".encode()

print("CERT.SF size", len(sf_content))
print(sf_content.decode()[:1000])

# Now we need to generate key and cert and sign SF to get RSA

# Use openssl to generate key and self-signed cert if not exist
key_pem = WORKDIR / 'private.key'
cert_pem = WORKDIR / 'cert.pem'

if not key_pem.exists():
    print("Generating private key")
    subprocess.check_call(["openssl", "genrsa", "-out", str(key_pem), "2048"])
if not cert_pem.exists():
    print("Generating self-signed cert")
    # Create cert with subject CN=DRK
    subprocess.check_call([
        "openssl", "req", "-new", "-x509",
        "-key", str(key_pem),
        "-out", str(cert_pem),
        "-days", "3650",
        "-subj", "/CN=DRK/O=DRKMUQEET/C=US",
        "-sha256"
    ])

# Now sign SF using openssl smime -sign to get PKCS7 DER (CERT.RSA)
# openssl smime is deprecated in openssl 3, use cms
# Try cms first

cert_rsa = WORKDIR / 'CERT.RSA'
cert_sf = WORKDIR / 'CERT.SF'
cert_sf.write_bytes(sf_content)

# Write manifest
manifest_path = WORKDIR / 'MANIFEST.MF'
manifest_path.write_bytes(manifest_content)

# Sign
# For v1 jar signing, the file should be named CERT.RSA (or CERT.DSA) containing PKCS#7 signed data of CERT.SF
# Using openssl cms

def try_sign():
    # Try openssl cms
    cmd = [
        "openssl", "cms",
        "-sign",
        "-binary",
        "-noattr",
        "-in", str(cert_sf),
        "-signer", str(cert_pem),
        "-inkey", str(key_pem),
        "-outform", "DER",
        "-out", str(cert_rsa)
    ]
    try:
        subprocess.check_call(cmd)
        print("Signed with cms")
        return True
    except subprocess.CalledProcessError as e:
        print(f"cms failed {e}, trying smime")
        cmd2 = [
            "openssl", "smime",
            "-sign",
            "-binary",
            "-noattr",
            "-in", str(cert_sf),
            "-signer", str(cert_pem),
            "-inkey", str(key_pem),
            "-outform", "DER",
            "-out", str(cert_rsa)
        ]
        subprocess.check_call(cmd2)
        print("Signed with smime")
        return True

try_sign()

print(f"CERT.RSA size {cert_rsa.stat().st_size}")

# Now build final signed APK: copy unsigned and add META-INF files
with zipfile.ZipFile(OUT_APK, 'r') as zin:
    # Read existing files
    existing = {info.filename: zin.read(info.filename) for info in zin.infolist()}

# Write signed
with zipfile.ZipFile(OUT_SIGNED, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
    # Add original files
    for name, data in existing.items():
        zi = zipfile.ZipInfo(name)
        zi.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(zi, data)
    # Add META-INF
    for mf_name, path in [("META-INF/MANIFEST.MF", manifest_path), ("META-INF/CERT.SF", cert_sf), ("META-INF/CERT.RSA", cert_rsa)]:
        data = pathlib.Path(path).read_bytes()
        zi = zipfile.ZipInfo(mf_name)
        zi.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(zi, data)

print(f"Signed APK {OUT_SIGNED} size {OUT_SIGNED.stat().st_size}")

# Also try zipalign? Not needed but we can copy as final
# For verification, list contents
with zipfile.ZipFile(OUT_SIGNED, 'r') as z:
    print("APK contents:")
    for info in z.infolist():
        print(f"  {info.filename} {info.file_size} compressed {info.compress_size}")

# Optionally try to verify with openssl?
print("Done")
