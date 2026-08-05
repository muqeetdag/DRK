#!/system/bin/sh
# DRKMUQEET — developer tools (USB/ADB, animations, GPU debug, process limits, misc)

dev_usage() {
  cat <<'EOF'
DEVELOPER TOOLS:
  drk dev usb <0|1>            ADB/USB debugging (verified)
  drk dev adbnet <0|1> [port]  ADB over network (restarts adbd)
  drk dev oem status           OEM unlock / boot state report
  drk dev devopts <0|1>        developer options availability
  drk dev anim <scale>         all animation scales (0, 0.5, 1, ...)
  drk dev keep <0|1>           don't keep activities
  drk dev bglimit <n>          background process limit (device_config)
  drk dev rtl <0|1>            force RTL layout
  drk dev simdisp <on|off> [WxH/dpi]   simulate secondary display
  drk dev anr <0|1>            show background ANRs
  drk dev strict <0|1>         strict mode (prop; dev builds)
  drk dev overdraw <0-3>       debug GPU overdraw
  drk dev hwerr <0|1>          hide/show error dialogs
  drk dev stayon <0|1>         stay awake while charging
  drk dev cliprect <0|1>       show non-rectangular clip regions
  drk dev simcolor <0-3>       simulate color space (dev option; 1=monochrome)
EOF
}

dev_main() {
  local a="$1"; [ -n "$a" ] || { dev_usage; return 1; }
  shift
  case "$a" in
    usb) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk dev usb <0|1>"; return 1; }
      case "$1" in 0|1) ss global adb_enabled "$1" "adb debugging" ;;
      *) bad "usage: drk dev usb <0|1>"; return 1 ;; esac ;;
    adbnet) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk dev adbnet <0|1> [port]"; return 1; }
      case "$1" in
        1) pp service.adb.tcp.port "${2:-5555}" "adb tcp port"
           stop adbd 2>/dev/null; start adbd 2>/dev/null
           sleep 1
           [ "$(getprop init.svc.adbd 2>/dev/null)" = "running" ] && ok "adbd over network on port ${2:-5555}" \
             || bad "adbd did not restart" ;;
        0) pp service.adb.tcp.port 0 "adb tcp port"
           stop adbd 2>/dev/null; start adbd 2>/dev/null
           sleep 1
           ok "adb over network disabled" ;;
        *) bad "usage: drk dev adbnet <0|1> [port]"; return 1 ;;
      esac ;;
    oem) dev_oem_status ;;
    devopts) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk dev devopts <0|1>"; return 1; }
      case "$1" in 0|1) ss global development_settings_enabled "$1" "developer options" ;;
      *) bad "usage: drk dev devopts <0|1>"; return 1 ;; esac ;;
    anim) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk dev anim <scale>"; return 1; }
      ss global window_animation_scale "$1" "window animation"
      ss global transition_animation_scale "$1" "transition animation"
      ss global animator_duration_scale "$1" "animator duration" ;;
    keep) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk dev keep <0|1>"; return 1; }
      case "$1" in 0|1) ss global always_finish_activities "$1" "don't keep activities" ;;
      *) bad "usage: drk dev keep <0|1>"; return 1 ;; esac ;;
    bglimit) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk dev bglimit <n>"; return 1; }
      dev_bglimit "$1" ;;
    rtl) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk dev rtl <0|1>"; return 1; }
      case "$1" in 0|1) ss global debug.force_rtl "$1" "force RTL" ;;
      *) bad "usage: drk dev rtl <0|1>"; return 1 ;; esac ;;
    simdisp) need_root || return 1
      case "$1" in
        on) ss global overlay_display_devices "${2:-1280x720/160}" "secondary display" ;;
        off) settings delete global overlay_display_devices >/dev/null 2>&1 && ok "secondary display removed" \
               || bad "settings delete failed" ;;
        *) bad "usage: drk dev simdisp <on|off> [WxH/dpi]"; return 1 ;;
      esac ;;
    anr) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk dev anr <0|1>"; return 1; }
      case "$1" in 0|1) ss secure anr_show_background "$1" "show background ANRs" ;;
      *) bad "usage: drk dev anr <0|1>"; return 1 ;; esac ;;
    strict) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk dev strict <0|1>"; return 1; }
      case "$1" in 0|1) pp persist.sys.strictmode "$1" "strict mode" ;;
      *) bad "usage: drk dev strict <0|1>"; return 1 ;; esac ;;
    overdraw) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk dev overdraw <0-3>"; return 1; }
      case "$1" in 0|1|2|3)
        ss global debug_gpu_overdraw "$1" "debug GPU overdraw"
        case "$1" in
          0) pp debug.hwui.overdraw false "hwui overdraw" ;;
          *) pp debug.hwui.overdraw show "hwui overdraw" ;;
        esac ;;
      *) bad "usage: drk dev overdraw <0-3>"; return 1 ;; esac ;;
    cliprect) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk dev cliprect <0|1>"; return 1; }
      case "$1" in 0|1) pp debug.hwui.show_non_rect_clip "$1" "non-rect clip debug" ;;
      *) bad "usage: drk dev cliprect <0|1>"; return 1 ;; esac ;;
    simcolor) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk dev simcolor <0|1|2|3> (0 off, 1 mono, 2/3 OEM)"; return 1; }
      case "$1" in 0|1|2|3) ss secure simulate_color_space "$1" "simulate color space" ;;
      *) bad "usage: drk dev simcolor <0|1|2|3>"; return 1 ;; esac ;;
    hwerr) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk dev hwerr <0|1>"; return 1; }
      case "$1" in 0|1) ss global hide_error_dialogs "$1" "hide error dialogs" ;;
      *) bad "usage: drk dev hwerr <0|1>"; return 1 ;; esac ;;
    stayon) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk dev stayon <0|1>"; return 1; }
      case "$1" in 0|1) ss global stay_on_while_plugged_in "$([ "$1" = 1 ] && echo 7 || echo 0)" "stay awake while charging" ;;
      *) bad "usage: drk dev stayon <0|1>"; return 1 ;; esac ;;
    *) dev_usage; return 1 ;;
  esac
}

dev_bglimit() {
  local n="$1"
  if has device_config && device_config put activity_manager max_cached_processes "$n" >/dev/null 2>&1; then
    local got; got=$(device_config get activity_manager max_cached_processes 2>/dev/null)
    [ "$got" = "$n" ] && ok "max cached processes → $n" || warn "device_config readback '$got'"
  else
    ss global background_process_limit "$n" "background_process_limit"
  fi
}

dev_oem_status() {
  hdr "OEM / boot state"
  say "  oem_unlock_enabled : $(settings get global oem_unlock_enabled 2>/dev/null)"
  say "  verifiedbootstate  : $(getprop ro.boot.verifiedbootstate 2>/dev/null)"
  say "  veritymode         : $(getprop ro.boot.veritymode 2>/dev/null)"
  say "  encryption         : $(getprop ro.crypto.state 2>/dev/null)"
  warn "OEM unlock can only be toggled from fastboot (fastboot oem unlock) — not at runtime"
}

dev_probe() {
  has wm && ok "wm tool" || warn "wm"
  has settings && ok "settings tool" || warn "settings"
  has device_config && ok "device_config" || warn "device_config"
  return 0
}

dev_status() {
  hdr "Developer"
  say "  adb_enabled: $(settings get global adb_enabled 2>/dev/null)"
  say "  anim scales: $(settings get global window_animation_scale 2>/dev/null)"
  say "  bg limit: $(device_config get activity_manager max_cached_processes 2>/dev/null)"
}
