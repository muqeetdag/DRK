#!/system/bin/sh
# DRKMUQEET — logging & debugging (logcat, kernel log, crash/ANR, traces, dumpstate, bugreport)

LOG_DIR=/data/adb/drk/logs

log_usage() {
  cat <<'EOF'
LOGGING & DEBUGGING:
  drk log cat [filter]       dump logcat (optionally filtered, e.g. 'ActivityManager')
  drk log cat -f <filter>    dump logcat filtered to a file in /data/adb/drk/logs/
  drk log kmsg [lines]       kernel log (dmesg)
  drk log sys [service]      dumpsys (optionally one service)
  drk log crash              crash/tombstone reports (DropBox + tombstones)
  drk log anr                ANR traces (/data/anr)
  drk log trace <category> <sec> <file>   atrace capture (e.g. gfx, view, sched)
  drk log dumpstate          full system dump to file
  drk log bugreport          Android bugreport to file (best-effort)
  drk log clear              clear logcat
  drk log size               log storage usage
EOF
}

log_main() {
  local a="$1"; [ -n "$a" ] || { log_usage; return 1; }
  shift
  case "$a" in
    cat) log_cat "$@" ;;
    kmsg) log_kmsg "$@" ;;
    sys) log_sys "$@" ;;
    crash) log_crash ;;
    anr) log_anr ;;
    trace) log_trace "$@" ;;
    dumpstate) log_dumpstate ;;
    bugreport) log_bugreport ;;
    clear) log_clear ;;
    size) log_size ;;
    *) log_usage; return 1 ;;
  esac
}

log_cat() {
  mkdir -p "$LOG_DIR"
  if [ "$1" = "-f" ]; then
    [ -n "$2" ] || { bad "usage: drk log cat -f <filter>"; return 1; }
    local out="$LOG_DIR/logcat-$(date +%Y%m%d-%H%M).txt"
    logcat -d -s "$2" > "$out" 2>/dev/null && ok "saved logcat($2) → $out ($(wc -l < "$out") lines)" \
      || bad "logcat failed"
  elif [ -n "$1" ]; then
    logcat -d -s "$1" 2>/dev/null | tail -n 120
  else
    logcat -d 2>/dev/null | tail -n 200
  fi
}

log_kmsg() {
  local lines="${1:-100}" out
  if [ "$1" = "-f" ]; then
    out="$LOG_DIR/kmsg-$(date +%Y%m%d-%H%M).txt"
    dmesg > "$out" 2>/dev/null && ok "saved kernel log → $out ($(wc -l < "$out") lines)" \
      || bad "dmesg failed"
    return 0
  fi
  dmesg 2>/dev/null | tail -n "$lines" | sed 's/^/  /' || bad "dmesg unavailable"
}

log_sys() {
  local out
  if [ "$1" = "-f" ]; then
    [ -n "$2" ] || { bad "usage: drk log sys -f <service>"; return 1; }
    out="$LOG_DIR/dumpsys-$2-$(date +%Y%m%d-%H%M).txt"
    dumpsys "$2" > "$out" 2>/dev/null && ok "saved dumpsys $2 → $out ($(wc -l < "$out") lines)" \
      || bad "dumpsys $2 failed"
  elif [ -n "$1" ]; then
    dumpsys "$1" 2>/dev/null | head -n 150 || bad "dumpsys $1 failed"
  else
    dumpsys -l 2>/dev/null | head -n 80
  fi
}

log_crash() {
  hdr "Crash reports"
  say "== DropBox (app crashes) =="
  dumpsys dropbox --print data_app_crash 2>/dev/null | tail -n 40 | sed 's/^/  /'
  say "== Native tombstones =="
  ls -lt /data/tombstones 2>/dev/null | head -n 6 | sed 's/^/  /' \
    || say "  no /data/tombstones"
}

log_anr() {
  hdr "ANR traces"
  ls -lt /data/anr 2>/dev/null | head -n 6 | sed 's/^/  /' || say "  no ANR traces"
  local f
  f=$(ls -t /data/anr/* 2>/dev/null | head -n1)
  [ -n "$f" ] && { say "  newest: $f"; tail -n 30 "$f" 2>/dev/null | sed 's/^/    /'; }
}

log_trace() {
  local cat="${1:-gfx}" sec="${2:-5}" file="${3:-$LOG_DIR/trace-$(date +%H%M%S).txt}"
  has atrace || { bad "atrace not available"; return 1; }
  [ -n "$3" ] || mkdir -p "$LOG_DIR"
  say "  capturing '$cat' for ${sec}s..."
  atrace -z -b 8192 -t "$sec" "$cat" > "$file" 2>/dev/null
  [ -s "$file" ] && ok "trace saved → $file ($(wc -c < "$file") bytes)" || { bad "trace capture failed"; return 1; }
}

log_dumpstate() {
  mkdir -p "$LOG_DIR"
  local out="$LOG_DIR/dumpstate-$(date +%Y%m%d-%H%M).txt"
  say "  collecting (takes a while)..."
  { echo "=== dumpstate $(date) ==="; dumpsys -l 2>/dev/null; } > "$out"
  for svc in activity window package battery cpuinfo meminfo netstats connectivity; do
    echo "--- $svc ---" >> "$out"
    dumpsys "$svc" 2>/dev/null | head -n 300 >> "$out"
  done
  ok "dumpstate → $out ($(wc -l < "$out") lines)"
}

log_bugreport() {
  mkdir -p "$LOG_DIR"
  local out="$LOG_DIR/bugreport-$(date +%Y%m%d-%H%M).txt"
  if has bugreport; then
    say "  generating bugreport (may take minutes)..."
    timeout 240 bugreport > "$out" 2>/dev/null
    [ -s "$out" ] && ok "bugreport → $out ($(wc -l < "$out") lines)" || { rm -f "$out"; bad "bugreport produced nothing"; return 1; }
  else
    warn "bugreport tool missing — falling back to dumpstate"
    log_dumpstate
  fi
}

log_clear() {
  logcat -c 2>/dev/null && ok "logcat cleared" || bad "logcat clear failed"
}

log_size() {
  hdr "Log storage"
  du -sh /data/adb/drk/logs /data/anr /data/tombstones 2>/dev/null | sed 's/^/  /'
}

log_probe() {
  has logcat && ok "logcat" || warn "logcat"
  has dmesg && ok "dmesg" || warn "dmesg"
  has dumpsys && ok "dumpsys" || warn "dumpsys"
  has atrace && ok "atrace" || warn "atrace"
  return 0
}

log_status() {
  hdr "Logging"
  say "  log dir: $(du -sh /data/adb/drk/logs 2>/dev/null | cut -f1)"
}
