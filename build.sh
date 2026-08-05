#!/bin/sh
# Build DRKMUQEET.zip (flashable Magisk module) from ./DRKMUQEET
# Magisk requires module.prop at the ZIP ROOT — so we zip the
# contents of the module directory, not the directory itself.
set -e
cd "$(dirname "$0")"

for f in DRKMUQEET/system/bin/drk DRKMUQEET/system/bin/drkd; do
  [ -x "$f" ] || { echo "error: $f missing or not executable — run: chmod +x $f"; exit 1; }
done
[ -f DRKMUQEET/module.prop ] || { echo "error: module.prop missing"; exit 1; }

rm -f DRKMUQEET.zip
cd DRKMUQEET
if command -v zip >/dev/null 2>&1; then
  zip -r9 ../DRKMUQEET.zip . -x '*.git*' >/dev/null
elif command -v python3 >/dev/null 2>&1; then
  python3 - <<'EOF'
import shutil
shutil.make_archive('../DRKMUQEET', 'zip', '.')
EOF
else
  echo "error: need 'zip' or 'python3'"; exit 1
fi
cd ..

# sanity: module.prop must be at zip root
if command -v unzip >/dev/null 2>&1; then
  unzip -l DRKMUQEET.zip | grep -q "module.prop" || { echo "error: module.prop not at zip root"; exit 1; }
fi

echo "Built $(pwd)/DRKMUQEET.zip ($(du -h DRKMUQEET.zip | cut -f1))"
echo "Flash in the Magisk app, then run:  su -c 'drk help'"
