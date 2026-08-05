#!/system/bin/sh
# DRKMUQEET — maintenance: doctor (verification harness), status, config

# ---------------- drk doctor ----------------
# Runs interface probes + environment checks and prints an honest ✓/✗/⚠ table.
doctor() {
  local tmp="$DRK_STATE/doctor.txt" pass fail warnc line
  mkdir -p "$DRK_STATE" 2>/dev/null
  : > "$tmp"

  rec() { printf '%s\n' "$*" | tee -a "$tmp"; }
  rec ""
  rec "== DRKMUQEET doctor — verification harness =="
  rec "  device: $(getprop ro.product.model 2>/dev/null) ($(getprop ro.product.device 2>/dev/null))"
  rec "  android: $(getprop ro.build.version.release 2>/dev/null) | kernel: $(uname -r 2>/dev/null)"
  rec "  root: $([ "$(id -u 2>/dev/null)" = 0 ] && echo yes || echo NO)"

  # environment
  if is_android; then rec "  ✓ android environment"; else rec "  ✗ android environment"; fi
  if has magisk; then rec "  ✓ magisk present"; else rec "  ⚠ magisk not found"; fi
  if has settings; then rec "  ✓ settings tool"; else rec "  ⚠ settings tool"; fi
  if has resetprop; then rec "  ✓ resetprop"; else rec "  ⚠ resetprop"; fi

  # interface probes (read-only, safe)
  for probe in cpu_probe gpu_probe mem_probe fs_probe net_probe priv_probe hw_probe \
               sens_probe audio_probe cam_probe disp_probe batt_probe proc_probe \
               log_probe dev_probe acc_probe; do
    rec ""
    rec "== $probe =="
    $probe 2>/dev/null | tee -a "$tmp"
  done

  pass=$(grep -c '✓' "$tmp")
  fail=$(grep -c '✗' "$tmp")
  warnc=$(grep -c '⚠' "$tmp")
  rec ""
  rec "  ───────────────────────────────────"
  rec "  PASS: $pass   FAIL: $fail   WARN: $warnc"
  rec "  FAIL items are hardware/kernel interfaces that do not exist on THIS"
  rec "  device/kernel — they are reported, never faked. See docs/COMPATIBILITY.md."
  rec ""
}

# ---------------- drk status ----------------
status_all() {
  for s in cpu_status gpu_status mem_status fs_status net_status priv_status \
           hw_status sens_status audio_status cam_status disp_status batt_status \
           proc_status log_status dev_status acc_status; do
    $s 2>/dev/null
  done
}

# ---------------- drk config ----------------
config_main() {
  local a="$1"
  case "$a" in
    show|get)
      if [ -f "$DRK_CONF" ]; then
        say "  config: $DRK_CONF"
        grep -vE '^\s*#|^\s*$' "$DRK_CONF" | sed 's/^/    /'
      else
        bad "no config at $DRK_CONF"; return 1
      fi ;;
    set)
      [ -n "$2" ] && [ -n "$3" ] || { bad "usage: drk config set <KEY> <value>"; return 1; }
      [ -f "$DRK_CONF" ] || { bad "no config at $DRK_CONF"; return 1; }
      if grep -qE "^$2=" "$DRK_CONF"; then
        sed -i "s|^$2=.*|$2=$3|" "$DRK_CONF" && ok "$2=$3 written" || { bad "write failed"; return 1; }
      else
        echo "$2=$3" >> "$DRK_CONF" && ok "$2=$3 appended" || { bad "write failed"; return 1; }
      fi ;;
    edit)
      if has vi; then vi "$DRK_CONF"
      elif has nano; then nano "$DRK_CONF"
      else bad "no editor found — edit $DRK_CONF manually"; return 1; fi ;;
    apply|boot) boot_main ;;
    *) cat <<'EOF'
usage: drk config <show|set KEY value|edit|apply>
  drk config show           display current config
  drk config set <K> <v>    set a config key (applied on next boot / drk config apply)
  drk config edit           edit config file in an editor
  drk config apply          apply config now (same as drk --boot)
EOF
      return 1 ;;
  esac
}
