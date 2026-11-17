#!/usr/bin/env bash
set -euo pipefail
# Requires Android SDK platform 34, build-tools 34.0.0, and JDK 17.
ROOT=$(cd "$(dirname "$0")/.." && pwd)
APP="$ROOT/mobilesec-companion"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
: "${ANDROID_JAR:?Set ANDROID_JAR to android-34/android.jar}"
: "${ANDROID_BUILD_TOOLS:?Set ANDROID_BUILD_TOOLS to the build-tools 34.0.0 directory}"
export PATH="$JAVA_HOME/bin:$PATH"
WORK="$ROOT/.cache/mobilesec-build"
OUT="$ROOT/deliverables"
KEYSTORE=${KEYSTORE:-"$ROOT/.cache/mobilesec-debug.keystore"}
KEY_ALIAS=${KEY_ALIAS:-androiddebugkey}
KEYSTORE_PASSWORD=${KEYSTORE_PASSWORD:-android}
KEY_PASSWORD=${KEY_PASSWORD:-android}
rm -rf "$WORK"
mkdir -p "$WORK/classes" "$WORK/dex" "$OUT" "$(dirname "$KEYSTORE")"
if [ ! -f "$KEYSTORE" ]; then
  if [ "$KEY_ALIAS" != androiddebugkey ] || [ "$KEYSTORE_PASSWORD" != android ] || [ "$KEY_PASSWORD" != android ]; then
    echo 'Custom keystore not found; create it before building.' >&2; exit 1
  fi
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASSWORD" -keypass "$KEY_PASSWORD" -alias "$KEY_ALIAS" -dname 'CN=DRK MobileSec Development, O=Development, C=US' -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$ANDROID_BUILD_TOOLS/aapt2" compile --dir "$APP/res" -o "$WORK/resources.zip"
"$ANDROID_BUILD_TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest "$APP/AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version 34 --version-code 1 --version-name 1.0.0 -o "$WORK/unsigned.apk" "$WORK/resources.zip"
javac -source 8 -target 8 -encoding UTF-8 -classpath "$ANDROID_JAR" -d "$WORK/classes" $(find "$APP/src" -name '*.java')
"$ANDROID_BUILD_TOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$WORK/dex" $(find "$WORK/classes" -name '*.class')
(cd "$WORK/dex" && zip -q "$WORK/unsigned.apk" classes.dex)
"$ANDROID_BUILD_TOOLS/zipalign" -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
"$ANDROID_BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" --ks-pass "pass:$KEYSTORE_PASSWORD" --key-pass "pass:$KEY_PASSWORD" --out "$OUT/DRK-MobileSec-debug.apk" "$WORK/aligned.apk"
"$ANDROID_BUILD_TOOLS/apksigner" verify --verbose "$OUT/DRK-MobileSec-debug.apk"
printf '\nBuilt %s\n' "$OUT/DRK-MobileSec-debug.apk"
