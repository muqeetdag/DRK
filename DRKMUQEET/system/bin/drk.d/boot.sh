#!/system/bin/sh
# DRKMUQEET — boot-time configuration (drk --boot), called by service.sh
# Reads /data/adb/drk/drk.conf and applies every set value with verification.
# Output goes to /data/adb/drk/logs/boot.log.

boot_main() {
  mkdir -p "$DRK_LOGS" "$DRK_STATE" 2>/dev/null
  # if config missing (module freshly updated), seed from module config
  if [ ! -f "$DRK_CONF" ] && [ -f /system/bin/drk.d/../config/drk.conf ]; then
    cp -f /system/bin/drk.d/../config/drk.conf "$DRK_CONF" 2>/dev/null
  fi
  [ -f "$DRK_CONF" ] || { bad "no config at $DRK_CONF — run: drk config show"; return 1; }
  . "$DRK_CONF" 2>/dev/null

  {
    echo "===== DRKMUQEET boot $(date) ====="
    DRK_QUIET=0 boot_apply
  } >> "$DRK_LOGS/boot.log" 2>&1
  echo "boot applied — full log: $DRK_LOGS/boot.log"
  # prune boot log
  [ "$(wc -l < "$DRK_LOGS/boot.log" 2>/dev/null)" -gt 400 ] && tail -n 200 "$DRK_LOGS/boot.log" > "$DRK_LOGS/boot.log.tmp" && mv "$DRK_LOGS/boot.log.tmp" "$DRK_LOGS/boot.log"
  return 0
}

boot_apply() {
  # --- CPU ---
  [ -n "$CPU_GOVERNOR" ] && set_all_cpus scaling_governor "$CPU_GOVERNOR" "CPU governor"
  [ -n "$CPU_MIN_FREQ" ] && set_all_cpus scaling_min_freq "$CPU_MIN_FREQ" "CPU min freq"
  [ -n "$CPU_MAX_FREQ" ] && set_all_cpus scaling_max_freq "$CPU_MAX_FREQ" "CPU max freq"
  case "$CPU_BOOST" in 1|0)
    try_sv "$CPU_BOOST" "input boost" /sys/module/cpu_boost/parameters/input_boost_enabled
  esac

  # --- GPU ---
  [ -n "$GPU_GOVERNOR" ] && try_sv "$GPU_GOVERNOR" "GPU governor" /sys/class/kgsl/kgsl-3d0/devfreq/governor
  [ -n "$GPU_MAX_FREQ" ] && try_sv "$GPU_MAX_FREQ" "GPU max freq" /sys/class/kgsl/kgsl-3d0/devfreq/max_freq

  # --- Memory ---
  [ -n "$SWAPPINESS" ] && sc vm.swappiness "$SWAPPINESS" "swappiness"
  [ -n "$VFS_CACHE_PRESSURE" ] && sc vm.vfs_cache_pressure "$VFS_CACHE_PRESSURE" "vfs_cache_pressure"
  [ -n "$DIRTY_RATIO" ] && sc vm.dirty_ratio "$DIRTY_RATIO" "dirty_ratio"
  [ -n "$DIRTY_BG_RATIO" ] && sc vm.dirty_background_ratio "$DIRTY_BG_RATIO" "dirty_background_ratio"
  [ -n "$MIN_FREE_KBYTES" ] && scn vm.min_free_kbytes "$MIN_FREE_KBYTES" "$MIN_FREE_KBYTES" "min_free_kbytes"
  case "$ZSWAP" in 1|0) try_sv "$ZSWAP" "zswap" /sys/module/zswap/parameters/enabled ;; esac
  case "$KSM" in 1|0) try_sv "$KSM" "KSM" /sys/kernel/mm/ksm/run ;; esac
  case "$ZRAM_SIZE_MB" in
    ''|0) ;;
    *) mem_zram_on "$ZRAM_SIZE_MB" "$ZRAM_ALGO" ;;
  esac

  # --- Storage ---
  [ -n "$READ_AHEAD_KB" ] && fs_readahead "$READ_AHEAD_KB"
  [ -n "$IO_SCHEDULER" ] && fs_sched "$IO_SCHEDULER"
  case "$FSTRIM_BOOT" in 1)
    if has fstrim; then
      fs_trim 2>/dev/null || warn "fstrim skipped (busy?)"
    fi
  esac

  # --- Network ---
  if [ -n "$TCP_CONGESTION" ]; then
    local avail
    avail=$(cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null)
    case " $avail " in
      *" $TCP_CONGESTION "*) sc net.ipv4.tcp_congestion_control "$TCP_CONGESTION" "tcp_congestion_control" ;;
      *) warn "tcp '$TCP_CONGESTION' unavailable (kernel has: $avail)" ;;
    esac
  fi
  [ -n "$TCP_FASTOPEN" ] && sc net.ipv4.tcp_fastopen "$TCP_FASTOPEN" "tcp_fastopen"

  # --- Privacy ---
  case "$ENABLE_DENYLIST" in 1)
    has magisk && magisk --denylist enable 2>/dev/null && ok "denylist enabled" || warn "denylist enable failed"
  esac
  case "$DEVICE_SPOOF" in 1)
    if [ -n "$SPOOF_FINGERPRINT" ]; then
      priv_spoof 1 begonia >/dev/null 2>&1 || true
      resetprop -n ro.build.fingerprint "$SPOOF_FINGERPRINT" 2>/dev/null
      ok "fingerprint spoofed: $(getprop ro.build.fingerprint)"
    else
      priv_spoof 1 pixel >/dev/null 2>&1 || true
      ok "device spoofed (pixel preset)"
    fi
  esac

  # --- Battery ---
  case "$CHARGE_LIMIT" in
    ''|0) ;;
    *) batt_limit "$CHARGE_LIMIT" ;;
  esac

  # --- Housekeeping ---
  case "$LOG_CLEAR_BOOT" in 1)
    logcat -c 2>/dev/null && ok "logcat cleared" || true
  esac

  ok "DRKMUQEET boot config applied"
}
