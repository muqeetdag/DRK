#!/system/bin/sh
MODDIR=${0%/*}
"$MODDIR/system/bin/drk" toggle
PROFILE=$(cat /data/adb/drkmuqeet/profile 2>/dev/null || echo balanced)
toast "DRKMUQEET: ${PROFILE} profile active"
