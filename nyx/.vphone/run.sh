#!/usr/bin/env bash
# Runs the shipped APK inside the in-terminal virtual phone and writes
# artifacts/*.png. No emulator, no KVM, no device required.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NYX="$(dirname "$HERE")"
JAVA_HOME_DIR="${NYX}/.build/tools/jre"
JAVA="${JAVA_HOME_DIR}/bin/java"
ECJ="${NYX}/.build/tools/ecj.jar"
APK="${NYX}/dist/nyx-1.3.0-obsidian.apk"
APP_SRC="${NYX}/.build/work/app-src/src"
GEN="${NYX}/.build/work/gen"

if [ ! -x "$JAVA" ] || [ ! -f "$ECJ" ]; then
  echo "toolchain missing — run 'bash ../build.sh' once first" >&2
  exit 1
fi
if [ ! -f "$APK" ]; then
  echo "APK missing — run 'bash ../build.sh' first" >&2
  exit 1
fi

cd "$HERE"
echo "▸ compile framework"
rm -rf fw/out && mkdir -p fw/out
"$JAVA" -jar "$ECJ" -source 17 -target 17 -nowarn -d fw/out \
  $(find fw/src -name '*.java')

echo "▸ compile app classes (for the runtime)"
rm -rf app/out && mkdir -p app/out
"$JAVA" -jar "$ECJ" -source 17 -target 17 -nowarn -d app/out -cp fw/out \
  $(find "$APP_SRC" "$GEN" -name '*.java')

echo "▸ drive the phone"
rm -rf run
"$JAVA" -Djava.awt.headless=true -cp fw/out:app/out com.nyx.vphone.VPhone \
  "$APK" artifacts
