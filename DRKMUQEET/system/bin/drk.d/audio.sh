#!/system/bin/sh
# DRKMUQEET — audio
# begonia has NO user-exposed DSP/EQ sysfs on the stock kernel; DSP effects
# (EQ/bass/virtualizer/... ) would require a vendor audio HAL modification or
# an external audio module (e.g. ViPER/JamesDSP). What IS controllable:
# volume streams, mono, DND, and real Qualcomm 'fluence' mic props.

audio_usage() {
  cat <<'EOF'
AUDIO:
  drk audio info                 dump audio HAL state (dumpsys audio)
  drk audio mono <0|1>           mono audio (accessibility)
  drk audio volume <stream> <0-15>   stream: music|ring|notification|alarm|system
  drk audio dnd <0|1>            do-not-disturb (zen mode)
  drk audio fluence <opt> <true|false>   fluence mic tuning (voicecall|voicerec|speaker|tmic)
  drk audio props <name> <value> set an arbitrary persist.vendor.audio.* prop
  drk audio eq                   explain DSP status (no fake EQ here)
EOF
}

audio_main() {
  local a="$1"; [ -n "$a" ] || { audio_usage; return 1; }
  shift
  case "$a" in
    info) audio_info ;;
    mono) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk audio mono <0|1>"; return 1; }
      case "$1" in 0|1) ss system master_mono "$1" "mono audio" ;;
      *) bad "usage: drk audio mono <0|1>"; return 1 ;; esac ;;
    volume) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk audio volume <stream> <0-15>"; return 1; }
      case "$1" in
        music|ring|notification|alarm|system)
          ss system "volume_$1" "$2" "volume_$1" ;;
        *) bad "stream must be music|ring|notification|alarm|system"; return 1 ;;
      esac ;;
    dnd) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk audio dnd <0|1>"; return 1; }
      case "$1" in
        0) ss global zen_mode 0 "zen mode" ;;
        1) ss global zen_mode 1 "zen mode" ;;
        *) bad "usage: drk audio dnd <0|1>"; return 1 ;;
      esac ;;
    fluence) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk audio fluence <opt> <true|false>"; return 1; }
      case "$1" in
        voicecall|voicerec|speaker|tmic)
          pp "persist.vendor.audio.fluence.$1" "$2" "fluence.$1" ;;
        *) bad "option must be voicecall|voicerec|speaker|tmic"; return 1 ;;
      esac ;;
    props) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk audio props <name> <value>"; return 1; }
      pp "$1" "$2" "audio prop $1" ;;
    eq) audio_eq_status ;;
    *) audio_usage; return 1 ;;
  esac
}

audio_info() {
  hdr "Audio state"
  dumpsys audio 2>/dev/null | grep -E "AudioHAL|AudioFlinger|stream|device|mode" | head -n 25 | sed 's/^/  /' \
    || bad "dumpsys audio unavailable"
}

audio_eq_status() {
  hdr "Audio DSP / EQ status"
  say "  begonia stock kernel exposes no DSP/EQ sysfs interface."
  say "  A 10-band EQ, bass boost, reverb etc. cannot be truthfully 'enabled' by a script"
  say "  — shipping one would be a fake feature. Options that DO work:"
  say "    • app-level EQ built into music players"
  say "    • an external DSP audio module (ViPER4Android / JamesDSP) — install separately"
  say "  What this module controls for real: volume, mono, DND, fluence mic props (above)."
}

audio_probe() {
  has dumpsys && ok "audio dump available" || warn "dumpsys audio unavailable"
  return 0
}

audio_status() {
  hdr "Audio"
  say "  mono: $(settings get system master_mono 2>/dev/null)"
  say "  zen mode: $(settings get global zen_mode 2>/dev/null)"
}
