# Build artifacts

| File | What it is |
| --- | --- |
| `NYX-1.0.0-obsidian.apk` | default build — violet/cyan, "orbit" glyph |
| `AURORA-1.0.0-aurora.apk` | teal/violet, "wave" glyph |
| `nyx-white-label-kit-1.0.0.zip` | full source kit + all four demo APKs |

All APKs are signed (APK Signature Scheme v2+v3), minSdk 26, package `io.nyx.app`.
Regenerate any variant with `cd nyx && NYX_CONFIG=brands/<name>.json ./build.sh`.
Keystores live in `nyx/keystore/` and are git-ignored — back them up before shipping updates.
