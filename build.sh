#!/usr/bin/env sh
set -eu
# Version comes from module.prop so the ZIP name can never drift from it.
VERSION=$(sed -n 's/^version=//p' DRKMUQEET/module.prop)
[ -n "$VERSION" ] || { echo 'module.prop has no version=' >&2; exit 1; }
OUT="DRKMUQEET-v${VERSION}.zip"
rm -f DRKMUQEET-v*.zip
(cd DRKMUQEET && zip -qr "../$OUT" . -x '*.DS_Store')
echo "Created $OUT"
