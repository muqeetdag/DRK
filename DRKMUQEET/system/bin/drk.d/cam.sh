#!/system/bin/sh
# DRKMUQEET — camera engine
# Manual photography controls (ISO, shutter, WB, focus...) are Camera2 API
# features used by the camera APP — no OS-level interface exists to set them
# globally, so they are not faked here. What IS controllable: HAL3 mode,
# EIS, camera app disable/enable, and real persist.vendor.camera.* props.

cam_usage() {
  cat <<'EOF'
CAMERA:
  drk cam info               list cameras & capabilities (dumpsys)
  drk cam hal3 <0|1>         camera HAL3 (Camera2 full) mode (verified)
  drk cam eis <0|1>          electronic image stabilization (HAL-dependent)
  drk cam disable|enable     disable/enable the stock camera app
  drk cam props <name> <val> set an arbitrary persist.vendor.camera.* prop
EOF
}

cam_main() {
  local a="$1"; [ -n "$a" ] || { cam_usage; return 1; }
  shift
  case "$a" in
    info) cam_info ;;
    hal3) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk cam hal3 <0|1>"; return 1; }
      case "$1" in
        0|1)
          pp persist.vendor.camera.HAL3.enabled "$1" "HAL3 (vendor)"
          pp persist.camera.HAL3.enabled "$1" "HAL3"
          warn "HAL3 takes effect for new camera sessions; restart camera app"
          ;;
        *) bad "usage: drk cam hal3 <0|1>"; return 1 ;;
      esac ;;
    eis) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk cam eis <0|1>"; return 1; }
      case "$1" in
        0|1)
          pp persist.vendor.camera.eis.enable "$1" "EIS (vendor)"
          pp persist.camera.eis.enable "$1" "EIS"
          warn "EIS applies only if the camera HAL honors it"
          ;;
        *) bad "usage: drk cam eis <0|1>"; return 1 ;;
      esac ;;
    disable|enable)
      need_root || return 1
      local pkg=com.android.camera
      require_pkg "$pkg" || { say "  stock camera app not found — trying com.miui.camera"; pkg=com.miui.camera; }
      require_pkg "$pkg" || { bad "no known camera package installed"; return 1; }
      pm "$a-user" --user 0 "$pkg" >/dev/null 2>&1
      if pm list packages -d 2>/dev/null | grep -q "$pkg"; then
        ok "camera app $a'd ($pkg)"
      else
        warn "state change not reflected — MIUI may block pm $a-user (check manually)"
      fi
      ;;
    props) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk cam props <name> <value>"; return 1; }
      pp "$1" "$2" "camera prop $1" ;;
    *) cam_usage; return 1 ;;
  esac
}

cam_info() {
  hdr "Camera"
  if dumpsys media.camera 2>/dev/null | grep -qE "Device|Cameras"; then
    dumpsys media.camera 2>/dev/null | grep -E "Device [0-9]|Camera ID|Facing|mCharacteristics" | head -n 25 | sed 's/^/  /'
  elif dumpsys camera 2>/dev/null | grep -q .; then
    dumpsys camera 2>/dev/null | head -n 25 | sed 's/^/  /'
  else
    say "  no camera dump available"
  fi
  say "  HAL3: $(getprop persist.vendor.camera.HAL3.enabled 2>/dev/null)"
}

cam_probe() {
  has dumpsys && ok "camera dump available" || warn "dumpsys"
  return 0
}

cam_status() {
  hdr "Camera"
  say "  HAL3 prop: $(getprop persist.vendor.camera.HAL3.enabled 2>/dev/null)"
  say "  EIS prop: $(getprop persist.vendor.camera.eis.enable 2>/dev/null)"
}
