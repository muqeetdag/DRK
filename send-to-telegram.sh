#!/usr/bin/env sh
set -eu

file=${1:-DRKMUQEET-dashboard.apk}
: "${TELEGRAM_BOT_TOKEN:?Set TELEGRAM_BOT_TOKEN in the shell}"
: "${TELEGRAM_CHAT_ID:?Set TELEGRAM_CHAT_ID in the shell}"

[ -f "$file" ] || {
    printf 'File not found: %s\n' "$file" >&2
    exit 1
}

curl --fail --silent --show-error \
    --request POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendDocument" \
    --form "chat_id=${TELEGRAM_CHAT_ID}" \
    --form "document=@${file}" \
    --form 'caption=DRKMUQEET dashboard APK'
printf '\nSent %s\n' "$file"
