#!/system/bin/sh
# DRKMUQEET — battery & charging
# All reads come from the real power_supply sysfs; charge limiting is
# kernel-dependent and honestly verified (begonia stock kernel usually lacks
# charge-control nodes — the command reports that, it does not fake it).

BATT=/sys/class/power_supply/battery
USB=/sys/class/power_supply/usb
MAIN=/sys/class/power_supply/main

batt_usage() {
  cat <<'EOF'
BATTERY & CHARGING:
  drk batt info           full battery/charger state (verified live reads)
  drk batt limit <0-100>  charge limit % (only if kernel exposes charge control)
  drk batt speed <fast|slow|default>   charge current hint (kernel-dependent)
  drk batt eta            estimated time to full/empty (computed from live current)
  drk batt cycles         charge cycle count (if exposed)
  drk batt health         capacity vs design + degradation estimate
  drk batt drain <start|stop|show>   drain monitoring via drkd logs
  drk batt calib                    reset battery stats (recalibration)
EOF
}

batt_main() {
  local a="$1"; [ -n "$a" ] || { batt_usage; return 1; }
  shift
  case "$a" in
    info) batt_info ;;
    limit) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk batt limit <0-100>"; return 1; }
      batt_limit "$1" ;;
    speed) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk batt speed <fast|slow|default>"; return 1; }
      batt_speed "$1" ;;
    eta) batt_eta ;;
    cycles) batt_cycles ;;
    health) batt_health ;;
    drain) batt_drain "$1" ;;
    calib) need_root || return 1; batt_calib ;;
    *) batt_usage; return 1 ;;
  esac
}

batt_calib() {
  local f
  for f in /data/system/batterystats.bin /data/system/batterystats-checkin.bin; do
    if [ -f "$f" ]; then
      rm -f "$f" && ok "removed $f" || bad "remove failed for $f"
    fi
  done
  say "  battery stats cleared — recalibration completes after a full charge cycle"
  warn "reboot for the stats service to rebuild cleanly"
}

batt_rd() { # batt_rd <name> [label] — read one battery attr
  local n="$1" lab="${2:-$1}" v
  v=$(cat "$BATT/$n" 2>/dev/null)
  [ -n "$v" ] && say "  $lab: $v"
}

batt_info() {
  hdr "Battery & charging"
  for n in status health capacity voltage_now current_now current_max temp technology \
           charge_full charge_full_design cycle_count capacity_level charge_type; do
    batt_rd "$n"
  done
  [ -d "$USB" ] && for n in present type voltage_now current_now input_current_limit; do
    v=$(cat "$USB/$n" 2>/dev/null); [ -n "$v" ] && say "  usb/$n: $v"
  done
  batt_eta
}

batt_limit() {
  local pct="$1" p
  p=$(ls /sys/class/power_supply/battery/charge_control_limit \
         /sys/class/power_supply/battery/charge_control_limit_max \
         /sys/class/power_supply/battery/battery_charging_enabled 2>/dev/null | head -n1)
  if [ -z "$p" ]; then
    bad "no charge-control interface on this kernel"
    warn "begonia stock kernel does not expose charge limiting — requires a custom kernel"
    return 1
  fi
  case "$p" in
    *battery_charging_enabled)
      # 0 = stop charging entirely, 1 = charge
      [ "$pct" = "0" ] && sv "$p" 0 "charging enabled" || sv "$p" 1 "charging enabled" ;;
    *charge_control_limit*)
      sv "$p" "$pct" "charge limit" ;;
  esac
}

batt_speed() {
  local mode="$1" p v
  case "$mode" in
    fast) v=3000000 ;;
    slow) v=500000 ;;
    default) v=0 ;;
    *) bad "usage: drk batt speed <fast|slow|default>"; return 1 ;;
  esac
  p=$(ls /sys/class/power_supply/battery/input_current_limit \
         /sys/class/power_supply/usb/input_current_limit \
         /sys/class/power_supply/battery/constant_charge_current_max \
         /sys/class/power_supply/main/constant_charge_current_max 2>/dev/null | head -n1)
  if [ -n "$p" ]; then
    sv "$p" "$v" "charge current"
  else
    bad "no charge-current interface on this kernel"
    warn "begonia stock kernel does not expose charge speed control"
    return 1
  fi
}

batt_eta() {
  local level volt curr_uA curr_A full mAh draw_mA hours
  level=$(cat "$BATT/capacity" 2>/dev/null); [ -n "$level" ] || level=0
  curr_uA=$(cat "$BATT/current_now" 2>/dev/null); [ -n "$curr_uA" ] || curr_uA=0
  curr_A=$(awk "BEGIN{printf \"%.3f\", $curr_uA/1000000}")
  case "$curr_uA" in
    -*)
      # discharging: estimate time to empty from current draw
      curr_uA=$((0 - curr_uA))
      [ "$curr_uA" -gt 0 ] 2>/dev/null || { say "  eta: n/a (no current reading)"; return 0; }
      full=$(cat "$BATT/charge_full" 2>/dev/null); [ -n "$full" ] || full=4500000
      mAh=$(awk "BEGIN{printf \"%.0f\", $level/100*$full/1000}")
      draw_mA=$(awk "BEGIN{printf \"%.0f\", $curr_uA/1000}")
      [ "$draw_mA" -gt 0 ] 2>/dev/null || { say "  eta: n/a (no draw reading)"; return 0; }
      hours=$(awk "BEGIN{printf \"%.1f\", $mAh/$draw_mA}")
      say "  eta to empty: ~${hours}h (level ${level}%, draw ${curr_A}A)"
      ;;
    0) say "  eta: not charging/discharging (idle)" ;;
    *)
      full=$(cat "$BATT/charge_full" 2>/dev/null); [ -n "$full" ] || full=4500000
      mAh=$(awk "BEGIN{printf \"%.0f\", (100-$level)/100*$full/1000}")
      draw_mA=$(awk "BEGIN{printf \"%.0f\", $curr_uA/1000}")
      [ "$draw_mA" -gt 0 ] 2>/dev/null || { say "  eta: n/a (no charge reading)"; return 0; }
      hours=$(awk "BEGIN{printf \"%.1f\", $mAh/$draw_mA}")
      say "  eta to full: ~${hours}h (level ${level}%, charge ${curr_A}A)"
      ;;
  esac
}

batt_cycles() {
  local c
  c=$(cat "$BATT/cycle_count" 2>/dev/null)
  [ -n "$c" ] && say "  charge cycles: $c" || { bad "cycle_count not exposed by this kernel"; return 1; }
}

batt_health() {
  local full design pct
  full=$(cat "$BATT/charge_full" 2>/dev/null)
  design=$(cat "$BATT/charge_full_design" 2>/dev/null)
  [ -n "$full" ] && [ -n "$design" ] && [ "$design" -gt 0 ] 2>/dev/null || {
    bad "charge_full/charge_full_design not exposed"; return 1
  }
  pct=$(awk "BEGIN{printf \"%.1f\", $full*100/$design}")
  say "  charge_full: $full µAh | design: $design µAh | health: ${pct}%"
  awk "BEGIN{ if ($pct >= 90) exit 0; else if ($pct >= 80) exit 1; else exit 2 }"
  case $? in
    0) say "  verdict: good" ;;
    1) warn "verdict: acceptable — consider battery service" ;;
    2) warn "verdict: degraded — battery replacement recommended" ;;
  esac
}

batt_drain() {
  local log="$DRK_LOGS/battery.csv"
  case "$1" in
    start)
      [ -f "$DRK_STATE/drkd.pid" ] && kill -0 "$(cat "$DRK_STATE/drkd.pid")" 2>/dev/null \
        && ok "drkd already running (battery sampling active)" \
        || { bad "drkd not running — restart module or launch drkd"; return 1; }
      ;;
    stop)
      [ -f "$DRK_STATE/drkd.pid" ] && kill "$(cat "$DRK_STATE/drkd.pid")" 2>/dev/null && ok "drkd stopped" \
        || bad "drkd not running"
      ;;
    show)
      [ -f "$log" ] && { say "  last 10 samples:"; tail -n 10 "$log" | sed 's/^/    /'; } \
        || { bad "no battery log yet (drkd samples every 5 min)"; return 1; }
      ;;
    *) bad "usage: drk batt drain <start|stop|show>"; return 1 ;;
  esac
}

batt_probe() {
  [ -d "$BATT" ] && ok "battery power_supply interface" || bad "battery interface"
  return 0
}

batt_status() {
  hdr "Battery"
  say "  level: $(cat "$BATT/capacity" 2>/dev/null)% | status: $(cat "$BATT/status" 2>/dev/null) | temp: $(cat "$BATT/temp" 2>/dev/null)"
}
