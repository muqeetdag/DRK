#!/usr/bin/env bash
# NYX — White-label build & sign script
# ------------------------------------------------------------------
# Builds a signed, installable APK from this source tree without Gradle.
# Toolchain (all fetched from public mirrors, cached in .build/tools):
#   * Temurin JRE                 (python package "jdk4py" on PyPI)
#   * aapt2 static linux-x86_64   (npm package "aaptjs3")
#   * android.jar API 35 + d8 + apksigner + ECJ
#                                 (npm package "@drxiaozhi/minapk" tools/)
#   * Pillow                      (PyPI) — PNG icon rendering
#
# Output: dist/nyx-<version>-<flavor>.apk
#
# White-label: every customisation lives in white-label.json (see
# nyx/white-label.example.json). Nothing is hardcoded into the APK.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

BUILD="$ROOT/.build"
TOOLS="$BUILD/tools"
WORK="$BUILD/work"
DIST="$ROOT/dist"
CONFIG="${NYX_CONFIG:-$ROOT/white-label.json}"
[ -f "$CONFIG" ] || CONFIG="$ROOT/white-label.example.json"

# Android 5.0 (API 21) and up. Anything higher silently refuses to install on
# older phones, which is exactly the "it crashes on my device" report.
MIN_SDK=21
TARGET_SDK=35
JAVA_OPTS_JAR=(-Xmx900m)

log() { printf '\033[38;5;141m▸\033[0m %s\n' "$*"; }
die() { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "missing command: $1"; }

need curl; need unzip; need zip; need python3

# ---------------------------------------------------------------- tools
fetch_tools() {
  mkdir -p "$TOOLS"
  local JRE="$TOOLS/jre"

  if [ ! -x "$JRE/bin/java" ]; then
    log "Fetching JDK (Temurin via jdk4py / PyPI)"
    python3 -m pip install --quiet --disable-pip-version-check --target "$BUILD/pyjdk" jdk4py
    rm -rf "$JRE" && mv "$BUILD/pyjdk/jdk4py/java-runtime" "$JRE" && rm -rf "$BUILD/pyjdk"
  fi

  if [ ! -x "$TOOLS/aapt2" ]; then
    log "Fetching aapt2 (aaptjs3 / npm)"
    ( cd "$BUILD" && curl -fsSL "$(npm view aaptjs3 dist.tarball 2>/dev/null || echo https://registry.npmjs.org/aaptjs3/-/aaptjs3-2.0.2.tgz)" -o aaptjs3.tgz )
    rm -rf "$BUILD/aaptjs3" && mkdir -p "$BUILD/aaptjs3"
    tar xzf "$BUILD/aaptjs3.tgz" -C "$BUILD/aaptjs3"
    cp "$BUILD/aaptjs3/package/bin/x64/linux/aapt2" "$TOOLS/aapt2"
    chmod +x "$TOOLS/aapt2"
  fi

  if [ ! -f "$TOOLS/android.jar" ] || [ ! -f "$TOOLS/d8.jar" ] || [ ! -f "$TOOLS/apksigner.jar" ] || [ ! -f "$TOOLS/ecj.jar" ]; then
    log "Fetching android.jar + d8 + apksigner + ECJ (@drxiaozhi/minapk / npm)"
    ( cd "$BUILD" && curl -fsSL "$(npm view @drxiaozhi/minapk dist.tarball 2>/dev/null || echo https://registry.npmjs.org/@drxiaozhi/minapk/-/minapk-0.5.1.tgz)" -o minapk.tgz )
    rm -rf "$BUILD/minapk" && mkdir -p "$BUILD/minapk"
    tar xzf "$BUILD/minapk.tgz" -C "$BUILD/minapk"
    cp "$BUILD/minapk/package/tools/android.jar"     "$TOOLS/android.jar"
    cp "$BUILD/minapk/package/tools/d8.jar"          "$TOOLS/d8.jar"
    cp "$BUILD/minapk/package/tools/apksigner.jar"   "$TOOLS/apksigner.jar"
    cp "$BUILD/minapk/package/tools/ecj-3.45.0.jar"  "$TOOLS/ecj.jar"
  fi

  if [ ! -f "$TOOLS/android-compile.jar" ]; then
    log "Preparing compile classpath (android.jar minus java.* boot classes)"
    rm -rf "$BUILD/aj" && mkdir -p "$BUILD/aj"
    ( cd "$BUILD/aj" && unzip -q "$TOOLS/android.jar" )
    rm -rf "$BUILD/aj/java" "$BUILD/aj/javax"
    ( cd "$BUILD/aj" && zip -qr "$TOOLS/android-compile.jar" . )
    rm -rf "$BUILD/aj"
  fi

  if ! PYTHONPATH="$BUILD/py" python3 -c 'import PIL' >/dev/null 2>&1; then
    log "Fetching Pillow (PyPI)"
    python3 -m pip install --quiet --disable-pip-version-check --target "$BUILD/py" pillow
  fi
}

JAVA="$TOOLS/jre/bin/java"
PY="PYTHONPATH=$BUILD/py python3"

# ------------------------------------------------------- config + icons
configure() {
  mkdir -p "$WORK"
  log "Applying white-label config: $CONFIG"
  PYTHONPATH="$BUILD/py" python3 "$ROOT/tools/configure.py" "$CONFIG" "$ROOT"
  rm -rf "$WORK/app-src"
  cp -r "$ROOT/app" "$WORK/app-src"
  PYTHONPATH="$BUILD/py" python3 "$ROOT/tools/configure.py" "$CONFIG" "$ROOT" --apply
}

gen_icons() {
  log "Rendering app icons"
  PYTHONPATH="$BUILD/py" python3 "$ROOT/tools/gen_icons.py" "$WORK" "$ROOT"
}

# ------------------------------------------------------------------ build
build() {
  local APP="$WORK/app-src"
  mkdir -p "$WORK" "$DIST"
  test -d "$APP" || { echo "work source missing"; exit 1; }

  local RES="$APP/res" MANIFEST="$APP/AndroidManifest.xml" SRC="$APP/src"

  log "aapt2 compile resources"
  "$TOOLS/aapt2" compile --dir "$RES" -o "$WORK/res.zip"

  log "aapt2 link (API $TARGET_SDK)"
  "$TOOLS/aapt2" link \
    -o "$WORK/base.apk" \
    -I "$TOOLS/android.jar" \
    --manifest "$MANIFEST" \
    -A "$APP/assets" \
    -R "$WORK/res.zip" \
    --java "$WORK/gen" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --version-code "$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["versionCode"])' "$WORK/config.json")" \
    --version-name "$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["version"])' "$WORK/config.json")" \
    --no-version-vectors \
    --auto-add-overlay

  log "Compile Java (ECJ, source/target 17)"
  mkdir -p "$WORK/classes"
  "$JAVA" "${JAVA_OPTS_JAR[@]}" -jar "$TOOLS/ecj.jar" \
    -source 17 -target 17 -warn:none \
    -cp "$TOOLS/android-compile.jar" \
    -d "$WORK/classes" \
    $(find "$SRC" "$WORK/gen" "$ROOT/tools/xposed-stubs" -name '*.java')

  log "D8 dex (min-api $MIN_SDK, release mode, full desugaring)"
  mkdir -p "$WORK/dex"
  # Desugaring MUST stay on. With --no-desugaring the Java 8 lambdas compiled by
  # ECJ remain `invoke-custom` instructions, and Dalvik / ART below API 26 aborts
  # the class with NoClassDefFoundError at load time. That is the crash-loop
  # users hit on older phones. D8 rewrites them to synthetic classes here.
  "$JAVA" "${JAVA_OPTS_JAR[@]}" -cp "$TOOLS/d8.jar" com.android.tools.r8.D8 \
    --lib "$TOOLS/android-compile.jar" \
    --min-api "$MIN_SDK" --release \
    --output "$WORK/dex" \
    $(find "$WORK/classes" -name '*.class')

  # Fail the build if any CPU-version-incompatible opcode survived.
  "$JAVA" -cp "$TOOLS/d8.jar" com.android.tools.r8.D8 --version >/dev/null 2>&1 || true
  if python3 "$ROOT/tools/check_dex.py" "$WORK/dex/classes.dex" "$MIN_SDK"; then
    log "dex opcode check passed (no invoke-custom for min-api $MIN_SDK)"
  else
    die "dex contains opcodes that crash on API < 26"
  fi

  log "Package APK"
  cp "$WORK/base.apk" "$WORK/unsigned.apk"
  ( cd "$WORK/dex" && zip -q "$WORK/unsigned.apk" classes.dex )

  # ------------------------------------------------------------ signing
  # One key per flavor: a different store password must never be forced onto
  # an existing keystore, and cross-flavor updates must not collide.
  local FLAVOR KS PASS ALIAS
  FLAVOR="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["flavor"])' "$WORK/config.json")"
  KS="$ROOT/keystore/nyx-$FLAVOR.jks"
  PASS="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["storePassword"])' "$WORK/config.json")"
  ALIAS="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["keyAlias"])' "$WORK/config.json")"
  if [ -f "$ROOT/keystore/nyx-release.jks" ] && [ ! -f "$KS" ]; then
    mv "$ROOT/keystore/nyx-release.jks" "$ROOT/keystore/nyx-obsidian.jks"
  fi
  if [ ! -f "$KS" ]; then
    log "Generating keystore for flavor '$FLAVOR' (store once — never commit)"
    mkdir -p "$ROOT/keystore"
    "$TOOLS/jre/bin/keytool" -genkeypair -keystore "$KS" \
      -storepass "$PASS" -keypass "$PASS" \
      -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
      -dname "CN=NYX White-Label, OU=Release, O=NYX, C=IN" >/dev/null 2>&1 \
      || die "keytool failed"
  fi

  log "Sign (v1 + v2 + v3)"
  local NAME
  NAME="$(python3 -c 'import json,sys;c=json.load(open(sys.argv[1]));print("nyx-"+c["version"]+"-"+c["flavor"]+".apk")' "$WORK/config.json")"
  "$JAVA" "${JAVA_OPTS_JAR[@]}" -jar "$TOOLS/apksigner.jar" sign \
    --ks "$KS" --ks-pass "pass:$PASS" --key-pass "pass:$PASS" --ks-key-alias "$ALIAS" \
    --min-sdk-version "$MIN_SDK" \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "$DIST/$NAME" "$WORK/unsigned.apk" 2>&1 | grep -vE '^WARNING|^$' || true

  log "Verify signature"
  if ! "$JAVA" "${JAVA_OPTS_JAR[@]}" -jar "$TOOLS/apksigner.jar" verify --print-certs "$DIST/$NAME" \
      2>&1 | grep -vE 'WARNING' | head -4; then
    die "signature verification failed"
  fi

  printf '\n\033[38;5;141m✓ Built %s (%s)\033[0m\n' "$DIST/$NAME" "$(du -h "$DIST/$NAME" | cut -f1)"
  cp "$WORK/config.json" "$DIST/${NAME%.apk}.json"
}

fetch_tools
configure
gen_icons
build
