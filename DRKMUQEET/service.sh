#!/system/bin/sh
MODDIR=${0%/*}
DRK="$MODDIR/system/bin/drk"

# Wait for Android to report boot completion instead of guessing at a fixed
# delay. A blind `sleep 15` fires too early on a slow boot (settings provider
# not up yet, so animation scales silently fail to apply) and wastes 15 seconds
# on a fast one. Cap the wait so the dashboard still comes up if the property
# never lands.
i=0
while [ "$i" -lt 120 ]; do
  [ "$(getprop sys.boot_completed 2>/dev/null)" = 1 ] && break
  sleep 1
  i=$((i + 1))
done

# Both self-background and record their own pidfiles.
"$DRK" serve >/dev/null 2>&1
"$DRK" guard >/dev/null 2>&1
