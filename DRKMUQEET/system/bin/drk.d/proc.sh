#!/system/bin/sh
# DRKMUQEET — process management (priority, affinity, kill, background restriction, doze)

proc_usage() {
  cat <<'EOF'
PROCESS MANAGEMENT:
  drk proc list                    top CPU/RAM processes
  drk proc nice <pid|pkg> <delta>  adjust nice value (verified)
  drk proc boost <pkg> <0-100>     schedtune boost for an app's uid (EAS kernels)
  drk proc affinity <pid> <mask>   CPU affinity mask (taskset)
  drk proc kill <pid|pkg>          kill process / force-stop package
  drk proc killall                 force-stop all background user apps
  drk proc restrict <pkg> <on|off> ban/allow background execution (AppOps)
  drk proc standby <pkg> <bucket>  bucket: active|working_set|frequent|rare|restricted
  drk proc alarms <pkg> <deny|allow>  alarm/wakeup permission (AppOps)
  drk proc whitelist <pkg> <add|remove>  doze whitelist
EOF
}

proc_main() {
  local a="$1"; [ -n "$a" ] || { proc_usage; return 1; }
  shift
  case "$a" in
    list) proc_list ;;
    nice) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk proc nice <pid|pkg> <delta>"; return 1; }
      proc_nice "$1" "$2" ;;
    boost) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk proc boost <pkg> <0-100>"; return 1; }
      proc_boost "$1" "$2" ;;
    affinity) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk proc affinity <pid> <mask>"; return 1; }
      taskset -p "$2" "$1" >/dev/null 2>&1 && ok "affinity set for pid $1 → $2" \
        || { bad "taskset failed"; return 1; }
      taskset -p "$1" 2>/dev/null | sed 's/^/  /' ;;
    kill) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk proc kill <pid|pkg>"; return 1; }
      case "$1" in
        *[!0-9]*) am force-stop "$1" >/dev/null 2>&1 && ok "force-stopped $1" || { bad "force-stop failed"; return 1; } ;;
        *) kill -9 "$1" 2>/dev/null && ok "killed pid $1" || { bad "kill failed"; return 1; } ;;
      esac ;;
    killall) need_root || return 1
      local n=0 pkg
      for pkg in $(user_packages); do
        am force-stop "$pkg" >/dev/null 2>&1 && n=$((n+1))
      done
      ok "force-stopped $n user apps" ;;
    restrict) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk proc restrict <pkg> <on|off>"; return 1; }
      case "$2" in
        on) appops_set "$1" RUN_ANY_IN_BACKGROUND deny "background ban" ;;
        off) appops_set "$1" RUN_ANY_IN_BACKGROUND allow "background ban" ;;
        *) bad "usage: drk proc restrict <pkg> <on|off>"; return 1 ;;
      esac ;;
    standby) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk proc standby <pkg> <bucket>"; return 1; }
      case "$2" in
        active|working_set|frequent|rare|restricted) ;;
        *) bad "bucket must be active|working_set|frequent|rare|restricted"; return 1 ;;
      esac
      am set-standby-bucket "$1" "$2" >/dev/null 2>&1 \
        && { ok "standby bucket → $2 ($(am get-standby-bucket "$1" 2>/dev/null | sed 's/^/readback: /'))"; } \
        || { bad "set-standby-bucket failed"; return 1; } ;;
    alarms) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk proc alarms <pkg> <deny|allow>"; return 1; }
      case "$2" in
        deny) appops_set "$1" WAKE_ALARM ignore "wake alarms" ;;
        allow) appops_set "$1" WAKE_ALARM allow "wake alarms" ;;
        *) bad "usage: drk proc alarms <pkg> <deny|allow>"; return 1 ;;
      esac ;;
    whitelist) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk proc whitelist <pkg> <add|remove>"; return 1; }
      case "$2" in
        add) dumpsys deviceidle whitelist +"$1" >/dev/null 2>&1 && ok "$1 added to doze whitelist" \
               || { bad "whitelist add failed"; return 1; } ;;
        remove) dumpsys deviceidle whitelist -"$1" >/dev/null 2>&1 && ok "$1 removed from doze whitelist" \
               || { bad "whitelist remove failed"; return 1; } ;;
        *) bad "usage: drk proc whitelist <pkg> <add|remove>"; return 1 ;;
      esac ;;
    *) proc_usage; return 1 ;;
  esac
}

proc_list() {
  hdr "Top processes by CPU"
  ps -A -o PID,USER,NICE,PCY,STAT,%CPU,%MEM,ARGS 2>/dev/null | sort -k6 -nr | head -n 15 | sed 's/^/  /' \
    || ps -A 2>/dev/null | head -n 15 | sed 's/^/  /'
}

proc_nice() {
  local target="$1" delta="$2" pid np want got
  case "$target" in
    *[!0-9]*)
      pid=$(pidof "$target" 2>/dev/null | awk '{print $1}')
      [ -n "$pid" ] || { bad "no running process for $target"; return 1; }
      say "  $target → pid $pid" ;;
    *) pid=$target ;;
  esac
  np=$(awk '{print $19}' "/proc/$pid/stat" 2>/dev/null)
  [ -n "$np" ] || { bad "cannot read nice of pid $pid"; return 1; }
  want=$((np + delta))
  [ "$want" -ge -20 ] && [ "$want" -le 19 ] || { bad "resulting nice $want out of range [-20,19]"; return 1; }
  renice -n "$want" -p "$pid" >/dev/null 2>&1
  got=$(awk '{print $19}' "/proc/$pid/stat" 2>/dev/null)
  [ "$got" = "$want" ] && ok "pid $pid nice $np → $want" || { bad "readback nice=$got"; return 1; }
}

proc_boost() {
  local pkg="$1" level="$2" uid d
  uid=$(uid_of "$pkg")
  [ -n "$uid" ] || { bad "package $pkg not installed"; return 1; }
  # try per-uid schedtune (EAS) — created by the kernel once the app runs
  d=$(ls -d /dev/stune/uid_$uid 2>/dev/null | head -n1)
  if [ -n "$d" ] && [ -w "$d/schedtune.boost" ]; then
    sv "$d/schedtune.boost" "$level" "schedtune boost ($pkg)"
  else
    bad "per-uid schedtune node not present (app must run once; kernel must be EAS)"
    return 1
  fi
}

proc_probe() {
  [ -d /dev/stune ] && ok "schedtune (EAS)" || warn "schedtune absent"
  has ps && ok "ps" || bad "ps"
  return 0
}

proc_status() {
  hdr "Processes"
  say "  running: $(ps -A 2>/dev/null | wc -l) processes"
}
