#!/system/bin/sh
MODDIR=${0%/*}
# Wait for Android services, then start a localhost-only dashboard.
sleep 15
"$MODDIR/system/bin/drk" serve >/dev/null 2>&1 &
