#!/system/bin/sh
# DRKMUQEET — late boot: apply all configured settings, start monitor daemon.

MODDIR=${0%/*}
DRK_HOME=/data/adb/drk

# Wait for the system to settle a bit so sysfs/power interfaces are up.
sleep 5

# Apply full configuration (idempotent, verified, logged to boot.log).
if [ -x "$MODDIR/system/bin/drk" ]; then
  DRK_QUIET=1 "$MODDIR/system/bin/drk" --boot 2>/dev/null
fi

# Start the monitor daemon (battery sampling, temp alerts, weekly fstrim).
if [ -x "$MODDIR/system/bin/drkd" ]; then
  nohup "$MODDIR/system/bin/drkd" >/dev/null 2>&1 &
fi

exit 0
