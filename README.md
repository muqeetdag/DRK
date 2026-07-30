# DRK · EXIF Viewer & Extractor

> *"One upload, chief. GPS, device, timestamp, all of it extracted and displayed
> without leaving the app."* — Knight

A lightweight Android app that extracts **all EXIF metadata** from a photo and
shows it in one scrollable evidence sheet:

| Field | Source |
|---|---|
| 📍 GPS coordinates (decimal + DMS) + altitude | EXIF GPS IFD |
| 🗺 "Open in Maps" button | standard `geo:` intent (no API key) |
| 📱 Device make / model / software | ExifIFD0 |
| 🕐 Date & time taken | `DateTimeOriginal` |
| 📷 Camera settings | ISO, FNumber, ExposureTime, FocalLength, flash, orientation |
| 🖼 Embedded thumbnail presence + image size / file size | EXIF thumbnail + decoder bounds |
| 📄 Full **raw EXIF dump** (every tag, every directory) | collapsible monospace view |

**100 % offline.** The manifest requests **zero permissions** — image access
comes from the Storage Access Framework (`ACTION_GET_CONTENT`), and there is
no `INTERNET` permission, so no data can leave the device. You can also
**share a photo straight into the app** from any gallery.

---

## The app — two build lanes, same code

```
app/            Canonical Gradle build — Kotlin, minSdk 24, targetSdk 34
                androidx.exifinterface 1.3.7  +  drewnoakes metadata-extractor 2.19.0
                Material3 UI, DayNight theme, coroutines + ActivityResult APIs.

offline-build/  Zero-maven, zero-Gradle variant of the same app (Kotlin against
                framework android.media.ExifInterface), built with plain tools:
                aapt2 → kotlinc → d8 → openssl v1-sign → 4-byte realign.
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

CI (`.github/workflows/android.yml`) does the same on every push and uploads
`app-debug.apk` as a workflow artifact.

## Telegram delivery to your bot

A Telegram bot can never message a user first — **send any message (e.g.
`/start`) to your bot once**, then pick one of:

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

## How it works (mechanism)

1. `ACTION_GET_CONTENT` / share-sheet → SAF grants a per-URI read (no permission dialogs)
2. Pass 1 `ExifInterface` (androidx) for typed headline fields + thumbnail
3. Pass 2 `metadata-extractor` walks every directory (EXIF, GPS, IPTC, XMP, …) → raw dump
4. Render: location · device · time · camera · file facts · collapsible raw table
5. `geo:lat,lon?q=…` intent for Maps; `getAltitude`, DMS formatting, orientation decode

## Testing checklist

- [x] Image with GPS → decimal + DMS coordinates, altitude, "Open in Maps" appears
- [x] Image without GPS → "not embedded in file" + a note explaining why
- [x] Device info → make/model (+software when present)
- [x] Non-photo / stripped file → "No EXIF data found" + error detail
- [x] Share image from Gallery → lands directly in the extractor
- [x] Dark mode (Material3 DayNight lane; the offline lane is dark-native)
