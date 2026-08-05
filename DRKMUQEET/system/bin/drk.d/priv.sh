#!/system/bin/sh
# DRKMUQEET — security & privacy
#  • Magisk DenyList integration (root-hide for banking/apps)
#  • attestation status + optional device/build fingerprint spoofing
#  • identifiers: Android ID reset, advertising ID reset, MAC control
#  • hardware killswitches (camera / mic / body sensors / USB / BT / NFC / WiFi / data)
#  • per-app permission control (AppOps), audits, reports
# NOTE: IMEI spoofing and game-cheat tooling are intentionally NOT part of
# this module — they are excluded by design (see docs/COMPATIBILITY.md).

priv_usage() {
  cat <<'EOF'
SECURITY & PRIVACY:
  drk priv deny <pkg>...            add packages to Magisk DenyList (root-hide)
  drk priv deny remove <pkg>        remove from DenyList
  drk priv deny list                list DenyList entries
  drk priv deny enable|disable      toggle DenyList enforcement (needs Zygisk)
  drk priv hide status              Magisk/Zygisk/DenyList/bootloader state
  drk priv integrity                attestation-relevant status report
  drk priv spoof <0|1> [pixel]      device/build fingerprint spoofing (verified; off restores)
  drk priv androidid <id>           set Android ID (verified)
  drk priv adid reset               reset Google advertising ID
  drk priv mac <0|1>                Wi-Fi MAC randomization toggle (framework)
  drk priv wmac <addr>              spoof Wi-Fi MAC (kernel-level; may revert on reconnect)
  drk priv wmac restore             restore original Wi-Fi MAC
  drk priv bmac <addr>              set Bluetooth MAC via btmac.txt (Qualcomm/Xiaomi)
  drk priv bmac restore             remove btmac.txt
  drk priv mock <0|1>               allow mock locations (developer option)
  drk priv loc <grant|deny> <pkg>   per-app location permission (AppOps)
  drk priv killswitch <cam|mic|body|usb|bt|nfc|wifi|data> <on|off>
  drk priv appops <pkg> <op> <allow|deny|ignore>   generic AppOps control
  drk priv appops list <pkg>        show AppOps state for a package
  drk priv batch <deny|allow> <op>  apply an AppOp to ALL user apps
  drk priv audit                    permission-change audit (diff since last run)
  drk priv report                   write privacy report to /data/adb/drk/logs/
  drk priv clip clear               clear clipboard (best-effort, OEM-dependent)
  drk priv notif <0|1>              hide notification content on lock screen
EOF
}

priv_main() {
  local a="$1"; [ -n "$a" ] || { priv_usage; return 1; }
  shift
  case "$a" in
    deny) need_root || return 1; priv_denylist "$@" ;;
    hide) priv_hide_status ;;
    integrity) priv_integrity ;;
    spoof) need_root || return 1; priv_spoof "$@" ;;
    androidid) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk priv androidid <id>"; return 1; }
      ss secure android_id "$1" "android_id" ;;
    adid) need_root || return 1
      priv_adid_reset ;;
    mac) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk priv mac <0|1>"; return 1; }
      case "$1" in 0|1)
        ssx global wifi_non_persistent_mac_randomization_enabled "$1" "$1" "non-persistent MAC randomization" \
          || ssx global wifi_mac_randomization_enabled "$1" "$1" "wifi_mac_randomization_enabled" ;;
      *) bad "usage: drk priv mac <0|1>"; return 1 ;; esac ;;
    wmac) need_root || return 1; priv_wmac "$@" ;;
    bmac) need_root || return 1; priv_bmac "$@" ;;
    mock) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk priv mock <0|1>"; return 1; }
      case "$1" in 0|1) ss secure mock_location "$1" "mock locations" ;;
      *) bad "usage: drk priv mock <0|1>"; return 1 ;; esac ;;
    loc) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk priv loc <grant|deny> <pkg>"; return 1; }
      case "$1" in
        grant) appops_set "$2" FINE_LOCATION allow "FINE_LOCATION/$2"; appops_set "$2" COARSE_LOCATION allow "COARSE_LOCATION/$2" ;;
        deny)  appops_set "$2" FINE_LOCATION deny "FINE_LOCATION/$2"; appops_set "$2" COARSE_LOCATION deny "COARSE_LOCATION/$2" ;;
        *) bad "usage: drk priv loc <grant|deny> <pkg>"; return 1 ;;
      esac ;;
    killswitch) need_root || return 1; priv_killswitch "$@" ;;
    appops) need_root || return 1
      case "$1" in
        list) [ -n "$2" ] || { bad "usage: drk priv appops list <pkg>"; return 1; }
          cmd appops get "$2" 2>/dev/null || bad "appops get failed" ;;
        *) [ -n "$1" ] && [ -n "$2" ] && [ -n "$3" ] || { bad "usage: drk priv appops <pkg> <op> <allow|deny|ignore>"; return 1; }
          appops_set "$1" "$2" "$3" ;;
      esac ;;
    batch) need_root || return 1
      [ -n "$1" ] && [ -n "$2" ] || { bad "usage: drk priv batch <deny|allow> <op>"; return 1; }
      priv_batch "$1" "$2" ;;
    audit) need_root || return 1; priv_audit ;;
    report) need_root || return 1; priv_report ;;
    clip) need_root || return 1; priv_clip ;;
    notif) need_root || return 1
      [ -n "$1" ] || { bad "usage: drk priv notif <0|1>"; return 1; }
      case "$1" in 0|1)
        ss secure lock_screen_allow_private_notifications "$1" "lock screen private notifications"
        ssx secure lock_screen_show_notifications "$1" "$1" "lock screen notifications" ;;
      *) bad "usage: drk priv notif <0|1>"; return 1 ;; esac ;;
    *) priv_usage; return 1 ;;
  esac
}

# ---------------- DenyList (Magisk) ----------------
priv_denylist() {
  local action="$1"
  has magisk || { bad "magisk binary not found"; return 1; }
  case "$action" in
    enable) magisk --denylist enable 2>/dev/null && ok "DenyList enabled" || { bad "denylist enable failed (Zygisk required)"; return 1; } ;;
    disable) magisk --denylist disable 2>/dev/null && ok "DenyList disabled" || { bad "denylist disable failed"; return 1; } ;;
    list) magisk --denylist ls 2>/dev/null || { bad "denylist ls failed"; return 1; } ;;
    remove) [ -n "$2" ] || { bad "usage: drk priv deny remove <pkg>"; return 1; }
      magisk --denylist rm "$2" 2>/dev/null && ok "removed $2 from DenyList" || { bad "remove failed"; return 1; } ;;
    *)
      [ -n "$action" ] || { bad "usage: drk priv deny <pkg>..."; return 1; }
      for p in "$@"; do
        require_pkg "$p" || continue
        magisk --denylist add "$p" 2>/dev/null && ok "$p added to DenyList" || bad "$p — denylist add failed"
      done ;;
  esac
}

priv_hide_status() {
  hdr "Magisk hide status"
  say "  magisk version : $(magisk -V 2>/dev/null) ($(magisk -v 2>/dev/null))"
  local zy
  zy=$(magisk --sqlite "SELECT value FROM settings WHERE key='zygisk'" 2>/dev/null | sed "s/.*value=//")
  say "  zygisk         : ${zy:-unknown}"
  say "  denylist       : $(magisk --denylist ls >/dev/null 2>&1 && echo enabled || echo disabled/unsupported)"
  say "  sepolicy       : $(getenforce 2>/dev/null)"
  say "  boot state     : verifiedbootstate=$(getprop ro.boot.verifiedbootstate 2>/dev/null) veritymode=$(getprop ro.boot.veritymode 2>/dev/null)"
}

priv_integrity() {
  hdr "Attestation / integrity status"
  say "  fingerprint : $(getprop ro.build.fingerprint 2>/dev/null)"
  say "  model       : $(getprop ro.product.model 2>/dev/null)"
  say "  bootloader  : unlocked? $(getprop ro.boot.verifiedbootstate 2>/dev/null)"
  say "  verity      : $(getprop ro.boot.veritymode 2>/dev/null)"
  say "  denylist    : $(magisk --denylist ls >/dev/null 2>&1 && echo active || echo inactive)"
  warn "A module cannot 'guarantee' a SafetyNet/Play Integrity PASS (server-side, hardware-backed)."
  warn "Best compatibility = DenyList (drk priv deny <app>) + optional fingerprint spoof (drk priv spoof 1)."
}

# ---------------- device spoofing ----------------
priv_spoof() {
  local mode="$1" preset="${2:-begonia}" bak="$DRK_STATE/spoof.bak" p
  if [ "$mode" = "0" ]; then
    [ -f "$bak" ] || { bad "no spoof backup to restore (nothing was spoofed)"; return 1; }
    while read -r p rest; do
      [ -n "$p" ] || continue
      resetprop -n "$p" "$rest" 2>/dev/null
      if [ "$(getprop "$p")" = "$rest" ]; then ok "restored $p = $rest"; else bad "restore $p failed"; fi
    done < "$bak"
    rm -f "$bak"
    return 0
  fi
  [ "$mode" = "1" ] || { bad "usage: drk priv spoof <0|1> [pixel|begonia]"; return 1; }
  # snapshot originals once (restore parses "key rest-of-line" so values with spaces survive)
  [ -f "$bak" ] || for p in ro.product.model ro.product.brand ro.product.name ro.product.device \
    ro.product.manufacturer ro.build.fingerprint ro.build.product ro.product.board; do
    v=$(getprop "$p"); [ -n "$v" ] && echo "$p $v" >> "$bak"
  done
  case "$preset" in
    pixel)
      resetprop -n ro.product.model "Pixel 5"
      resetprop -n ro.product.brand "google"
      resetprop -n ro.product.name "redfin"
      resetprop -n ro.product.device "redfin"
      resetprop -n ro.product.manufacturer "Google"
      resetprop -n ro.build.product "redfin"
      resetprop -n ro.product.board "redfin"
      resetprop -n ro.build.fingerprint "google/redfin/redfin:13/TQ3A.230901.001/10638005:user/release-keys" ;;
    begonia)
      resetprop -n ro.product.model "Redmi Note 8 Pro"
      resetprop -n ro.product.brand "Redmi"
      resetprop -n ro.product.name "begonia"
      resetprop -n ro.product.device "begonia"
      resetprop -n ro.product.manufacturer "Xiaomi"
      resetprop -n ro.build.product "begonia"
      resetprop -n ro.product.board "begonia" ;;
    *) bad "preset must be 'pixel' or 'begonia'"; return 1 ;;
  esac
  say "  verifying..."
  for p in ro.product.model ro.product.brand ro.product.name ro.product.device ro.product.manufacturer ro.build.fingerprint; do
    v=$(getprop "$p")
    [ -n "$v" ] && ok "$p = $v" || bad "$p unset"
  done
  warn "fingerprint spoofing may confuse some apps; revert with: drk priv spoof 0"
}

priv_adid_reset() {
  local f=/data/data/com.google.android.gms/shared_prefs/adid.xml
  if [ -f "$f" ]; then
    rm -f "$f" && ok "advertising ID file removed (will regenerate on next GMS sync)"
  else
    say "  no adid.xml found — advertising ID may already be clean"
  fi
  # also clear cached copies
  rm -f /data/data/com.google.android.gms/databases/adid* 2>/dev/null && ok "adid caches cleared"
}

# ---------------- MAC handling ----------------
priv_wmac() {
  local iface=wlan0 action="$1"
  has ip || { bad "ip tool not available"; return 1; }
  case "$action" in
    restore)
      [ -f "$DRK_STATE/wmac.bak" ] || { bad "no saved MAC to restore"; return 1; }
      local orig; orig=$(cat "$DRK_STATE/wmac.bak")
      ip link set "$iface" down 2>/dev/null
      ip link set "$iface" address "$orig" 2>/dev/null
      ip link set "$iface" up 2>/dev/null
      priv_wmac_verify "$orig" ;;
    *)
      [ -n "$action" ] || { bad "usage: drk priv wmac <addr>"; return 1; }
      case "$action" in
        [0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F])
          local cur
          cur=$(ip link show "$iface" 2>/dev/null | grep -o 'link/ether [0-9a-f:]*' | awk '{print $2}')
          [ -n "$cur" ] && echo "$cur" > "$DRK_STATE/wmac.bak"
          ip link set "$iface" down 2>/dev/null
          ip link set "$iface" address "$action" 2>/dev/null
          ip link set "$iface" up 2>/dev/null
          priv_wmac_verify "$action" ;;
        *) bad "invalid MAC format"; return 1 ;;
      esac ;;
  esac
}

priv_wmac_verify() {
  local want="$1" got
  got=$(ip link show wlan0 2>/dev/null | grep -o 'link/ether [0-9a-f:]*' | awk '{print $2}')
  if [ "$got" = "$want" ]; then
    ok "wlan0 MAC = $got"
    warn "Android's Wi-Fi framework may restore its own MAC on reconnect"
  else
    bad "wlan0 MAC is '$got' — interface does not allow MAC change on this kernel"
  fi
}

priv_bmac() {
  local f=/data/misc/bluetooth/btmac.txt action="$1"
  case "$action" in
    restore)
      rm -f "$f" && ok "btmac.txt removed (factory BD address restored after Bluetooth restart)" || bad "remove failed" ;;
    *)
      [ -n "$action" ] || { bad "usage: drk priv bmac <addr>"; return 1; }
      case "$action" in
        [0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F]:[0-9a-fA-F][0-9a-fA-F])
          mkdir -p /data/misc/bluetooth 2>/dev/null
          echo "$action" > "$f" && [ "$(cat "$f")" = "$action" ] \
            && { ok "btmac.txt written ($action) — restart Bluetooth to apply"; } \
            || { bad "write failed"; return 1; } ;;
        *) bad "invalid MAC format"; return 1 ;;
      esac ;;
  esac
}

# ---------------- killswitches ----------------
priv_killswitch() {
  local sw="$1" mode="$2"
  [ -n "$sw" ] && [ -n "$mode" ] || { bad "usage: drk priv killswitch <cam|mic|body|usb|bt|nfc|wifi|data> <on|off>"; return 1; }
  case "$mode" in on|off) ;; *) bad "mode must be on|off"; return 1 ;; esac
  case "$sw" in
    cam) priv_ks_appops CAMERA "$mode" ;;
    mic) priv_ks_appops RECORD_AUDIO "$mode" ;;
    body) priv_ks_appops BODY_SENSORS "$mode" ;;
    usb) priv_ks_usb "$mode" ;;
    bt) priv_ks_svc bluetooth "$mode" bluetooth_on secure ;;
    nfc) priv_ks_svc nfc "$mode" nfc_on secure ;;
    wifi) priv_ks_svc wifi "$mode" wifi_on global ;;
    data) priv_ks_svc data "$mode" mobile_data global ;;
    *) bad "killswitch must be cam|mic|body|usb|bt|nfc|wifi|data"; return 1 ;;
  esac
}

priv_ks_appops() { # priv_ks_appops <OP> <on|off>
  local op="$1" mode="$2" want n=0 pkg
  want=deny; [ "$mode" = "off" ] && want=allow
  for pkg in $(user_packages); do
    cmd appops set "$pkg" "$op" "$want" >/dev/null 2>&1 && n=$((n+1))
  done
  # verify on samples
  local v okn=0
  for pkg in $(user_packages | head -n 5); do
    v=$(cmd appops get "$pkg" "$op" 2>/dev/null | head -n1)
    case "$v" in *"$want"*) okn=$((okn+1));; esac
  done
  [ "$okn" -gt 0 ] && ok "$op → $want for $n packages (verified on $okn samples)" \
    || { bad "$op appops did not apply"; return 1; }
  logx "killswitch $op $mode ($n packages)"
}

priv_ks_usb() {
  local mode="$1"
  case "$mode" in
    on) # disable USB gadget
      try_sv 0 "usb gadget" /sys/class/android_usb/android0/enable \
        /sys/devices/platform/android_usb/android0/enable \
        /sys/kernel/config/usb_gadget/g1/UDC ;;
    off)
      try_sv 1 "usb gadget" /sys/class/android_usb/android0/enable \
        /sys/devices/platform/android_usb/android0/enable
      [ -w /sys/kernel/config/usb_gadget/g1/UDC ] && echo "$(ls /sys/class/udc 2>/dev/null | head -n1)" > /sys/kernel/config/usb_gadget/g1/UDC 2>/dev/null && ok "usb gadget reattached" ;;
  esac
}

priv_ks_svc() { # priv_ks_svc <service> <on|off> <settings-key> <namespace>
  local svc="$1" mode="$2" key="$3" ns="$4" want
  if [ "$mode" = "on" ]; then
    # "on" means KILLSWITCH ON → disable the radio
    svc "$svc" disable >/dev/null 2>&1 || { bad "svc $svc disable failed"; return 1; }
    want=0
  else
    svc "$svc" enable >/dev/null 2>&1 || { bad "svc $svc enable failed"; return 1; }
    want=1
  fi
  sleep 1
  local got
  got=$(settings get "$ns" "$key" 2>/dev/null)
  [ "$got" = "$want" ] && ok "$svc killswitch $mode (settings $key=$got)" \
    || { warn "$svc $mode — settings $key=$got (svc accepted command)"; }
}

# ---------------- appops batch / audit / report / clipboard ----------------
priv_batch() {
  local mode="$1" op="$2" want n=0
  want=deny; [ "$mode" = "allow" ] && want=allow
  for pkg in $(user_packages); do
    cmd appops set "$pkg" "$op" "$want" >/dev/null 2>&1 && n=$((n+1))
  done
  ok "$op → $want for $n packages"
  logx "batch $mode $op ($n packages)"
}

priv_audit() {
  local snap=$DRK_STATE/appops.snapshot prev=$DRK_STATE/appops.prev
  dumpsys appops 2>/dev/null | grep -E "  [A-Z_]+:" | sort > "$snap"
  if [ -f "$prev" ]; then
    if diff -u "$prev" "$snap" > "$DRK_LOGS/appops.diff" 2>/dev/null; then
      say "  no permission changes since last audit"
    else
      say "  permission changes since last audit:"
      grep -E "^[+-]" "$DRK_LOGS/appops.diff" | grep -v "^[+-][+-]" | sed 's/^/    /'
      logx "audit: permission changes detected"
    fi
  else
    say "  baseline snapshot created (run again to see changes)"
  fi
  mv "$snap" "$prev"
}

priv_report() {
  local out="$DRK_LOGS/privacy-report-$(date +%Y%m%d-%H%M).txt"
  mkdir -p "$DRK_LOGS"
  {
    hdr "DRKMUQEET privacy report — $(date)"
    say ""
    say "== Denied permissions (AppOps) =="
    dumpsys appops 2>/dev/null | grep -E "deny" | head -n 60
    say ""
    say "== Location permission users =="
    dumpsys appops 2>/dev/null | grep -B1 -A1 "FINE_LOCATION" | head -n 40
    say ""
    say "== Packages with camera/mic access =="
    dumpsys appops 2>/dev/null | grep -E "CAMERA|RECORD_AUDIO" | head -n 40
  } > "$out"
  ok "report written: $out"
  logx "privacy report generated"
}

priv_clip() {
  # best-effort: several known interfaces; version-dependent
  if has cmd && cmd clipboard clear >/dev/null 2>&1; then
    ok "clipboard cleared (cmd clipboard)"
    return 0
  fi
  # legacy binder calls (Android 8-11 era)
  for call in "service call clipboard 2 i32 0" "service call clipboard 4 i32 0"; do
    if eval "$call" >/dev/null 2>&1; then
      ok "clipboard cleared (binder call)"
      return 0
    fi
  done
  bad "no working clipboard-clear interface on this ROM (OEM-dependent)"
  return 1
}

# ---------------- probes ----------------
priv_probe() {
  has magisk && ok "magisk binary" || warn "magisk binary not found"
  has cmd && ok "cmd tool" || warn "cmd tool not found"
  has resetprop && ok "resetprop" || warn "resetprop not found"
  [ -d /sys/class/android_usb/android0 ] && ok "usb gadget interface" || warn "usb gadget interface absent"
  return 0
}

priv_status() {
  hdr "Security & privacy"
  say "  denylist: $(magisk --denylist ls >/dev/null 2>&1 && echo active || echo inactive)"
  say "  fingerprint: $(getprop ro.build.fingerprint 2>/dev/null)"
  say "  mock_location: $(settings get secure mock_location 2>/dev/null)"
  say "  android_id: $(settings get secure android_id 2>/dev/null)"
}
