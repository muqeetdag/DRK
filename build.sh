#!/usr/bin/env sh
set -eu
VERSION=$(sed -n 's/^version=//p' DRKMUQEET/module.prop)
ZIP="DRKMUQEET-v${VERSION}.zip"
rm -f "$ZIP"
(cd DRKMUQEET && zip -qr "../$ZIP" . -x '*.DS_Store')
echo "Created $ZIP"
