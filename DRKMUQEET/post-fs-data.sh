#!/system/bin/sh
# DRKMUQEET — early boot (post-fs-data): minimal, safe, fast.
# Heavy lifting happens in service.sh via 'drk --boot' once /data is settled.

MODDIR=${0%/*}
DRK_HOME=/data/adb/drk

mkdir -p "$DRK_HOME/logs" "$DRK_HOME/state" 2>/dev/null
chmod 0777 "$DRK_HOME/logs" "$DRK_HOME/state" 2>/dev/null

# Seed config on very first boot if installer could not (edge cases).
if [ ! -f "$DRK_HOME/drk.conf" ] && [ -f "$MODDIR/config/drk.conf" ]; then
  cp -f "$MODDIR/config/drk.conf" "$DRK_HOME/drk.conf" 2>/dev/null
  chmod 0644 "$DRK_HOME/drk.conf" 2>/dev/null
fi

# Early memory tuning from config (applies before zygote).
if [ -f "$DRK_HOME/drk.conf" ]; then
  . "$DRK_HOME/drk.conf" 2>/dev/null
  [ -n "$SWAPPINESS" ] && sysctl -w vm.swappiness="$SWAPPINESS" >/dev/null 2>&1
  [ -n "$VFS_CACHE_PRESSURE" ] && sysctl -w vm.vfs_cache_pressure="$VFS_CACHE_PRESSURE" >/dev/null 2>&1
  [ -n "$DIRTY_RATIO" ] && sysctl -w vm.dirty_ratio="$DIRTY_RATIO" >/dev/null 2>&1
  [ -n "$DIRTY_BG_RATIO" ] && sysctl -w vm.dirty_background_ratio="$DIRTY_BG_RATIO" >/dev/null 2>&1
fi

exit 0
