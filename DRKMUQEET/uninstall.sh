#!/system/bin/sh
# Remove only module-owned state. Kernel and Android defaults are restored before removal.
MODDIR=${0%/*}
[ -x "$MODDIR/system/bin/drk" ] && "$MODDIR/system/bin/drk" reset >/dev/null 2>&1
rm -rf /data/adb/drkmuqeet
