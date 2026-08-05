#!/system/bin/sh
# DRKMUQEET — GPU control (Adreno 618 on begonia, kgsl-3d0)

KGSL=/sys/class/kgsl/kgsl-3d0

gpu_usage() {
  cat <<'EOF'
GPU:
  drk gpu governor <gov>           set devfreq governor (e.g. msm_adreno_tz)
  drk gpu freq <min|max> <hz>      clamp GPU frequency (verified)
  drk gpu freq list                list available frequencies/governors
  drk gpu renderer <gl|skia|skiavk>  HWUI renderer selection
  drk gpu vulkan <0|1>             prefer Vulkan (skiavk) / OpenGL renderer
  drk gpu profile <0|1|2>          GPU profiling (0 off, 1 bars, 2 logcat)
  drk gpu msaa <0|1>               force 4x MSAA (device-dependent)
  drk gpu overlay <0|1>            disable hardware overlays
  drk gpu forcegpu <0|1>           force GPU rendering (2D)
  drk gpu info                     dump GPU state
EOF
}

gpu_main() {
  local a="$1"; [ -n "$a" ] || { gpu_usage; return 1; }
  shift
  case "$a" in
    governor) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk gpu governor <gov>"; return 1; }
      try_sv "$1" "gpu governor" "$KGSL/devfreq/governor" ;;
    freq) need_root || return 1
      [ "$1" = "list" ] && { gpu_freq_list; return 0; }
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk gpu freq <min|max> <hz>"; return 1; }
      case "$1" in
        min) try_sv "$2" "gpu min freq" "$KGSL/devfreq/min_freq" "$KGSL/min_gpuclk" ;;
        max) try_sv "$2" "gpu max freq" "$KGSL/devfreq/max_freq" "$KGSL/max_gpuclk" ;;
        *) bad "usage: drk gpu freq <min|max> <hz>"; return 1 ;;
      esac ;;
    renderer) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk gpu renderer <gl|skia|skiavk>"; return 1; }
      case "$1" in
        gl|opengl) pp debug.hwui.renderer opengl "gpu renderer" ;;
        skia)      pp debug.hwui.renderer skia "gpu renderer" ;;
        skiavk)    pp debug.hwui.renderer skiavk "gpu renderer" ;;
        *) bad "renderer must be gl|skia|skiavk"; return 1 ;;
      esac ;;
    vulkan) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk gpu vulkan <0|1>"; return 1; }
      case "$1" in
        1) pp debug.hwui.renderer skiavk "renderer (vulkan)"; pp persist.graphics.vulkan.disable false "vulkan disable flag" ;;
        0) pp debug.hwui.renderer opengl "renderer (opengl)"; pp persist.graphics.vulkan.disable true "vulkan disable flag" ;;
        *) bad "usage: drk gpu vulkan <0|1>"; return 1 ;;
      esac ;;
    profile) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk gpu profile <0|1|2>"; return 1; }
      case "$1" in
        0|1|2) ss global profile_gpu_rendering "$1" "gpu profiling"
               [ "$1" = "0" ] && pp debug.hwui.profile false "hwui profile" \
                             || pp debug.hwui.profile true "hwui profile" ;;
        *) bad "profile must be 0|1|2"; return 1 ;;
      esac ;;
    msaa) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk gpu msaa <0|1>"; return 1; }
      case "$1" in 0|1)
        ssx global multisample "$1" "$1" "force 4x MSAA" ;;
      *) bad "usage: drk gpu msaa <0|1>"; return 1 ;; esac ;;
    overlay) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk gpu overlay <0|1>"; return 1; }
      case "$1" in 0|1) ss global disable_hw_overlays "$1" "disable HW overlays" ;;
      *) bad "usage: drk gpu overlay <0|1>"; return 1 ;; esac ;;
    forcegpu) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk gpu forcegpu <0|1>"; return 1; }
      case "$1" in 0|1) ss global force_gpu_rendering "$1" "force GPU rendering" ;;
      *) bad "usage: drk gpu forcegpu <0|1>"; return 1 ;; esac ;;
    info) gpu_info ;;
    *) gpu_usage; return 1 ;;
  esac
}

gpu_freq_list() {
  [ -d "$KGSL" ] || { bad "no kgsl GPU interface"; return 1; }
  say "  available freqs: $(cat "$KGSL/gpu_available_frequencies" 2>/dev/null)"
  say "  available govs : $(cat "$KGSL/devfreq/available_governors" 2>/dev/null)"
  say "  current        : $(cat "$KGSL/gpuclk" 2>/dev/null) Hz (min $(cat "$KGSL/devfreq/min_freq" 2>/dev/null), max $(cat "$KGSL/devfreq/max_freq" 2>/dev/null))"
}

gpu_info() {
  hdr "GPU (kgsl)"
  for f in gpu_model gpuclk max_gpuclk min_gpuclk gpu_busy_percentage gpu_available_frequencies gpu_tmu; do
    [ -r "$KGSL/$f" ] && say "  $f: $(cat "$KGSL/$f" 2>/dev/null)"
  done
  [ -r "$KGSL/devfreq/governor" ] && say "  governor: $(cat "$KGSL/devfreq/governor" 2>/dev/null)"
  [ -r "$KGSL/devfreq/cur_freq" ] && say "  devfreq cur_freq: $(cat "$KGSL/devfreq/cur_freq" 2>/dev/null)"
}

gpu_probe() {
  [ -d "$KGSL" ] && ok "kgsl GPU interface" || bad "kgsl GPU interface (unusual for begonia)"
  [ -w "$KGSL/devfreq/governor" ] && ok "gpu governor writable" || warn "gpu governor not writable"
  return 0
}

gpu_status() {
  hdr "GPU"
  say "  clock: $(cat "$KGSL/gpuclk" 2>/dev/null) Hz"
  say "  governor: $(cat "$KGSL/devfreq/governor" 2>/dev/null)"
  say "  renderer prop: $(getprop debug.hwui.renderer 2>/dev/null)"
}
