# DRK

DRKMUQEET is a conservative, reversible Magisk module for Redmi Note 8 Pro (`begonia`). It exposes a local dashboard and guarded performance, balanced, and reset profiles.

## Outputs

- **Magisk package:** `./build.sh` creates `DRKMUQEET-v1.0.0.zip`.
- **Android launcher:** the optional `app/` project creates an APK that opens the module dashboard at `http://127.0.0.1:8080/`. Install and enable the Magisk module first; the APK is only a launcher and does not provide root on its own.

Build the APK with Gradle 8.7 and Android SDK platform 35:

```sh
./build-apk.sh
```

A GitHub Actions workflow is also available under `.github/workflows/build-apk.yml`. The release variant uses the local debug signing key for direct installation and is not a Play Store signing key.
