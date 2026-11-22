#!/usr/bin/env sh
set -eu

# Default build: the DRK Magisk console module (unchanged).
rm -f DRKMUQEET-v1.0.0.zip
(cd DRKMUQEET && zip -qr ../DRKMUQEET-v1.0.0.zip . -x '*.DS_Store')
echo 'Created DRKMUQEET-v1.0.0.zip'

# Also build the NYX white-label APK when its toolchain is present.
if [ -x nyx/build.sh ]; then
  echo
  echo '--- NYX white-label APK ---'
  sh nyx/build.sh
fi
