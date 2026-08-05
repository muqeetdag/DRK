#!/system/bin/sh
MODDIR=${0%/*}
mkdir -p /data/adb/drkmuqeet
chmod 700 /data/adb/drkmuqeet
# Apply a previously selected reversible profile early, if present.
[ -x "$MODDIR/system/bin/drk" ] && "$MODDIR/system/bin/drk" boot >/dev/null 2>&1
