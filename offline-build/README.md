# offline-build — the zero-dependency lane

Same app as `/app`, but compilable **without Gradle, Maven, or any network
package fetching** — useful where the Android toolchain can't reach
`maven.google.com` / `repo.maven.apache.org`.

Differences vs the canonical lane (intentional):

- Framework `android.media.ExifInterface` instead of androidx — the InputStream
  constructor exists since API 24, which is our minSdk.
- No `metadata-extractor` (drewnoakes), so the raw dump lists all tags the
  framework exposes plus a curated "Extra" set, rather than every IPTC/XMP tag.
- Framework widgets + `Theme.Material` (dark-native) instead of AppCompat/Material3.
- `onActivityResult` rather than Activity Result APIs (no androidx.activity).
- targetSdk **29**, so the classic **v1 (JAR) signature** alone is valid —
  modern `apksigner` v2 is only mandatory from targetSdk 30.

## Pipeline (`build.sh`)

| Step | Tool | What it does |
|---|---|---|
| 1 | `tools/make_icons.py` | draws the lens launcher icon as PNG mipmaps (pure zlib) |
| 2 | `aapt2 compile` | resources → `compiled.flata` |
| 3 | `aapt2 link` | manifest + flata → unsigned APK + symbol table (`--output-text-symbols`) |
| 4 | `tools/gen_r.py` + `kotlinc` | symbol table → `R.kt`, then Kotlin → `.class` against `android.jar` |
| 5 | `d8` (from r8.jar) | `.class` + `kotlin-stdlib.jar` → `classes.dex` (`--min-api 24`) |
| 6 | `tools/signalign.py` + openssl | injects dex, writes `MANIFEST.MF`/`CERT.SF`, PKCS#7 `CERT.RSA` (sha256, content+attrs embedded), rebuilds the zip with STORED entries 4-byte aligned |
| 7 | keytool + `aapt2 dump badging` | structural verification |

Only the entries listed below are required; point `build.sh` at them via env:

```
AAPT2        aapt2 binary                (e.g. npm package aaptjs3)
ANDROID_JAR  API-34 android.jar          (Sable/android-platforms @ android-34/android.jar)
KOTLINC      kotlinc launcher            (npm package kotlin-compiler)
KOTLIN_STDLIB kotlin-stdlib.jar          (same package's lib/)
R8_JAR       r8-master.jar               (LineageOS/android_prebuilts_r8)
JRE_HOME     any JRE (needs `java` + `keytool`; jdk4py from PyPI works)
```

Output: `out/drk-exif-offline.apk`
