#!/system/bin/sh
# DRKMUQEET — storage & filesystem (read-ahead, I/O scheduler, trim, f2fs/ext4, encryption status)

fs_usage() {
  cat <<'EOF'
STORAGE / FILESYSTEM:
  drk fs readahead <kb>          set read-ahead on all block devices (verified)
  drk fs sched <algo>            I/O scheduler: cfq|noop|deadline|bfq|kyber (verified)
  drk fs trim                    fstrim /data /cache /system now
  drk fs mount <noatime|atime>   remount /data with/without noatime
  drk fs journal status          ext4 journal status (read-only; safe)
  drk fs f2fs <key> <value>      tune f2fs sysfs (see: drk fs f2fs keys)
  drk fs ext4 <key> <value>      tune ext4 sysfs (see: drk fs ext4 keys)
  drk fs encrypt status          report /data encryption state
  drk fs info                    block devices, schedulers, mount options
EOF
}

fs_main() {
  local a="$1"; [ -n "$a" ] || { fs_usage; return 1; }
  shift
  case "$a" in
    readahead) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk fs readahead <kb>"; return 1; }
      fs_readahead "$1" ;;
    sched) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk fs sched <algo>"; return 1; }
      fs_sched "$1" ;;
    trim) need_root || return 1; fs_trim ;;
    mount) need_root || return 1
      case "$1" in
        noatime) mount -o remount,noatime /data 2>/dev/null && ok "/data remounted noatime" || bad "remount noatime failed" ;;
        atime)   mount -o remount,atime /data 2>/dev/null && ok "/data remounted atime" || bad "remount atime failed" ;;
        *) bad "usage: drk fs mount <noatime|atime>"; return 1 ;;
      esac ;;
    journal) need_root || return 1
      fs_journal_status ;;
    f2fs) need_root || return 1
      [ "$1" = "keys" ] && { fs_f2fs_keys; return 0; }
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk fs f2fs <key> <value>"; return 1; }
      fs_fs_sysfs f2fs "$1" "$2" ;;
    ext4) need_root || return 1
      [ "$1" = "keys" ] && { fs_ext4_keys; return 0; }
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk fs ext4 <key> <value>"; return 1; }
      fs_fs_sysfs ext4 "$1" "$2" ;;
    encrypt) fs_encrypt_status ;;
    info) fs_info ;;
    *) fs_usage; return 1 ;;
  esac
}

fs_trim() {
  local mp n=0
  has fstrim || { bad "fstrim not available"; return 1; }
  for mp in /data /cache /system; do
    if fstrim -v "$mp" 2>/dev/null; then ok "trimmed $mp"; n=$((n+1)); else warn "$mp — fstrim failed/skipped"; fi
  done
  [ "$n" -gt 0 ]
}

fs_readahead() {
  local kb="$1" f n=0
  for f in /sys/block/*/queue/read_ahead_kb; do
    [ -e "$f" ] || continue
    sv "$f" "$kb" "read-ahead $f" && n=$((n+1))
  done
  [ "$n" -gt 0 ] || { bad "no block devices exposed"; return 1; }
}

fs_sched() {
  local algo="$1" f n=0 avail
  for f in /sys/block/*/queue/scheduler; do
    [ -e "$f" ] || continue
    avail=$(cat "$f" 2>/dev/null)
    case " $avail " in
      *" $algo "*) sv "$f" "$algo" "scheduler $(basename "$(dirname "$(dirname "$f")")")" && n=$((n+1)) ;;
      *) bad "scheduler $algo not supported on $(dirname "$f") (available: $avail)" ;;
    esac
  done
  [ "$n" -gt 0 ]
}

fs_f2fs_keys() {
  local d
  for d in /sys/fs/f2fs/*; do
    [ -d "$d" ] || continue
    say "  $d:"
    for k in "$d"/*; do
      [ -r "$k" ] && say "    $(basename "$k") = $(cat "$k" 2>/dev/null)"
    done
  done
}

fs_ext4_keys() {
  local d
  for d in /sys/fs/ext4/*; do
    [ -d "$d" ] || continue
    say "  $d:"
    for k in "$d"/*; do
      [ -r "$k" ] && say "    $(basename "$k") = $(cat "$k" 2>/dev/null)"
    done
  done
}

fs_fs_sysfs() {
  local fs="$1" key="$2" val="$3" d f n=0
  for d in /sys/fs/$fs/*; do
    [ -d "$d" ] || continue
    f="$d/$key"
    [ -e "$f" ] || continue
    sv "$f" "$val" "$fs $key ($d)" && n=$((n+1))
  done
  [ "$n" -gt 0 ] || { bad "$fs key '$key' not found (see: drk fs $fs keys)"; return 1; }
}

fs_journal_status() {
  local dev
  for dev in /dev/block/by-name/userdata /dev/block/mmcblk0p60 /dev/block/mmcblk0p*; do
    [ -b "$dev" ] || continue
    if has tune2fs; then
      tune2fs -l "$dev" 2>/dev/null | grep -qi "has_journal" && say "  $dev: ext4 journal present" && return 0
    fi
  done
  # f2fs has no classic journal toggle; report fs type
  local fst
  fst=$(mount | awk '$3=="/data"{print $5; exit}')
  say "  /data filesystem: $fst (journal control applies to ext4 only; f2fs uses its own checkpointing)"
}

fs_encrypt_status() {
  hdr "Encryption status"
  say "  ro.crypto.state : $(getprop ro.crypto.state 2>/dev/null)"
  say "  ro.crypto.type  : $(getprop ro.crypto.type 2>/dev/null)"
  say "  ro.crypto.fs_crypto: $(getprop ro.crypto.fs_crypto 2>/dev/null)"
  say "  userdata type   : $(mount | awk '$3=="/data"{print $5; exit}')"
}

fs_info() {
  hdr "Storage"
  for f in /sys/block/*/queue/scheduler; do
    [ -e "$f" ] || continue
    say "  $(dirname "$f"): sched=$(cat "$f" 2>/dev/null) readahead=$(cat "$(dirname "$f")/read_ahead_kb" 2>/dev/null)kB"
  done
  mount | grep -E " /data | /cache | /system " | sed 's/^/  /'
}

fs_probe() {
  local n=0
  for f in /sys/block/*/queue/read_ahead_kb; do [ -e "$f" ] && n=$((n+1)); done
  [ "$n" -gt 0 ] && ok "block queue interfaces ($n devices)" || bad "block queue interfaces"
  has fstrim && ok "fstrim available" || warn "fstrim unavailable"
  return 0
}

fs_status() {
  hdr "Storage"
  for f in /sys/block/*/queue/scheduler; do
    [ -e "$f" ] || continue
    say "  $(dirname "$f"): $(cat "$f" 2>/dev/null) | readahead $(cat "$(dirname "$f")/read_ahead_kb" 2>/dev/null)kB"
  done
}
