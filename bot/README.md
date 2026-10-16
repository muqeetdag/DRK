# DRK EXIF Bot 🤖

The DRK EXIF Viewer app, living inside Telegram. Send the bot a photo (or an
image file as a document) and it replies with the full evidence card:

- 📍 GPS coordinates (decimal + DMS + altitude)
- 📍 a **native Telegram location pin** (live map, right in the chat)
- 🗺 a direct **Google Maps link** (inline button)
- 📱 device · 🕐 time taken · 📷 camera settings · 🖼 size
- 📄 the complete **raw EXIF dump** (`.txt` file when it doesn't fit a message)

The bot's native menu (`/start`, `/help`, `/about` + menu button) and a
persistent reply keyboard make it feel exactly like the app.

## Run modes

| Mode | Who runs it | Latency |
|---|---|---|
| **GitHub Actions cron** (`ci/drk-bot.yml`, every ~5 min, one batch per run) | free, always on | replies within ~5–10 min |
| `--poll` long-polling | any machine / VPS / Termux on your phone | replies instantly |

```bash
pip install pillow
export TELEGRAM_BOT_TOKEN=123456:abc…     # never commit this
python bot/drk_bot.py --poll              # or: --once for a single batch
```

⚠ Run **exactly one** consumer of the bot's updates at a time (two pollers
will steal each other's updates). The getUpdates offset is persisted in
`bot/state/offset.txt` (committed by the workflow after every batch).

## Activating the 24/7 Actions runner (one-time, ~2 min)

1. **Rotate the token** at [@BotFather](https://t.me/BotFather) (`/revoke`,
   then `/token`) — earlier tokens were shared in transit; treat them burned.
2. Repo → **Settings → Secrets and variables → Actions → New repository secret**:
   name `TELEGRAM_BOT_TOKEN`, value = the **new** token.
3. Copy `ci/drk-bot.yml` to `.github/workflows/drk-bot.yml` (GitHub web UI →
   Add file) — automation tokens can't create workflow files directly.
4. Done. The workflow polls every 5 minutes, answers every pending message,
   and commits the new offset with `[skip ci]`.

## Message flow

```
user sends photo ─▶ getUpdates ─▶ getFile ─▶ download bytes
                                     │
                                     ▼
                         Pillow EXIF extract
                                     │
    ┌────────────────────────────────┼────────────────────────────┐
    ▼                                ▼                            ▼
 sendMessage (evidence card    sendLocation (native        sendMessage/raw.txt
 + Google Maps inline button)   map pin in chat)           (full EXIF dump)
```
