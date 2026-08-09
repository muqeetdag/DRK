# DRK • DRKMUQEET Device Tuning Console + Android APK

A reversible, safety-first tuning toolkit for **Redmi Note 8 Pro (begonia)** and similar devices.

This repo contains:

1. **Magisk Module `DRKMUQEET`** - `DRKMUQEET/` folder, builds to `DRKMUQEET-v1.0.0.zip`
   - Local dashboard at `http://127.0.0.1:8080` (busybox httpd, loopback only)
   - Profiles: Performance (only if kernel exposes `performance` governor, 1024 KiB read-ahead, 0.5x animations), Balanced (512 KiB, 0.5x), Reset (schedutil if available, 128 KiB, 1x)
   - CLI: `drk performance|balanced|reset|toggle|stats`

2. **Android APK `DRK-Device-Tuning`** - native wrapper for the dashboard
   - Minimal Python-built APK `DRK-Device-Tuning-v1.0.0.apk` (3.2KB) - WebView to 127.0.0.1:8080, built without Android SDK using pure Python DEX generation
   - Full Android Studio project in `android-app/` - WebView + fallback native UI with direct root `su -c drk` execution, stats polling

## Quick Install

1. Flash Magisk module `DRKMUQEET-v1.0.0.zip` and reboot
2. Install APK `DRK-Device-Tuning-v1.0.0.apk`
3. Open DRK app:
   - If module's httpd is running, you'll see the web dashboard (battery, thermal, uptime, profile buttons)
   - If port 8080 unreachable, fallback native UI appears with direct root controls

Or via CLI (root):

```sh
drk performance
drk balanced
drk reset
drk stats
```

## Building

### Magisk ZIP
```sh
./build.sh
```

### Minimal APK (no SDK needed)
This uses the same technique described in [Building APK on phone with Claude Code + Termux](https://jasonmoon.dev/blog/2026-02-27-claude-code-mobile-apk-build) - generate Dalvik bytecode directly with Python's `struct`.

```sh
cd apk_builder
python3 build_manifest.py   # binary AndroidManifest.xml (AXML)
python3 dexgen_full.py      # classes.dex from scratch
python3 build_apk.py        # package + v1 sign with openssl
# Output: DRK-Device-Tuning.apk
```

The build script does:
- DEX header, string_ids, type_ids, proto_ids, method_ids, class_defs
- Code items for MainActivity.<init> and onCreate with WebView logic (invoke-super, new-instance, invoke-direct, invoke-virtual, const-string, etc.)
- Binary AXML manifest with string pool, namespace, start/end elements
- v1 JAR signing: MANIFEST.MF, CERT.SF, CERT.RSA via openssl cms

Result is 3.2KB installable APK.

### Full APK (Android Studio)
```sh
cd android-app
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

Full MainActivity features:
- WebView loads local dashboard, fallback if unreachable
- Native UI with profile/battery/thermal/uptime
- Executes `su -c drk <action>` when HTTP fails
- Periodic stats refresh via HTTP `/cgi-bin/control?action=stats` or CLI JSON
- Back press handles WebView navigation

## What this will NOT do

- No root hiding / Play Integrity spoofing
- No bypassing banking apps, DRM, ads, paid features
- No game cheats
- No thermal safety removal / forced overclocks beyond kernel-exposed limits
- No fake status toggles

All tuning is reversible and checks kernel node writability before changing.

## Project Layout

```
DRK/
  DRKMUQEET/                # Magisk module
    module.prop
    system/bin/drk          # core logic (conservative)
    system/bin/drk-web      # CGI handler
    webroot/                # dashboard HTML/CSS/JS
  apk_builder/              # Pure-Python minimal APK toolchain
    dexgen_full.py          # DEX generator
    build_manifest.py       # AXML generator
    build_apk.py            # APK packer + signer
    DRK-Device-Tuning.apk   # output
  android-app/              # Full Android Studio project
    app/src/main/java/.../MainActivity.java
    app/src/main/AndroidManifest.xml
  DRKMUQEET-v1.0.0.zip      # Magisk module zip
  DRK-Device-Tuning-v1.0.0.apk
  build.sh
```

## Developer

[@DRKMUQEETT](https://t.me/DRKMUQEETT) on Telegram

## License

See module README. This project is intentionally minimal and auditable - shell scripts only for module, Python for APK builder.
