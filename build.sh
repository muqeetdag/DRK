#!/usr/bin/env sh
set -eu
rm -f DRKMUQEET-v1.0.0.zip
(cd DRKMUQEET && zip -qr ../DRKMUQEET-v1.0.0.zip . -x '*.DS_Store')
echo 'Created DRKMUQEET-v1.0.0.zip'

# Build APK via pure-Python toolchain (no Android SDK required)
if command -v python3 >/dev/null 2>&1; then
  echo 'Building minimal APK via Python toolchain...'
  (cd apk_builder && python3 build_manifest.py && python3 dexgen_full.py && python3 build_apk.py)
  cp apk_builder/DRK-Device-Tuning.apk ./DRK-Device-Tuning-v1.0.0.apk
  echo 'Created DRK-Device-Tuning-v1.0.0.apk (WebView wrapper to 127.0.0.1:8080)'
  ls -lh DRK-*.apk
else
  echo 'python3 not found, skipping APK build'
fi

