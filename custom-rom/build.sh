#!/usr/bin/env bash
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="$ROOT/custom-rom/manifests/begonia.xml"

cmd="${1:-help}"
case "$cmd" in
  sync)
    echo "==> DRK Custom ROM sync — begonia / Lineage 20"
    echo "Need ~200GB and 'repo' tool installed."
    if [ ! -d ".repo" ]; then
      repo init -u https://github.com/LineageOS/android.git -b lineage-20.0 --git-lfs
      mkdir -p .repo/local_manifests
      cp "$MANIFEST" .repo/local_manifests/begonia.xml
    fi
    repo sync -c -j$(nproc --all) --force-sync --no-clone-bundle --no-tags
    echo "Sync done. Next: ./begonia/extract-files.sh && source build/envsetup.sh && lunch lineage_begonia-userdebug && mka bacon"
    ;;
  lunch)
    source build/envsetup.sh
    lunch lineage_begonia-userdebug
    ;;
  clean)
    mka clean
    ;;
  help|*)
    echo "Usage: ./custom-rom/build.sh {sync|lunch|clean}"
    echo "  sync  — repo init + sync with begonia manifest"
    echo "  lunch — setup env for begonia (run after sync)"
    ;;
esac
