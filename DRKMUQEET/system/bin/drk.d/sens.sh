#!/system/bin/sh
# DRKMUQEET — sensors
# begonia hardware reality: accelerometer, gyroscope, magnetometer, proximity,
# light, step counter (virtual), orientation family (virtual).
# NO heart-rate/SpO2/ECG/EEG/EMG/GSR/barometer/humidity/UV hardware exists on
# this device — those are documented in COMPATIBILITY.md, not faked here.

sens_usage() {
  cat <<'EOF'
SENSORS:
  drk sens info                 list every sensor, type, vendor, range (dumpsys sensorservice)
  drk sens reset <accel|gyro|mag|prox|light|all>   delete calibration data (kernel re-calibrates)
EOF
}

sens_main() {
  local a="$1"; [ -n "$a" ] || { sens_usage; return 1; }
  shift
  case "$a" in
    info) sens_info ;;
    reset) need_root || return 1; sens_reset "$1" ;;
    *) sens_usage; return 1 ;;
  esac
}

sens_info() {
  hdr "Sensors"
  if has dumpsys; then
    dumpsys sensorservice 2>/dev/null | sed -n '/Sensors:/,/^$/p' | head -n 40 | sed 's/^/  /'
  else
    bad "dumpsys not available"
  fi
}

sens_reset() {
  local which="$1" d f n=0
  [ -n "$which" ] || { bad "usage: drk sens reset <accel|gyro|mag|prox|light|all>"; return 1; }
  case "$which" in accel|gyro|mag|prox|light|all) ;; *) bad "unknown sensor '$which'"; return 1 ;; esac
  # Known calibration locations on begonia (Qualcomm sensors HAL)
  for d in /data/vendor/sensors /mnt/vendor/persist/sensors /persist/sensors /data/misc/sensors; do
    [ -d "$d" ] || continue
    for f in "$d"/*; do
      [ -f "$f" ] || continue
      case "$(basename "$f")" in
        *accel*|*gyro*|*mag*|*prox*|*light*|*calib*|*cal*)
          case "$which" in
            all) ;;
            accel) case "$(basename "$f")" in *accel*) ;; *) continue ;; esac ;;
            gyro)  case "$(basename "$f")" in *gyro*) ;; *) continue ;; esac ;;
            mag)   case "$(basename "$f")" in *mag*) ;; *) continue ;; esac ;;
            prox)  case "$(basename "$f")" in *prox*) ;; *) continue ;; esac ;;
            light) case "$(basename "$f")" in *light*) ;; *) continue ;; esac ;;
          esac
          rm -f "$f" && ok "removed calibration: $f" && n=$((n+1))
          ;;
      esac
    done
  done
  if [ "$n" -gt 0 ]; then
    say "  calibration files cleared — sensors will re-calibrate automatically"
    warn "recalibration may take a few minutes of use"
  else
    bad "no calibration files found (this kernel may store them elsewhere)"
  fi
}

sens_probe() {
  has dumpsys && ok "sensorservice dump available" || bad "dumpsys"
  return 0
}

sens_status() {
  hdr "Sensors"
  dumpsys sensorservice 2>/dev/null | grep -c "0x" | sed 's/^/  sensor entries: /'
}
