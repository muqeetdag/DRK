#!/system/bin/sh
# DRKMUQEET — accessibility (font, display size, contrast, inversion, color correction,
# magnification, pointer, call/rotate/password toggles, services, captions, talkback)

acc_usage() {
  cat <<'EOF'
ACCESSIBILITY:
  drk acc font <scale>         font scale (e.g. 1.0, 1.15, 1.3)
  drk acc size <scale>         display size (e.g. 0.9, 1.0, 1.1) — wm density based
  drk acc bold <0|1>           bold text (OEM key, try-verify)
  drk acc contrast <0|1>       high contrast text
  drk acc invert <0|1>         color inversion
  drk acc dalton <0|1> [mode]  color correction (0 mono, 1/2/3 deficiency types)
  drk acc gray <0|1>           grayscale (OEM key, try-verify)
  drk acc magnify <0|1>        magnification gesture
  drk acc pointer <0|1>        large mouse pointer
  drk acc powercall <0|1>      power button ends call
  drk acc speakpw <0|1>        speak passwords
  drk acc captions <0|1> [size]  system captions
  drk acc talkback <0|1>       enable/disable TalkBack service
  drk acc service <comp> <0|1> generic accessibility service toggle (switch access,
                               select-to-speak, voice access, ...)
  drk acc rotate <0|1>         auto-rotate screen
EOF
}

acc_main() {
  local a="$1"; [ -n "$a" ] || { acc_usage; return 1; }
  shift
  case "$a" in
    font) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk acc font <scale>"; return 1; }
      ss system font_scale "$1" "font scale" ;;
    size) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk acc size <scale>"; return 1; }
      ss secure display_size_forced "$1" "display size" ;;
    bold) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk acc bold <0|1>"; return 1; }
      case "$1" in 0|1)
        ssx system font_bold "$1" "$1" "bold text" \
          || ssx secure accessibility_bold_text_enabled "$1" "$1" "bold text (secure)" ;;
      *) bad "usage: drk acc bold <0|1>"; return 1 ;; esac ;;
    contrast) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk acc contrast <0|1>"; return 1; }
      case "$1" in 0|1) ss secure high_text_contrast_enabled "$1" "high contrast text" ;;
      *) bad "usage: drk acc contrast <0|1>"; return 1 ;; esac ;;
    invert) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk acc invert <0|1>"; return 1; }
      case "$1" in 0|1) ss secure accessibility_display_inversion_enabled "$1" "color inversion" ;;
      *) bad "usage: drk acc invert <0|1>"; return 1 ;; esac ;;
    dalton) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk acc dalton <0|1> [mode]"; return 1; }
      case "$1" in
        0) ss secure accessibility_display_daltonizer_enabled 0 "color correction" ;;
        1) ss secure accessibility_display_daltonizer_enabled 1 "color correction"
           ss secure accessibility_display_daltonizer "${2:-1}" "correction mode" ;;
        *) bad "usage: drk acc dalton <0|1> [mode]"; return 1 ;;
      esac ;;
    gray) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk acc gray <0|1>"; return 1; }
      case "$1" in 0|1)
        ssx system grayscale_mode "$1" "$1" "grayscale" \
          || ssx secure accessibility_display_inversion_enabled "$1" "$1" "grayscale fallback (invert)" ;;
      *) bad "usage: drk acc gray <0|1>"; return 1 ;; esac ;;
    magnify) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk acc magnify <0|1>"; return 1; }
      case "$1" in 0|1) ss secure accessibility_display_magnification_enabled "$1" "magnification" ;;
      *) bad "usage: drk acc magnify <0|1>"; return 1 ;; esac ;;
    pointer) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk acc pointer <0|1>"; return 1; }
      case "$1" in 0|1) ss secure pointer_icon_enabled "$1" "large pointer" ;;
      *) bad "usage: drk acc pointer <0|1>"; return 1 ;; esac ;;
    powercall) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk acc powercall <0|1>"; return 1; }
      case "$1" in 0|1) ss secure power_button_ends_call "$1" "power button ends call" ;;
      *) bad "usage: drk acc powercall <0|1>"; return 1 ;; esac ;;
    speakpw) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk acc speakpw <0|1>"; return 1; }
      case "$1" in 0|1) ss secure speak_password "$1" "speak passwords" ;;
      *) bad "usage: drk acc speakpw <0|1>"; return 1 ;; esac ;;
    captions) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk acc captions <0|1> [size]"; return 1; }
      case "$1" in
        0|1)
          ss secure accessibility_captioning_enabled "$1" "captions"
          [ -n "$2" ] && ss secure accessibility_captioning_font_scale "$2" "caption size" ;;
        *) bad "usage: drk acc captions <0|1> [size]"; return 1 ;;
      esac ;;
    talkback) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk acc talkback <0|1>"; return 1; }
      acc_service "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService" "$1" "TalkBack" ;;
    service) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk acc service <component> <0|1>"; return 1; }
      acc_service "$1" "$2" "$1" ;;
    rotate) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk acc rotate <0|1>"; return 1; }
      case "$1" in 0|1) ss system accelerometer_rotation "$1" "auto-rotate" ;;
      *) bad "usage: drk acc rotate <0|1>"; return 1 ;; esac ;;
    *) acc_usage; return 1 ;;
  esac
}

# acc_service <component> <0|1> <label> — enable/disable an accessibility service
acc_service() {
  local comp="$1" mode="$2" label="$3" cur new
  cur=$(settings get secure enabled_accessibility_services 2>/dev/null)
  case "$mode" in
    1)
      case " $cur " in *"$comp"*) say "  $label already enabled" ;; *)
        new="$cur:$comp"; new=${new#:}
        ss secure enabled_accessibility_services "$new" "$label"
        ss secure accessibility_enabled 1 "accessibility" ;;
      esac ;;
    0)
      new=$(echo "$cur" | tr ':' '\n' | grep -v "^$comp$" | tr '\n' ':')
      new=${new%:}
      ss secure enabled_accessibility_services "$new" "$label" ;;
  esac
}

acc_probe() {
  has settings && ok "settings tool" || bad "settings"
  return 0
}

acc_status() {
  hdr "Accessibility"
  say "  font_scale: $(settings get system font_scale 2>/dev/null)"
  say "  inversion: $(settings get secure accessibility_display_inversion_enabled 2>/dev/null)"
  say "  talkback: $(settings get secure enabled_accessibility_services 2>/dev/null | grep -c talkback)"
}
