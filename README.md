# DRK · EXIF Viewer & Extractor

> *"One upload, chief. GPS, device, timestamp, all of it extracted and displayed
> without leaving the app."* — Knight

A lightweight Android app that extracts **all EXIF metadata** from a photo and
shows it in one scrollable evidence sheet:

| Field | Source |
|---|---|
| 📍 GPS coordinates (decimal + DMS) + altitude | EXIF GPS IFD |
| 🗺 **Built-in Google Maps preview** — pin shown directly on upload | key-less embed in a framework `WebView` (tiles need internet) |
| 🗺 "Open in Google Maps ↗" button + copyable direct link | Maps app / `geo:` intent / browser (no API key) |
| 📱 Device make / model / software | ExifIFD0 |
| 🕐 Date & time taken | `DateTimeOriginal` |
| 📷 Camera settings | ISO, FNumber, ExposureTime, FocalLength, flash, orientation |
| 🖼 Embedded thumbnail presence + image size / file size | EXIF thumbnail + decoder bounds |
| 📄 Full **raw EXIF dump** (every tag, every directory) | collapsible monospace view |

**Privacy, honestly.** All EXIF analysis is 100 % on-device — the photo never
leaves your phone. Since v1.2 the single networked feature is the built-in
Google Maps preview (it streams map tiles from Google only while you view the
pin); every other feature keeps working fully offline. No storage permission
is requested: image access comes from the Storage Access Framework
(`ACTION_GET_CONTENT`). You can also **share a photo straight into the app**
from any gallery.

---

## The app — two build lanes, same code

```
app/            Canonical Gradle build — Kotlin, minSdk 24, targetSdk 34
                androidx.exifinterface 1.3.7  +  drewnoakes metadata-extractor 2.19.0
                Material3 UI, DayNight theme, coroutines + ActivityResult APIs.

offline-build/  Zero-maven, zero-Gradle variant of the same app (Kotlin against
                framework android.media.ExifInterface), built with plain tools:
                aapt2 → kotlinc → d8 → openssl v1-sign + v2 block → 4-byte realign.
                Signed with the committed release key (offline-build/keystore/), so
                every build upgrades the previous one cleanly.
                See offline-build/README.md. Output: offline-build/out/drk-exif-offline.apk
```

The Gradle lane is the spec-grade app builder output (`androidx` +
`metadata-extractor` give the deepest tag coverage). The offline lane exists so
the APK can be produced and statically verified on machines with no Maven /
Gradle access at all, and its scripts document every build step end-to-end.

### Build the Gradle lane

```bash
gradle :app:assembleDebug        # or ./gradlew if you add a wrapper jar
# → app/build/outputs/apk/debug/app-debug.apk
```

Two ready-made workflows live in `ci/` (`android.yml`, `telegram-deliver.yml`).
To activate them, copy both files into `.github/workflows/` with the GitHub web
UI (*Add file → Create new file*, one file each) — repo-owner commits carry the
`workflows` scope that bots lack. `android.yml` builds `app-debug.apk` on every
push and uploads it as a workflow artifact; `telegram-deliver.yml` delivers the
committed offline APK to your bot on demand (see below).

## Telegram delivery to your bot

A Telegram bot can never message a user first — **send any message (e.g.
`/start`) to your bot once**, then pick one of:

**0. Zero-setup, works anywhere right now**

Any machine with open internet can deliver the APK in one line:

```bash
curl -F "chat_id=<your chat id>" \
     -F "document=@drk-exif-offline.apk" \
     "https://api.telegram.org/bot<TOKEN>/sendDocument"
```

Find your chat id by messaging the bot and opening
`https://api.telegram.org/bot<TOKEN>/getUpdates`.

**A. Repo secret (set once, then automatic on every push)**

1. Repo → *Settings → Secrets and variables → Actions → New repository secret*
2. Name: `TELEGRAM_BOT_TOKEN` · Value: your bot token → *Add secret*
3. Push (or re-run the `android-build` workflow). The workflow polls
   `getUpdates`, finds your chat, and `sendDocument`s the APK.

**B. One-off, no secret stored (Actions → `telegram-deliver`)**

1. Repo → *Actions → telegram-deliver → Run workflow*
2. Paste the bot token in the `bot_token` input (leave `chat_id` empty to
   auto-discover from the bot's updates) → *Run workflow*
3. The job sends `offline-build/out/drk-exif-offline.apk` to your chat.
   Delete the run afterwards if you don't want the input values retained on
   the run page (`gh run delete` or the ••• menu).

`tools/telegram-send.py` is a third option for workstations: an MTProto
(telethon) sender that works even where the HTTPS Bot API is unreachable.

## The bot — DRK inside Telegram 🤖

The same evidence reader also lives in your bot: send @DRKMUQEETsbot any photo
and it answers with the full EXIF card — GPS (decimal + DMS), a **native
Telegram map pin**, a **Google Maps inline button**, device, timestamp, camera
settings, and the complete raw dump. Native app-like menu (`/start` `/help`
`/about` + keyboard) comes pre-wired via the Bot API.

Worker: `bot/drk_bot.py` (stdlib + Pillow only). Two ways to keep it alive:
`ci/drk-bot.yml` — a free GitHub Actions cron that polls every ~5 min — or
`python bot/drk_bot.py --poll` on any always-on host for instant replies.
Setup (rotate token → secret → copy workflow): see `bot/README.md`.

## How it works (mechanism)

1. `ACTION_GET_CONTENT` / share-sheet → SAF grants a per-URI read (no permission dialogs)
2. Pass 1 `ExifInterface` (androidx) for typed headline fields + thumbnail
3. Pass 2 `metadata-extractor` walks every directory (EXIF, GPS, IPTC, XMP, …) → raw dump
4. Render: location (+ embedded Google Maps pin) · device · time · camera · file facts · collapsible raw table
5. `geo:lat,lon?q=…` intent for Maps; `getAltitude`, DMS formatting, orientation decode

## Testing checklist

- [x] Image with GPS → decimal + DMS coordinates, altitude, "Open in Maps" appears
- [x] GPS image → in-app Google Maps panel drops the pin directly on upload (v1.2)
- [x] Image without GPS → "not embedded in file" + a note explaining why
- [x] Device info → make/model (+software when present)
- [x] Non-photo / stripped file → "No EXIF data found" + error detail
- [x] Share image from Gallery → lands directly in the extractor
- [x] Dark mode (Material3 DayNight lane; the offline lane is dark-native)
