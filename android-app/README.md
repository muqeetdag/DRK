# DRK Android App - Native APK

This folder contains a full Android Studio project for the DRK Device Tuning Console.

## Two build paths

### 1. Python-built minimal APK (no Android SDK required)
The repository root contains a pure-Python toolchain that builds a minimal working APK without needing Java/Gradle. This is what was used in this sandbox where Android SDK was not available.

Files:
- `apk_builder/dexgen_full.py` - generates `classes.dex` from scratch (Dalvik bytecode) using Python struct
- `apk_builder/build_manifest.py` - generates binary AndroidManifest.xml (AXML format)
- `apk_builder/build_apk.py` - packages and signs APK with v1 JAR signature using openssl

Output: `apk_builder/DRK-Device-Tuning.apk` (3.1KB)

The minimal APK simply opens a WebView to `http://127.0.0.1:8080` - the local dashboard provided by the Magisk module `DRKMUQEET`.

When installed on a device with the module active, it shows the same UI as the web dashboard, allowing you to switch between Balanced/Performance/Default profiles.

### 2. Full Android Studio build (recommended for Play Store)
Open `android-app/` in Android Studio:

```
android-app/
  app/src/main/java/com/drkmuqeet/app/MainActivity.java
```

Full features:
- WebView to local dashboard (http://127.0.0.1:8080)
- Fallback native UI with direct root commands (`su -c drk ...`) if HTTP server unavailable
- Battery, thermal, uptime stats via HTTP JSON or `drk stats` CLI
- Buttons: Apply performance, Apply balanced, Restore defaults, Toggle
- Handles back press for WebView navigation
- Material dark theme matching web dashboard

Build:

```sh
cd android-app
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # release APK (needs signing)
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Why WebView + fallback?

The Magisk module DRKMUQEET already provides a local HTTP server on 127.0.0.1:8080 with CGI controls.
The simplest app is a WebView wrapper. The full app adds fallback root execution so it works even if port 8080 is occupied or BusyBox httpd fails.

## Security notes

This app intentionally does NOT:
- Bypass banking protections, Play Integrity, SafetyNet
- Disable thermal protection
- Overclock beyond kernel-exposed governors
- Modify games or premium features

It only toggles CPU governors that your kernel advertises, adjusts I/O read-ahead (1024 KiB performance, 512 balanced, 128 default) and animation scales (0.5x).

## Module dependency

The app requires the Magisk module DRKMUQEET to be installed:
1. Flash `DRKMUQEET-v1.0.0.zip` in Magisk and reboot
2. Install the APK
3. Open DRK app - it will load http://127.0.0.1:8080 or use root fallback

CLI equivalent (root shell):

```
drk performance
drk balanced
drk reset
drk stats
```

## Contact

Developer: [@DRKMUQEETT](https://t.me/DRKMUQEETT)
