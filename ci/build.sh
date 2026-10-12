#!/usr/bin/env bash
# Builds the DRKWiFi APK. Everything the build needs lives here so the workflow file
# that calls it can stay short enough to paste from a phone.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

GRADLE_VERSION="8.7"
ANDROID_PLATFORM="platforms;android-34"
ANDROID_BUILDTOOLS="build-tools;34.0.0"

log() { printf '\n=== %s\n' "$1"; }

# ---------------------------------------------------------------- toolchain
log "Toolchain"
if [ -z "${JAVA_HOME:-}" ] && [ -d /usr/lib/jvm/temurin-17-jdk-amd64 ]; then
  export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64
fi
java -version

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK" ]; then
  for candidate in /usr/local/lib/android/sdk "$HOME/Android/Sdk" "$HOME/android-sdk"; do
    [ -d "$candidate" ] && SDK="$candidate" && break
  done
fi
[ -n "$SDK" ] || { echo "No Android SDK found. Set ANDROID_SDK_ROOT." >&2; exit 1; }
export ANDROID_SDK_ROOT="$SDK"
export ANDROID_HOME="$SDK"
echo "Android SDK: $SDK"

# ---------------------------------------------------------------- sdk packages
log "Android platform and build tools"
SDKMANAGER="$(find "$SDK/cmdline-tools" -maxdepth 3 -name sdkmanager -type f 2>/dev/null | head -1)"
[ -n "$SDKMANAGER" ] || SDKMANAGER="$(command -v sdkmanager || true)"
[ -n "$SDKMANAGER" ] || { echo "sdkmanager not found." >&2; exit 1; }
yes 2>/dev/null | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" --install "$ANDROID_PLATFORM" "$ANDROID_BUILDTOOLS"

# ---------------------------------------------------------------- gradle
log "Gradle $GRADLE_VERSION"
if [ ! -x "/tmp/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  curl -sSLo /tmp/gradle.zip \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  unzip -q -o /tmp/gradle.zip -d /tmp
fi
GRADLE="/tmp/gradle-$GRADLE_VERSION/bin/gradle"
"$GRADLE" --version | head -5

# ---------------------------------------------------------------- signing
# A release APK needs a key. If the repository carries DRK_KEYSTORE_BASE64 as a secret it
# is used and the build stays installable over a previous one; otherwise a keystore is
# generated for this run and uploaded alongside the APK so it can be saved as that secret.
log "Signing key"
mkdir -p "$ROOT/.keys"
KEYSTORE="$ROOT/.keys/drk-release.jks"
STORE_PASSWORD="${DRK_STORE_PASSWORD:-drkmuqeet}"
KEY_ALIAS="${DRK_KEY_ALIAS:-drk}"

if [ -n "${DRK_KEYSTORE_BASE64:-}" ]; then
  printf '%s' "$DRK_KEYSTORE_BASE64" | base64 -d > "$KEYSTORE"
  echo "Using the keystore from repository secrets."
else
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$STORE_PASSWORD" \
    -keypass "$STORE_PASSWORD" -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=DRKMUQEET, OU=WiFi Control, O=DRK, C=IN"
  echo "Generated a keystore for this run (see the drk-signing-keystore artifact)."
fi

export DRK_STORE_FILE="$KEYSTORE"
export DRK_STORE_PASSWORD="$STORE_PASSWORD"
export DRK_KEY_ALIAS="$KEY_ALIAS"
export DRK_KEY_PASSWORD="${DRK_KEY_PASSWORD:-$STORE_PASSWORD}"

# ---------------------------------------------------------------- build
log "Tests, then release build"
VERSION="$(sed -n "s/.*versionName '\(.*\)'/\1/p" DRKWiFi/app/build.gradle | head -1)"
"$GRADLE" -p DRKWiFi test assembleRelease --no-daemon --stacktrace

APK="DRKWiFi/app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "Build produced no APK at $APK" >&2; exit 1; }

OUT="$ROOT/DRKWiFi-${VERSION}-release.apk"
cp "$APK" "$OUT"

log "Done"
ls -l "$OUT"
echo "APK: $OUT"
echo "$OUT" >> "$GITHUB_STEP_SUMMARY" 2>/dev/null || true
