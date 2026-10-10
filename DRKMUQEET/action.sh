#!/system/bin/sh
MODDIR=${0%/*}
DRK="$MODDIR/system/bin/drk"
STATE=/data/adb/drkmuqeet

"$DRK" toggle

PROFILE=$(cat "$STATE/profile" 2>/dev/null || echo default)
GUARD=$(cat "$STATE/guard" 2>/dev/null)
if [ -n "$GUARD" ]; then
  # The profile the user chose and the override the guard is holding are two
  # different facts. Show both, or the toast reads as a lie about the clocks.
  toast "DRKMUQEET: ${PROFILE} selected, thermal guard holding at ${GUARD}"
else
  toast "DRKMUQEET: ${PROFILE} profile active"
fi
