#!/usr/bin/env bash
# Extract vendor blobs for begonia — run on build machine with device connected or with MIUI zip
set -eu
DEVICE=begonia
VENDOR=xiaomi
MY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_ROOT="${MY_DIR}/../../.."

if [ ! -f "${ANDROID_ROOT}/system/build.prop" ]; then
  echo "Run this from Android root after 'repo sync'."
  echo "Or set ANDROID_ROOT to your AOSP checkout."
  exit 1
fi

# Uses the standard Lineage extract script from device/xiaomi/begonia
EXTRACT="${ANDROID_ROOT}/device/${VENDOR}/${DEVICE}/extract-files.sh"
if [ -x "$EXTRACT" ]; then
  "$EXTRACT" "$@"
else
  echo "Missing ${EXTRACT} — sync device tree first: repo sync device/xiaomi/begonia"
  exit 1
fi

echo "Done. Blobs in vendor/${VENDOR}/${DEVICE}/"
