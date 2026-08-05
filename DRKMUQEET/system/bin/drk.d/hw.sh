#!/system/bin/sh
# DRKMUQEET — hardware control (temperatures, throttling status, voltage, display, haptics)

hw_usage() {
  cat <<'EOF'
HARDWARE:
  drk hw temp <all|cpu|gpu|batt>    read thermal zones (real-time)
  drk hw throttle status            thermal service / throttling state
  drk hw volt <cpu|gpu>             show voltage interfaces (begonia stock: none writable)
  drk hw brightness <0-255>         LCD brightness (verified)
  drk hw night <0|1> [temp]         night mode / blue light filter
  drk hw color <mode>               color mode (standard/vivid/natural/cinema) — OEM-dependent
  drk hw dpi <n>|reset              display density (wm)
  drk hw res <WxH>|reset            display resolution (wm)
  drk hw refresh <hz>               refresh rate hint (panel-capped, verified readback)
  drk hw devfreq list               list devfreq nodes (memory/GPU/bus clocks)
  drk hw devfreq <name> <min|max> <hz>  set a devfreq clock limit (verified)
  drk hw haptic <0|1>               haptic feedback toggle
  drk hw haptic intensity <0-100>   vibration intensity (if kernel exposes it)
EOF
}

hw_main() {
  local a="$1"; [ -n "$a" ] || { hw_usage; return 1; }
  shift
  case "$a" in
    temp) hw_temp "${1:-all}" ;;
    throttle) hw_throttle ;;
    volt) hw_volt "$1" ;;
    brightness) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk hw brightness <0-255>"; return 1; }
      hw_brightness "$1" ;;
    night) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk hw night <0|1> [temp]"; return 1; }
      case "$1" in
        0|1)
          ss secure night_display_activated "$1" "night mode"
          [ -n "$2" ] && ss secure night_display_color_temperature "$2" "night color temperature" ;;
        *) bad "usage: drk hw night <0|1>"; return 1 ;;
      esac ;;
    color) need_root || return 1
      hw_color "$1" ;;
    dpi) need_root || return 1
      case "$1" in
        reset) wm density reset 2>/dev/null && ok "density reset" || bad "wm density reset failed" ;;
        *) [ -n "$1" ] || { bad "usage: drk hw dpi <n>|reset"; return 1; }
          wm density "$1" 2>/dev/null && ok "density set to $1" || bad "wm density failed" ;;
      esac ;;
    res) need_root || return 1
      case "$1" in
        reset) wm size reset 2>/dev/null && ok "resolution reset" || bad "wm size reset failed" ;;
        *) [ -n "$1" ] || { bad "usage: drk hw res <WxH>|reset"; return 1; }
          wm size "$1" 2>/dev/null && ok "resolution set to $1" || bad "wm size failed" ;;
      esac ;;
    refresh) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk hw refresh <hz>"; return 1; }
      hw_refresh "$1" ;;
    devfreq) need_root || return 1
      case "$1" in
        list)
          for d in /sys/class/devfreq/*/name; do
            [ -e "$d" ] || continue
            say "  $(cat "$d" 2>/dev/null): min $(cat "$(dirname "$d")/min_freq" 2>/dev/null) max $(cat "$(dirname "$d")/max_freq" 2>/dev/null) cur $(cat "$(dirname "$d")/cur_freq" 2>/dev/null)"
          done ;;
        *)
          [ -n "$1" ] && [ -n "$2" ] && [ -n "$3" ] || { bad "usage: drk hw devfreq <name> <min|max> <hz>"; return 1; }
          case "$2" in
            min|max) sv "/sys/class/devfreq/$1/$2_freq" "$3" "devfreq $1 $2" ;;
            *) bad "usage: drk hw devfreq <name> <min|max> <hz>"; return 1 ;;
          esac ;;
      esac ;;
    haptic) need_root || return 1
      case "$1" in
        intensity) [ -n "$2" ] || { bad "usage: drk hw haptic intensity <0-100>"; return 1; }
          hw_haptic_intensity "$2" ;;
        0|1) ss system haptic_feedback_enabled "$1" "haptic feedback" ;;
        *) bad "usage: drk hw haptic <0|1> | drk hw haptic intensity <0-100>"; return 1 ;;
      esac ;;
    *) hw_usage; return 1 ;;
  esac
}

hw_temp() {
  local which="$1" z t type max=0 found=0
  hdr "Temperatures (°C)"
  for z in /sys/class/thermal/thermal_zone*; do
    [ -r "$z/temp" ] || continue
    type=$(cat "$z/type" 2>/dev/null)
    t=$(cat "$z/temp" 2>/dev/null)
    case "$t" in *[!0-9-]*) continue;; esac
    [ -n "$t" ] || continue
    [ "$t" -gt 1000 ] && t=$((t / 1000))
    case "$which" in
      all) say "  $type: ${t}C"; found=$((found+1)) ;;
      cpu) case "$type" in *cpu*|*CPU*|*cluster*|*sensor*) say "  $type: ${t}C"; found=$((found+1));; esac ;;
      gpu) case "$type" in *gpu*|*GPU*) say "  $type: ${t}C"; found=$((found+1));; esac ;;
      batt) case "$type" in *batt*|*BATT*|*pmic*) say "  $type: ${t}C"; found=$((found+1));; esac ;;
    esac
    [ "$t" -gt "$max" ] && max=$t
  done
  if [ "$found" -eq 0 ]; then
    bad "no readable thermal zones (type '$which') on this kernel"
    return 1
  fi
  say "  max: ${max}C"
  [ "$max" -gt 60 ] && warn "high temperature — consider performance preset (drk perf battery)"
}

hw_throttle() {
  hdr "Thermal / throttling state"
  if has dumpsys && dumpsys thermalservice 2>/dev/null | grep -q "ThermalHal"; then
    dumpsys thermalservice 2>/dev/null | head -n 30 | sed 's/^/  /'
  else
    say "  thermalservice not available; showing zones:"
    hw_temp all
  fi
}

hw_volt() {
  local which="$1" f
  hdr "Voltage interfaces ($which)"
  case "$which" in
    cpu)
      for f in /sys/devices/system/cpu/cpu0/cpufreq/vdd_levels /sys/devices/system/cpu/cpu0/cpufreq/UV_mV_table \
               /sys/kernel/cpufreq_voltage /sys/devices/system/cpu/cpufreq/vdd_table; do
        [ -e "$f" ] && say "  found: $f ($(cat "$f" 2>/dev/null | head -n2 | tr '\n' ' '))" || say "  absent: $f"
      done ;;
    gpu)
      for f in /sys/class/kgsl/kgsl-3d0/vdd /sys/class/kgsl/kgsl-3d0/vdd_levels /sys/kernel/gpu_voltage; do
        [ -e "$f" ] && say "  found: $f ($(cat "$f" 2>/dev/null | head -n2 | tr '\n' ' '))" || say "  absent: $f"
      done ;;
    *) bad "usage: drk hw volt <cpu|gpu>"; return 1 ;;
  esac
  warn "begonia stock kernel exposes no writable voltage interface — undervolting requires a custom kernel"
}

hw_brightness() {
  local v="$1" p
  # clamp 0-255
  [ "$v" -ge 0 ] && [ "$v" -le 255 ] || { bad "brightness must be 0-255"; return 1; }
  p=$(ls /sys/class/leds/lcd-backlight/brightness /sys/class/backlight/*/brightness 2>/dev/null | head -n1)
  [ -n "$p" ] && sv "$p" "$v" "LCD brightness"
  ss system screen_brightness "$v" "screen_brightness setting"
}

hw_color() {
  local mode="$1"
  [ -n "$mode" ] || { bad "usage: drk hw color <standard|vivid|natural|cinema>"; return 1; }
  case "$mode" in
    standard|vivid|natural|cinema) ;;
    *) bad "mode must be standard|vivid|natural|cinema"; return 1 ;;
  esac
  # MIUI keeps display color modes under display_color_mode (0=default,2=vivid...); values OEM-specific.
  # Try the documented command path first, fall back to the settings key with readback.
  if has cmd && cmd display help 2>/dev/null | grep -q "color"; then
    cmd display set-color-mode "$mode" 2>/dev/null && ok "color mode → $mode (cmd display)" && return 0
  fi
  # MIUI mapping (device-dependent; verified by readback)
  local v
  case "$mode" in standard) v=1;; vivid) v=2;; natural) v=3;; cinema) v=4;; esac
  ssx system display_color_mode "$v" "$v" "display_color_mode ($mode)" \
    || warn "no writable color-mode interface on this ROM (OEM-dependent)"
}

hw_refresh() {
  local hz="$1"
  ss system min_refresh_rate "$hz" "min_refresh_rate"
  ss system max_refresh_rate "$hz" "max_refresh_rate"
  local panel
  panel=$(dumpsys display 2>/dev/null | grep -o "supportedRefreshRates=\[[0-9., ]*\]" | head -n1)
  say "  panel: $panel"
  warn "begonia panel is 60 Hz — values above 60 are ignored by hardware"
}

hw_haptic_intensity() {
  local v="$1" p
  p=$(ls /sys/class/leds/vibrator/intensity /sys/class/leds/vibrator/vmax_mv 2>/dev/null | head -n1)
  [ -n "$p" ] && sv "$p" "$v" "vibrator intensity" || {
    bad "no vibrator intensity interface on this kernel"
    warn "only the haptic toggle is available: drk hw haptic <0|1>"
  }
}

hw_probe() {
  local n=0
  for z in /sys/class/thermal/thermal_zone*; do [ -r "$z/temp" ] && n=$((n+1)); done
  [ "$n" -gt 0 ] && ok "thermal zones ($n)" || bad "thermal zones"
  [ -e /sys/class/leds/lcd-backlight/brightness ] && ok "backlight interface" || warn "backlight interface absent"
  return 0
}

hw_status() {
  hdr "Hardware"
  hw_temp all
  say "  density: $(wm density 2>/dev/null | grep -o 'Override density: [0-9]*' | awk '{print $3}')"
  say "  size: $(wm size 2>/dev/null | grep -o 'Override size: [0-9x]*' | awk '{print $3}')"
}
