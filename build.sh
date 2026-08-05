#!/usr/bin/env sh
set -eu
rm -f DRKMUQEET-v1.0.0.zip
(cd DRKMUQEET && zip -qr ../DRKMUQEET-v1.0.0.zip . -x '*.DS_Store')
echo 'Created DRKMUQEET-v1.0.0.zip'
