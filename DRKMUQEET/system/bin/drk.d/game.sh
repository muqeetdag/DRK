#!/system/bin/sh
# DRKMUQEET — game mode
# A legitimate performance profile: boost scheduler + CPU/GPU, kill background
# apps, disable animations. It does NOT and will not include cheat tooling
# (memory editing, aimbots, wallhacks, speed hacks, etc.) — those are not
# part of this module by design.

game_usage() {
  cat <<'EOF'
GAME MODE (performance profile):
  drk game on        enable: schedtune boost, input boost, CPU/GPU max, animations off
  drk game off       disable: restore balanced defaults
  drk game status    show current gaming-related state
EOF
}

game_main() {
  local a="$1"; [ -n "$a" ] || { game_usage; return 1; }
  shift
  case "$a" in
    on) need_root || return 1; game_on ;;
    off) need_root || return 1; game_off ;;
    status) game_status ;;
    *) game_usage; return 1 ;;
  esac
}

game_on() {
  hdr "Game mode ON"
  [ -w /dev/stune/top-app/schedtune.boost ] && sv /dev/stune/top-app/schedtune.boost 20 "schedtune boost"
  try_sv 1 "input boost" /sys/module/cpu_boost/parameters/input_boost_enabled
  # raise CPU floor a little (only if kernel exposes min freq)
  local c p min
  for c in $(online_cpus); do
    p="/sys/devices/system/cpu/cpu$c/cpufreq"
    [ -r "$p/cpuinfo_min_freq" ] || continue
    min=$(cat "$p/cpuinfo_min_freq" 2>/dev/null)
    [ -w "$p/scaling_min_freq" ] && sv "$p/scaling_min_freq" "$min" "cpu$c min freq (floor)"
  done
  # GPU to max
  local gmax
  gmax=$(cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies 2>/dev/null | awk '{print $NF}')
  [ -n "$gmax" ] && try_sv "$gmax" "gpu max freq" /sys/class/kgsl/kgsl-3d0/devfreq/max_freq
  # animations off for snappy feel
  ss global window_animation_scale 0 "window animation"
  ss global transition_animation_scale 0 "transition animation"
  ss global animator_duration_scale 0 "animator duration"
  # drop background apps
  local n=0 pkg
  for pkg in $(user_packages); do
    case "$pkg" in com.android.*|com.google.android.gms|com.google.android.gsf|com.miui.*) continue ;; esac
    am force-stop "$pkg" >/dev/null 2>&1 && n=$((n+1))
  done
  say "  force-stopped $n user apps"
  logx "game mode ON"
}

game_off() {
  hdr "Game mode OFF"
  [ -w /dev/stune/top-app/schedtune.boost ] && sv /dev/stune/top-app/schedtune.boost 0 "schedtune boost"
  preset_balanced
  ss global window_animation_scale 1 "window animation"
  ss global transition_animation_scale 1 "transition animation"
  ss global animator_duration_scale 1 "animator duration"
  logx "game mode OFF"
}

game_status() {
  hdr "Game mode status"
  say "  schedtune boost (top-app): $(cat /dev/stune/top-app/schedtune.boost 2>/dev/null)"
  say "  input boost: $(cat /sys/module/cpu_boost/parameters/input_boost_enabled 2>/dev/null)"
  say "  gpu max: $(cat /sys/class/kgsl/kgsl-3d0/devfreq/max_freq 2>/dev/null)"
  say "  anim scales: $(settings get global window_animation_scale 2>/dev/null)"
}
