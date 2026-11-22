#!/usr/bin/env bash
# Runs the shipped APK on every Android version we claim to support.
#
# The virtual phone re-boots with a different Build.VERSION.SDK_INT each round,
# so the same code paths the app guards with Build.VERSION checks are actually
# taken - not merely reviewed.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NYX="$(dirname "$HERE")"
JAVA="${NYX}/.build/tools/jre/bin/java"
APK="${NYX}/dist/${NYX_APK:-nyx-1.3.0-obsidian-root.apk}"
APIS="${APIS:-21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36}"

cd "$HERE"
[ -x "$JAVA" ] || { echo "run 'bash ../build.sh' once to fetch the toolchain" >&2; exit 1; }
[ -f "$APK" ] || { echo "APK missing: $APK" >&2; exit 1; }

pass=0
fail=0
failed_apis=()

printf '%-6s %-10s %s\n' "API" "ANDROID" "RESULT"
for api in $APIS; do
  rm -rf run
  out="$("$JAVA" -Dvphone.sdk="$api" -Djava.awt.headless=true \
        -cp fw/out:app/out com.nyx.vphone.VPhone "$APK" "artifacts-${NYX_TAG:-root}-api$api" 2>&1)"
  release="$(printf '%s' "$out" | sed -n 's/.*android=\([^ ]*\) (API.*/\1/p' | head -1)"
  summary="$(printf '%s' "$out" | tail -1)"
  if printf '%s' "$summary" | grep -qE '^0 failed$|,[[:space:]]*0 failed$'; then
    pass=$((pass + 1))
    printf '%-6s %-10s ok   (%s)\n' "$api" "$release" "$summary"
  else
    fail=$((fail + 1))
    failed_apis+=("$api")
    printf '%-6s %-10s FAIL (%s)\n' "$api" "$release" "$summary"
    printf '%s\n' "$out" | grep -E '^  FAIL' | sed 's/^/        /'
  fi
done

echo
echo "API matrix: $pass passed, $fail failed"
if [ "$fail" -ne 0 ]; then
  echo "failed APIs: ${failed_apis[*]}"
  exit 1
fi
