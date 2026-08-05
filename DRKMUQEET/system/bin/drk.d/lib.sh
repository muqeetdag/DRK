#!/system/bin/sh
# ============================================================
#  DRKMUQEET — shared library (sourced by drk and drkd)
#  Every apply-function writes a value and READS IT BACK to
#  verify. Nothing is ever claimed to work without proof.
# ============================================================

DRK_HOME=/data/adb/drk
DRK_CONF=$DRK_HOME/drk.conf
DRK_LOGS=$DRK_HOME/logs
DRK_STATE=$DRK_HOME/state
DRK_VER="2.0"
DRK_VERCODE=200

export PATH=/system/bin:/system/xbin:/sbin:/vendor/bin:/su/bin:/data/adb/magisk:$PATH

DRK_QUIET=${DRK_QUIET:-0}

# ---------------- output helpers ----------------
say()  { printf '%s\n' "$*"; }
ok()   { [ "$DRK_QUIET" = 1 ] || printf '  ✓ %s\n' "$*"; }
bad()  { printf '  ✗ %s\n' "$*"; }
warn() { printf '  ⚠ %s\n' "$*"; }
hdr()  { [ "$DRK_QUIET" = 1 ] || printf '\n== %s ==\n' "$*"; }

logx() { # logx <message> — timestamped event log
  mkdir -p "$DRK_LOGS" 2>/dev/null
  printf '%s %s\n' "$(date '+%F %T')" "$*" >> "$DRK_LOGS/events.log" 2>/dev/null
}

has() { command -v "$1" >/dev/null 2>&1; }

is_android() { [ -x /system/bin/getprop ]; }

need_root() {
  [ "$(id -u 2>/dev/null)" = "0" ] && return 0
  warn "root required — run through a root shell (su -c 'drk ...')"
  return 1
}

# normalize boolean representations for comparison (1/Y/on/true ↔ 0/N/off/false)
norm_bool() {
  case "$1" in
    1|Y|y|yes|true|on) echo 1 ;;
    0|N|n|no|false|off) echo 0 ;;
    *) echo "$1" ;;
  esac
}

# ---------------- verifiers ----------------
# sv <path> <value> [label] : write to sysfs/proc file, read back, verify.
# Tolerates bracketed readbacks ("[cfq] noop") and Y/N boolean params.
sv() {
  local p="$1" v="$2" lab="${3:-$1}" rv nr
  [ -e "$p" ] || { bad "$lab — $p not present"; return 1; }
  [ -w "$p" ] || { bad "$lab — $p not writable"; return 1; }
  if ! echo "$v" > "$p" 2>/dev/null; then bad "$lab — write failed"; return 1; fi
  rv=$(cat "$p" 2>/dev/null)
  nr=$(echo "$rv" | tr -d '[]')
  if [ "$(norm_bool "$nr")" = "$(norm_bool "$v")" ]; then ok "$lab = $rv"; return 0; fi
  case " $nr " in *" $v "*) ok "$lab = $rv"; return 0;; esac
  bad "$lab — readback '$rv' != '$v'"; return 1
}

# try_sv <value> <label> <paths...> : write to the first existing path
try_sv() {
  local v="$1" lab="$2" p
  shift 2
  for p in "$@"; do
    [ -e "$p" ] || continue
    sv "$p" "$v" "$lab" && return 0
    return 1
  done
  bad "$lab — no kernel interface found (tried: $*)"
  return 1
}

# sc <sysctl-key> <value> [label] : sysctl + verify
sc() {
  local k="$1" v="$2" lab="${3:-$k}" rv
  if ! sysctl -w "$k=$v" >/dev/null 2>&1; then bad "$lab — sysctl $k failed"; return 1; fi
  rv=$(sysctl -n "$k" 2>/dev/null)
  [ "$rv" = "$v" ] && { ok "$lab = $v"; return 0; }
  bad "$lab — readback '$rv' != '$v'"; return 1
}

# scn <sysctl-key> <value> <expected-readback> [label] : tolerate kernel clamping
scn() {
  local k="$1" v="$2" want="$3" lab="${4:-$k}" rv
  if ! sysctl -w "$k=$v" >/dev/null 2>&1; then bad "$lab — sysctl $k failed"; return 1; fi
  rv=$(sysctl -n "$k" 2>/dev/null)
  [ "$rv" = "$want" ] && { ok "$lab = $rv"; return 0; }
  bad "$lab — readback '$rv' != '$want' (kernel may have clamped)"; return 1
}

# ss <namespace> <key> <value> [label] : Android settings + verify
ss() {
  local ns="$1" k="$2" v="$3" lab="${4:-$k}" rv
  if ! settings put "$ns" "$k" "$v" >/dev/null 2>&1; then bad "$lab — settings put failed"; return 1; fi
  rv=$(settings get "$ns" "$k" 2>/dev/null)
  [ "$rv" = "$v" ] && { ok "$lab = $v"; return 0; }
  bad "$lab — readback '$rv' != '$v'"; return 1
}

# ssx <namespace> <key> <value> <expected-readback> [label] : settings, tolerant variant
ssx() {
  local ns="$1" k="$2" v="$3" want="$4" lab="${5:-$k}" rv
  if ! settings put "$ns" "$k" "$v" >/dev/null 2>&1; then bad "$lab — settings put failed"; return 1; fi
  rv=$(settings get "$ns" "$k" 2>/dev/null)
  [ "$rv" = "$want" ] && { ok "$lab = $rv"; return 0; }
  bad "$lab — readback '$rv' != '$want'"; return 1
}

# pp <prop> <value> [label] : setprop + verify
pp() {
  local n="$1" v="$2" lab="${3:-$n}" rv
  if ! setprop "$n" "$v" >/dev/null 2>&1; then bad "$lab — setprop failed"; return 1; fi
  rv=$(getprop "$n" 2>/dev/null)
  [ "$rv" = "$v" ] && { ok "$lab = $v"; return 0; }
  bad "$lab — readback '$rv' != '$v'"; return 1
}

# appops_set <pkg> <op> <mode> [label] : cmd appops + verify
appops_set() {
  local pkg="$1" op="$2" mode="$3" rv
  local lab="${4:-$op/$pkg}"
  if ! cmd appops set "$pkg" "$op" "$mode" >/dev/null 2>&1; then
    bad "$lab — appops set failed"; return 1
  fi
  rv=$(cmd appops get "$pkg" "$op" 2>/dev/null | head -n1)
  case "$rv" in
    *"$mode"*) ok "$lab → $mode"; return 0 ;;
    *) bad "$lab — readback '$rv'"; return 1 ;;
  esac
}

# ---------------- helpers ----------------
uid_of() { # uid_of <pkg> → uid (empty if not installed)
  pm list packages -U 2>/dev/null | sed -n "s/^package:$1 uid://p" | head -n1
}

user_packages() { pm list packages -u 2>/dev/null | sed 's/^package://'; }

online_cpus() {
  ls -d /sys/devices/system/cpu/cpu[0-9]* 2>/dev/null \
    | sed 's/.*cpu//' \
    | while read -r c; do
        [ -f "/sys/devices/system/cpu/cpu$c/online" ] || { printf '%s ' "$c"; continue; }
        [ "$(cat "/sys/devices/system/cpu/cpu$c/online" 2>/dev/null)" = "1" ] && printf '%s ' "$c"
      done
}

first_of() { # first_of <space separated list> — first non-empty token
  local x
  for x in $1; do [ -n "$x" ] && { printf '%s' "$x"; return 0; }; done
  return 1
}

require_pkg() { # require_pkg <pkg>
  pm path "$1" >/dev/null 2>&1 || { bad "package $1 not installed"; return 1; }
  return 0
}

# apply a numeric value to all online CPUs: set_all_cpus <file-suffix> <value> <label>
set_all_cpus() {
  local suf="$1" v="$2" lab="$3" c p n=0
  for c in $(online_cpus); do
    p="/sys/devices/system/cpu/cpu$c/cpufreq/$suf"
    [ -w "$p" ] || continue
    sv "$p" "$v" "$lab (cpu$c)" && n=$((n+1))
  done
  [ "$n" -gt 0 ] && return 0
  bad "$lab — no writable cpufreq interface"; return 1
}

# ---------------- presets ----------------
preset_perf() { # performance preset (safe on begonia)
  hdr "Applying performance preset"
  local gov
  gov=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors 2>/dev/null)
  case " $gov " in
    *" schedutil "*) set_all_cpus scaling_governor schedutil "governor" ;;
    *" interactive "*) set_all_cpus scaling_governor interactive "governor" ;;
  esac
  sc vm.swappiness 80 "swappiness"
  sc vm.vfs_cache_pressure 100 "vfs_cache_pressure"
  try_sv 1 "input boost" /sys/module/cpu_boost/parameters/input_boost_enabled \
    /sys/module/cpu_boost/parameters/sched_boost_on_input
  [ -w /dev/stune/top-app/schedtune.boost ] && sv /dev/stune/top-app/schedtune.boost 10 "top-app schedtune boost"
}

preset_battery() {
  hdr "Applying battery preset"
  local gov
  gov=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors 2>/dev/null)
  case " $gov " in
    *" powersave "*) set_all_cpus scaling_governor powersave "governor" ;;
    *" schedutil "*) set_all_cpus scaling_governor schedutil "governor" ;;
  esac
  sc vm.swappiness 60 "swappiness"
  sc vm.vfs_cache_pressure 80 "vfs_cache_pressure"
  try_sv 0 "input boost" /sys/module/cpu_boost/parameters/input_boost_enabled
}

preset_balanced() {
  hdr "Applying balanced preset"
  local gov
  gov=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors 2>/dev/null)
  case " $gov " in
    *" schedutil "*) set_all_cpus scaling_governor schedutil "governor" ;;
    *" interactive "*) set_all_cpus scaling_governor interactive "governor" ;;
  esac
  sc vm.swappiness 80 "swappiness"
  sc vm.vfs_cache_pressure 100 "vfs_cache_pressure"
}

preset_default() { # restore kernel defaults where we can
  hdr "Restoring defaults"
  local gov c p
  for c in $(online_cpus); do
    p="/sys/devices/system/cpu/cpu$c/cpufreq"
    [ -r "$p/scaling_available_governors" ] || continue
    gov=$(cat "$p/scaling_available_governors" 2>/dev/null)
    set -- $gov
    [ -n "$1" ] && [ -w "$p/scaling_governor" ] && sv "$p/scaling_governor" "$1" "default governor (cpu$c)"
    [ -r "$p/cpuinfo_max_freq" ] && [ -w "$p/scaling_max_freq" ] && \
      sv "$p/scaling_max_freq" "$(cat "$p/cpuinfo_max_freq")" "default max freq (cpu$c)"
    [ -r "$p/cpuinfo_min_freq" ] && [ -w "$p/scaling_min_freq" ] && \
      sv "$p/scaling_min_freq" "$(cat "$p/cpuinfo_min_freq")" "default min freq (cpu$c)"
  done
  sc vm.swappiness 100 "swappiness"
  sc vm.vfs_cache_pressure 100 "vfs_cache_pressure"
  try_sv 1 "input boost" /sys/module/cpu_boost/parameters/input_boost_enabled
  # zram off if we turned it on
  if [ -f /sys/block/zram0/disksize ]; then
    swapoff /dev/block/zram0 2>/dev/null
    echo 1 > /sys/block/zram0/reset 2>/dev/null && ok "zram reset"
  fi
}
