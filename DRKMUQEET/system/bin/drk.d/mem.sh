#!/system/bin/sh
# DRKMUQEET — memory management (sysctl VM, zram, zswap, KSM, THP, hugepages)

mem_usage() {
  cat <<'EOF'
MEMORY:
  drk mem clean                  drop page/dentry/inode caches (trigger)
  drk mem compact                compact memory (trigger)
  drk mem swappiness <n>         vm.swappiness (verified)
  drk mem dirty <n> [bg]         vm.dirty_ratio / dirty_background_ratio
  drk mem vfs <n>                vm.vfs_cache_pressure
  drk mem minfree <kb>           vm.min_free_kbytes (kernel may clamp)
  drk mem overcommit <0|1|2> [ratio]  vm.overcommit_memory/ratio
  drk mem oom <task|panic>       oom killer policy toggles
  drk mem zram <mb> [algo]       create & enable zram swap (verified)
  drk mem zram off               disable & reset zram
  drk mem zswap <0|1> [algo]     zswap compressed swap cache (if kernel has it)
  drk mem ksm <0|1>              kernel samepage merging (if compiled in)
  drk mem thp <always|madvise|never>  transparent huge pages
  drk mem hugepages <n>          vm.nr_hugepages
  drk mem info                   current memory state
EOF
}

mem_main() {
  local a="$1"; [ -n "$a" ] || { mem_usage; return 1; }
  shift
  case "$a" in
    clean) need_root || return 1
      if echo 3 > /proc/sys/vm/drop_caches 2>/dev/null; then ok "cache drop triggered"; else bad "drop_caches failed"; return 1; fi ;;
    compact) need_root || return 1
      if echo 1 > /proc/sys/vm/compact_memory 2>/dev/null; then ok "memory compaction triggered"; else bad "compact failed"; return 1; fi ;;
    swappiness) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk mem swappiness <0-200>"; return 1; }
      sc vm.swappiness "$1" "swappiness" ;;
    dirty) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk mem dirty <ratio> [bg]"; return 1; }
      sc vm.dirty_ratio "$1" "dirty_ratio"
      [ -n "$2" ] && sc vm.dirty_background_ratio "$2" "dirty_background_ratio" ;;
    vfs) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk mem vfs <0-200>"; return 1; }
      sc vm.vfs_cache_pressure "$1" "vfs_cache_pressure" ;;
    minfree) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk mem minfree <kb>"; return 1; }
      scn vm.min_free_kbytes "$1" "$1" "min_free_kbytes" ;;
    overcommit) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk mem overcommit <0|1|2> [ratio]"; return 1; }
      sc vm.overcommit_memory "$1" "overcommit_memory"
      [ -n "$2" ] && sc vm.overcommit_ratio "$2" "overcommit_ratio" ;;
    oom) need_root || return 1
      case "$1" in
        task) sc vm.oom_kill_allocating_task 1 "oom_kill_allocating_task" ;;
        panic) sc vm.panic_on_oom 1 "panic_on_oom" ;;
        *) bad "usage: drk mem oom <task|panic>"; return 1 ;;
      esac ;;
    zram) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk mem zram <mb> [algo]"; return 1; }
      [ "$1" = "off" ] && { mem_zram_off; return 0; }
      mem_zram_on "$1" "$2" ;;
    zswap) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk mem zswap <0|1> [algo]"; return 1; }
      case "$1" in 0|1)
        try_sv "$1" "zswap" /sys/module/zswap/parameters/enabled
        [ -n "$2" ] && try_sv "$2" "zswap compressor" /sys/module/zswap/parameters/compressor ;;
      *) bad "usage: drk mem zswap <0|1>"; return 1 ;; esac ;;
    ksm) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk mem ksm <0|1>"; return 1; }
      case "$1" in 0|1)
        try_sv "$1" "KSM" /sys/kernel/mm/ksm/run
        [ "$1" = "1" ] && try_sv 100 "KSM pages to scan" /sys/kernel/mm/ksm/pages_to_scan ;;
      *) bad "usage: drk mem ksm <0|1>"; return 1 ;; esac ;;
    thp) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk mem thp <always|madvise|never>"; return 1; }
      case "$1" in always|madvise|never)
        try_sv "$1" "transparent huge pages" /sys/kernel/mm/transparent_hugepage/enabled ;;
      *) bad "usage: drk mem thp <always|madvise|never>"; return 1 ;; esac ;;
    hugepages) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk mem hugepages <n>"; return 1; }
      sc vm.nr_hugepages "$1" "nr_hugepages" ;;
    info) mem_info ;;
    *) mem_usage; return 1 ;;
  esac
}

mem_zram_on() {
  local mb="$1" algo="${2:-lz4}" dev=/dev/block/zram0
  [ -e /sys/block/zram0 ] || { bad "zram0 not present in this kernel"; return 1; }
  # reset any existing zram first
  swapoff "$dev" 2>/dev/null
  [ -w /sys/block/zram0/reset ] && echo 1 > /sys/block/zram0/reset 2>/dev/null
  # pick algorithm: verify it is supported
  local algs avail
  avail=$(cat /sys/block/zram0/comp_algorithm 2>/dev/null)
  case " $avail " in
    *"$algo"*) algs=$algo ;;
    *) algs=$(first_of "$(echo "$avail" | tr -cd 'a-z0-9 \n')" 2>/dev/null)
       [ -n "$algs" ] || algs=lzo ;;
  esac
  sv /sys/block/zram0/comp_algorithm "$algs" "zram algorithm"
  local size=$((mb * 1024 * 1024))
  sv /sys/block/zram0/disksize "$size" "zram disksize"
  if [ -x /system/bin/mkswap ]; then
    mkswap "$dev" >/dev/null 2>&1 || { bad "mkswap failed"; return 1; }
    swapon "$dev" >/dev/null 2>&1 || { bad "swapon failed"; return 1; }
  else
    swapon "$dev" >/dev/null 2>&1 || { bad "swapon failed"; return 1; }
  fi
  if grep -q zram /proc/swaps 2>/dev/null; then
    ok "zram swap active: $(grep zram /proc/swaps | awk '{print $2, $3}')"
  else
    bad "zram not visible in /proc/swaps"; return 1
  fi
}

mem_zram_off() {
  swapoff /dev/block/zram0 2>/dev/null
  [ -w /sys/block/zram0/reset ] && echo 1 > /sys/block/zram0/reset 2>/dev/null && ok "zram disabled & reset"
}

mem_info() {
  hdr "Memory"
  grep -E "MemTotal|MemFree|MemAvailable|SwapTotal|SwapFree|Dirty|KernelStack|PageTables" /proc/meminfo
  [ -e /sys/block/zram0 ] && say "  zram: $(cat /sys/block/zram0/disksize 2>/dev/null) bytes, algo $(cat /sys/block/zram0/comp_algorithm 2>/dev/null)"
  [ -r /sys/module/zswap/parameters/enabled ] && say "  zswap enabled: $(cat /sys/module/zswap/parameters/enabled 2>/dev/null)"
  [ -r /sys/kernel/mm/ksm/run ] && say "  ksm run: $(cat /sys/kernel/mm/ksm/run 2>/dev/null)"
  [ -r /sys/kernel/mm/transparent_hugepage/enabled ] && say "  thp: $(cat /sys/kernel/mm/transparent_hugepage/enabled 2>/dev/null)"
}

mem_probe() {
  [ -w /proc/sys/vm/swappiness ] && ok "vm sysctls writable" || bad "vm sysctls"
  [ -e /sys/block/zram0 ] && { ok "zram interface"; } || warn "zram interface absent"
  [ -e /sys/module/zswap ] && { ok "zswap module"; } || warn "zswap module absent"
  [ -e /sys/kernel/mm/ksm ] && { ok "KSM interface"; } || warn "KSM absent"
  return 0
}

mem_status() {
  hdr "Memory"
  say "  swappiness: $(sysctl -n vm.swappiness 2>/dev/null)"
  say "  vfs_cache_pressure: $(sysctl -n vm.vfs_cache_pressure 2>/dev/null)"
  say "  dirty_ratio: $(sysctl -n vm.dirty_ratio 2>/dev/null) / bg $(sysctl -n vm.dirty_background_ratio 2>/dev/null)"
  say "  min_free_kbytes: $(sysctl -n vm.min_free_kbytes 2>/dev/null)"
  say "  overcommit: $(sysctl -n vm.overcommit_memory 2>/dev/null)"
  grep -E "SwapTotal|SwapFree" /proc/meminfo | sed 's/^/  /'
}
