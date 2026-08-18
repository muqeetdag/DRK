#!/usr/bin/env sh
set -eu

if ! command -v gradle >/dev/null 2>&1; then
  echo 'Gradle is required. Run the Android build workflow or install Gradle 8.7 locally.' >&2
  exit 127
fi

gradle --no-daemon assembleRelease
printf '%s\n' 'Created app/build/outputs/apk/release/app-release.apk'
