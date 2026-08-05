#!/system/bin/sh
# DRKMUQEET — display engine (modes, dark mode, brightness, rotation, gestures, doze)

disp_usage() {
  cat <<'EOF'
DISPLAY:
  drk disp mode <standard|vivid|natural|cinema>   color mode (OEM-dependent)
  drk disp dark <0|1|2>        dark mode (0 off, 1 on, 2 auto)
  drk disp autobright <0|1>    auto-brightness
  drk disp blue <0|1>          blue light filter (alias of night mode)
  drk disp rotate <auto|0|90|180|270>   rotation control
  drk disp gesture <0|1|2>     nav mode (0 3-button, 2 gesture) — OEM-dependent
  drk disp saver <0|1>         screen saver
  drk disp ambient <0|1>       doze / ambient display
  drk disp touch <sensitive|glove|normal>   touch mode (MIUI keys, try-verify)
  drk disp hdr status          panel HDR capability report
EOF
}

disp_main() {
  local a="$1"; [ -n "$a" ] || { disp_usage; return 1; }
  shift
  case "$a" in
    mode) need_root || return 1; hw_color "$1" ;;
    dark) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk disp dark <0|1|2>"; return 1; }
      case "$1" in 0|1|2) ss secure ui_night_mode "$1" "dark mode" ;;
      *) bad "usage: drk disp dark <0|1|2>"; return 1 ;; esac ;;
    autobright) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk disp autobright <0|1>"; return 1; }
      case "$1" in 0|1) ss system screen_brightness_mode "$1" "auto brightness" ;;
      *) bad "usage: drk disp autobright <0|1>"; return 1 ;; esac ;;
    blue) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk disp blue <0|1>"; return 1; }
      case "$1" in 0|1) ss secure night_display_activated "$1" "blue light filter" ;;
      *) bad "usage: drk disp blue <0|1>"; return 1 ;; esac ;;
    rotate) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk disp rotate <auto|0|90|180|270>"; return 1; }
      case "$1" in
        auto) ss system accelerometer_rotation 1 "auto-rotate" ;;
        0|90|180|270)
          ss system accelerometer_rotation 0 "auto-rotate"
          ss system user_rotation "$1" "rotation" ;;
        *) bad "usage: drk disp rotate <auto|0|90|180|270>"; return 1 ;;
      esac ;;
    gesture) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk disp gesture <0|1|2>"; return 1; }
      case "$1" in 0|1|2)
        ssx secure navigation_mode "$1" "$1" "navigation mode" \
          || warn "navigation_mode not honored by this ROM (MIUI uses its own setting)" ;;
      *) bad "usage: drk disp gesture <0|1|2>"; return 1 ;; esac ;;
    saver) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk disp saver <0|1>"; return 1; }
      case "$1" in 0|1) ss secure screensaver_enabled "$1" "screen saver" ;;
      *) bad "usage: drk disp saver <0|1>"; return 1 ;; esac ;;
    ambient) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk disp ambient <0|1>"; return 1; }
      case "$1" in 0|1)
        ss secure doze_enabled "$1" "doze (ambient)"
        warn "begonia is an LCD panel — ambient/always-on display may not render" ;;
      *) bad "usage: drk disp ambient <0|1>"; return 1 ;; esac ;;
    touch) need_root || return 1
      case "$1" in
        sensitive)
          ssx system glove_mode 1 1 "glove mode (MIUI)"
          ssx system touch_sensitivity 1 1 "touch sensitivity (MIUI)" ;;
        glove)
          ssx system glove_mode 1 1 "glove mode (MIUI)" ;;
        normal)
          ssx system glove_mode 0 0 "glove mode (MIUI)"
          ssx system touch_sensitivity 0 0 "touch sensitivity (MIUI)" ;;
        *) bad "usage: drk disp touch <sensitive|glove|normal>"; return 1 ;;
      esac ;;
    hdr) disp_hdr_status ;;
    *) disp_usage; return 1 ;;
  esac
}

disp_hdr_status() {
  hdr "HDR status"
  dumpsys display 2>/dev/null | grep -iE "hdr|wcg|color" | head -n 10 | sed 's/^/  /'
  say "  note: begonia's panel is HDR10-capable but most MIUI builds expose it as SDR"
}

disp_probe() {
  has dumpsys && ok "display dump available" || warn "dumpsys"
  return 0
}

disp_status() {
  hdr "Display"
  say "  dark mode: $(settings get secure ui_night_mode 2>/dev/null)"
  say "  auto-bright: $(settings get system screen_brightness_mode 2>/dev/null)"
  say "  night: $(settings get secure night_display_activated 2>/dev/null)"
  say "  nav mode: $(settings get secure navigation_mode 2>/dev/null)"
}
