#!/usr/bin/env bash
# setup.sh — clone HackiOS as one tree for begonia iOS work
# Credits: @OnyxAsh + upstream oosexgang/hackios-xnu-*
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
DEST="$HERE/hackios"

if [ -d "$DEST/.git" ]; then
  echo "hackios/ already exists at $DEST"
  echo "Update with: cd $DEST && git pull && common/setup.sh"
  exit 0
fi

echo "==> Cloning hackios-xnu-kernel (will pull userland via common/setup.sh)"
git clone https://github.com/oosexgang/hackios-xnu-kernel.git "$DEST"
cd "$DEST"

if [ -x common/setup.sh ]; then
  echo "==> Running common/setup.sh to pull hackios-xnu-userland into same tree"
  common/setup.sh
  echo "==> Check with: common/ugit status"
else
  echo "WARN: common/setup.sh not found — cloning userland directly"
  git clone https://github.com/oosexgang/hackios-xnu-userland.git .userland
fi

echo "Done. Structure:"
echo "  $DEST/xnu/          # kernel patches + platform"
echo "  $DEST/uefi/          # UEFI port (redwood reference)"
echo "  $DEST/xnuloader/     # EFI payload loader"
echo "  $DEST/common/        # shared scripts"
echo "  $DEST/qemu/          # QEMU virt boot"
echo "  $DEST/userland/      # via ugit (APFS builders, init, inject libs)"
echo "Begonia templates at: $HERE/begonia/"
