#!/system/bin/sh
# Remove only module-owned state. Kernel and Android defaults are restored
# before removal, and both daemons are stopped first so nothing outlives the
# module directory it was launched from.
MODDIR=${0%/*}
DRK="$MODDIR/system/bin/drk"
if [ -x "$DRK" ]; then
  # Release any thermal-guard override while the module still exists, then
  # restore stock values. reset also calls unguard; the explicit call first
  # keeps the log honest about what stopped and in what order.
  "$DRK" unguard >/dev/null 2>&1
  "$DRK" reset >/dev/null 2>&1
fi
STATE=/data/adb/drkmuqeet
[ -s "$STATE/httpd.pid" ] && kill "$(cat "$STATE/httpd.pid")" 2>/dev/null
rm -rf "$STATE"
exit 0
