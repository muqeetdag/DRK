#!/usr/bin/env bash
#
# Offline build for the DRK EXIF Viewer, using only:
#   aapt2            (resource compiler / packager)
#   android.jar      (API 34 platform stubs, compile-time only)
#   kotlinc          (Kotlin -> JVM bytecode)
#   r8/d8            (JVM bytecode -> Dalvik dex)
#   openssl          (JAR v1 signature; valid for targetSdk <= 29)
#   python3          (zip surgery, R.kt generation, icon generation)
#   a JRE            (runs kotlinc/d8; keytool used for verification)
#
# Every tool path can be overridden via environment variables.
#
set -euo pipefail

TOOLS=${TOOLS:-/tmp/sdkx}
AAPT2=${AAPT2:-$TOOLS/aapt2-bin/aapt2}
ANDROID_JAR=${ANDROID_JAR:-$TOOLS/aosp-plat/android-34/android.jar}
KOTLINC=${KOTLINC:-$TOOLS/kotlinc/bin/kotlinc}
KOTLIN_STDLIB=${KOTLIN_STDLIB:-$TOOLS/kotlinc/lib/kotlin-stdlib.jar}
R8_JAR=${R8_JAR:-$TOOLS/aosp-r8/r8-master.jar}
JRE_HOME=${JRE_HOME:-$TOOLS/jdkrt/jdk4py/java-runtime}
JAVA=${JAVA:-$JRE_HOME/bin/java}
KEYTOOL=${KEYTOOL:-$JRE_HOME/bin/keytool}

HERE=$(cd "$(dirname "$0")" && pwd)
APP_SRC=$HERE/app/src/main
BUILD=$HERE/out/build
FINAL=$HERE/out/drk-exif-offline.apk
PKG=com.drk.exifviewer

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/gen" "$HERE/out"

echo "==> 1/7  launcher icons"
python3 "$HERE/tools/make_icons.py" "$APP_SRC/res"

echo "==> 2/7  aapt2 compile (resources)"
"$AAPT2" compile --dir "$APP_SRC/res" -o "$BUILD/compiled.flata"

echo "==> 3/7  aapt2 link (unsigned APK + symbol table)"
"$AAPT2" link -o "$BUILD/unsigned.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$APP_SRC/AndroidManifest.xml" \
    --min-sdk-version 24 \
    --target-sdk-version 29 \
    --version-code 5 \
    --version-name "1.2.2-offline" \
    --output-text-symbols "$BUILD/symbols.txt" \
    "$BUILD/compiled.flata"

echo "==> 4/7  R.kt + kotlinc (Kotlin -> .class)"
python3 "$HERE/tools/gen_r.py" "$BUILD/symbols.txt" "$PKG" "$BUILD/gen/R.kt"
find "$APP_SRC/kotlin" -name '*.kt' > "$BUILD/kotlin-files.txt"
JAVA_HOME=$JRE_HOME JAVA_OPTS="-Dfile.encoding=UTF-8" "$KOTLINC" \
    -classpath "$ANDROID_JAR" \
    -nowarn -jvm-target 1.8 \
    -d "$BUILD/classes" \
    "$BUILD/gen/R.kt" @"$BUILD/kotlin-files.txt"

echo "==> 5/7  d8 (.class -> classes.dex)"
find "$BUILD/classes" -name '*.class' > "$BUILD/class-files.txt"
"$JAVA" -cp "$R8_JAR" com.android.tools.r8.D8 \
    --min-api 24 \
    --lib "$ANDROID_JAR" \
    --output "$BUILD/dex" \
    $KOTLIN_STDLIB @"$BUILD/class-files.txt" --release

echo "==> 6/7  sign (v1 + v2, RELEASE key) + align"
# Persistent release key — committed on purpose so every build keeps the SAME
# signature and upgrades install cleanly (no more "uninstall first").
# This is a public convenience key for this app only — never reuse it elsewhere.
KEYDIR=$HERE/keystore
KEY=$KEYDIR/release-key.pem
CERT=$KEYDIR/release-cert.pem
if [[ ! -f "$KEY" || ! -f "$CERT" ]]; then
    echo "!! release key missing — generating a fresh one (upgrades from older builds will NOT work)"
    mkdir -p "$KEYDIR"
    openssl req -x509 -newkey rsa:3072 -nodes \
        -keyout "$KEY" -out "$CERT" \
        -days 10950 -subj "/CN=DRK EXIF Viewer Release/OU=DRK/O=DRK/C=IN" 2>/dev/null
fi

# 6a) JAR v1 signature + 4-byte realignment (targetSdk 29 keeps v1 fully valid)
python3 "$HERE/tools/signalign.py" \
    "$BUILD/unsigned.apk" "$BUILD/dex/classes.dex" \
    "$KEY" "$CERT" "$BUILD/signed-v1.apk"

# 6b) APK Signature Scheme v2 on top (whole-APK signature, API 24+ verifies it)
python3 "$HERE/tools/sign_v2.py" \
    "$BUILD/signed-v1.apk" "$FINAL" "$KEY" "$CERT"

echo "==> 7/7  verify"
"$KEYTOOL" -printcert -jarfile "$FINAL" | head -5
# v1 scheme: full detached CMS verification against CERT.SF content
python3 - "$FINAL" <<'PYEOF'
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
open('/tmp/drk-cert.rsa','wb').write(z.read('META-INF/CERT.RSA'))
open('/tmp/drk-cert.sf','wb').write(z.read('META-INF/CERT.SF'))
PYEOF
openssl cms -verify -binary -inform DER -in /tmp/drk-cert.rsa -content /tmp/drk-cert.sf -noverify -out /dev/null
# v2 scheme: digest + RSA signature + signer-cert check
python3 "$HERE/tools/sign_v2.py" --verify "$FINAL" "$CERT"
# no SMIMECapability regression (the v1.0 install-error class)
SMIME_COUNT=$(openssl asn1parse -inform DER -in /tmp/drk-cert.rsa 2>/dev/null | grep -c SMIME || true)
[[ "$SMIME_COUNT" == "0" ]] && echo "SMIME attr: absent (good)" || { echo "SMIME attr PRESENT — aborting"; exit 1; }
"$AAPT2" dump badging "$FINAL" | sed -n '1,6p'
echo
sha256sum "$FINAL"
ls -la "$FINAL"
