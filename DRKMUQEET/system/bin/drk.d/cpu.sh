#!/system/bin/sh
# DRKMUQEET — CPU control (governor / frequency / cores / hotplug / boost / scheduler)

cpu_usage() {
  cat <<'EOF'
CPU:
  drk cpu governor <gov>             set governor on all online CPUs (verified)
  drk cpu freq <min|max> <khz>       clamp CPU frequency (verified)
  drk cpu freq list                  list available governors/frequencies
  drk cpu cores <n>                  leave only n cores online (>=2, verified)
  drk cpu cores on|off <cpu>         bring a specific core online/offline
  drk cpu boost <0|1>                input/sched boost on touch
  drk cpu hotplug status             report online cores & hotplug state
  drk cpu sched <boost <0|1>>        EAS scheduler boost (schedtune)
EOF
}

cpu_main() {
  local a="$1"; [ -n "$a" ] || { cpu_usage; return 1; }
  shift
  case "$a" in
    governor) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk cpu governor <gov>"; return 1; }
      set_all_cpus scaling_governor "$1" "governor" ;;
    freq) need_root || return 1
      [ "$1" = "list" ] && { cpu_freq_list; return 0; }
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk cpu freq <min|max> <khz>"; return 1; }
      case "$1" in
        min) set_all_cpus scaling_min_freq "$2" "min freq" ;;
        max) set_all_cpus scaling_max_freq "$2" "max freq" ;;
        *) bad "usage: drk cpu freq <min|max> <khz>"; return 1 ;;
      esac ;;
    cores) need_root || return 1
      case "$1" in
        on|off)
          [ -n "$2" ] || { bad "usage: drk cpu cores <on|off> <cpu>"; return 1; }
          [ -w "/sys/devices/system/cpu/cpu$2/online" ] || { bad "cpu$2 has no online interface"; return 1; }
          v=0; [ "$1" = "on" ] && v=1
          sv "/sys/devices/system/cpu/cpu$2/online" "$v" "cpu$2 online" ;;
        *)
          [ -n "$1" ] || { bad "usage: drk cpu cores <n>"; return 1; }
          cpu_cores_limit "$1" ;;
      esac ;;
    boost) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk cpu boost <0|1>"; return 1; }
      case "$1" in 0|1)
        try_sv "$1" "input boost" /sys/module/cpu_boost/parameters/input_boost_enabled \
          /sys/module/cpu_boost/parameters/sched_boost_on_input
        try_sv "$1" "sched_boost" /sys/module/msm_performance/parameters/touchboost ;;
      *) bad "usage: drk cpu boost <0|1>"; return 1 ;; esac ;;
    hotplug) cpu_hotplug_status ;;
    sched) need_root || return 1
      [ "$1" = "boost" ] && [ -n "$2" ] || { bad "usage: drk cpu sched boost <0|1>"; return 1; }
      try_sv "$2" "schedtune boost" /dev/stune/top-app/schedtune.boost /dev/stune/schedtune.boost ;;
    *) cpu_usage; return 1 ;;
  esac
}

cpu_freq_list() {
  local c p
  for c in $(online_cpus); do
    p="/sys/devices/system/cpu/cpu$c/cpufreq"
    [ -r "$p/scaling_available_governors" ] || continue
    say "cpu$c governors: $(cat "$p/scaling_available_governors" 2>/dev/null)"
    say "cpu$c freqs:     $(cat "$p/scaling_available_frequencies" 2>/dev/null)"
    say "cpu$c current:   $(cat "$p/scaling_cur_freq" 2>/dev/null) kHz (min $(cat "$p/scaling_min_freq" 2>/dev/null), max $(cat "$p/scaling_max_freq" 2>/dev/null))"
  done
}

cpu_cores_limit() {
  local n="$1" count=0 c p
  [ "$n" -ge 2 ] || { bad "refusing to offline below 2 cores"; return 1; }
  for c in $(online_cpus); do
    count=$((count + 1))
    [ "$count" -le "$n" ] && continue
    p="/sys/devices/system/cpu/cpu$c/online"
    [ -w "$p" ] || continue
    sv "$p" 0 "offline cpu$c"
  done
  say "  now online: $(online_cpus)"
}

cpu_hotplug_status() {
  hdr "CPU hotplug status"
  say "  online cores : $(online_cpus)"
  say "  governor     : $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null)"
  for z in /sys/kernel/intelli_plug /sys/kernel/alucard_hotplug /sys/module/lazyplug; do
    [ -d "$z" ] && say "  hotplug driver present: $z"
  done
  [ -r /sys/module/cpu_boost/parameters/input_boost_enabled ] && \
    say "  input boost  : $(cat /sys/module/cpu_boost/parameters/input_boost_enabled 2>/dev/null)"
  [ -r /dev/stune/top-app/schedtune.boost ] && \
    say "  schedtune boost (top-app): $(cat /dev/stune/top-app/schedtune.boost 2>/dev/null)"
}

# --- doctor/status probes ---
cpu_probe() {
  local okc=0
  [ -r /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor ] && { ok "cpu governor interface"; okc=$((okc+1)); } \
    || bad "cpu governor interface"
  [ -w /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq ] && { ok "cpu freq interface"; okc=$((okc+1)); } \
    || bad "cpu freq interface"
  [ -e /sys/module/cpu_boost ] && { ok "cpu boost module present"; okc=$((okc+1)); } \
    || warn "cpu_boost module absent (boost feature unavailable)"
  [ -w /dev/stune/top-app/schedtune.boost ] && { ok "schedtune interface"; okc=$((okc+1)); } \
    || warn "schedtune absent (EAS sched boost unavailable)"
  return 0
}

cpu_status() {
  hdr "CPU"
  say "  governor: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null)"
  say "  freqs: min $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq 2>/dev/null) / cur $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null) / max $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq 2>/dev/null) kHz"
  say "  online: $(online_cpus)"
}
