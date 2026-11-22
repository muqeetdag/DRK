# NYX — white-label Instagram shell (LSPosed module + companion console)

A fully customisable, dark, "futuristic" **white-label module for the open-source
[InstaMod / insta-](https://github.com/SmitroniX/insta-)** project: a floating
Instagram dock, media keeper, theme engine and a companion console app — all
driven by one `white-label.json` file, built into a **signed, sideload-ready APK**.

```
nyx/
├── white-label.json           ← your brand: name, tagline, colours, glyph, defaults
├── build.sh                   ← one command → dist/nyx-<version>-<flavor>.apk
├── tools/
│   ├── configure.py           ← bakes the JSON into the source
│   ├── gen_icons.py           ← renders launcher / adaptive / splash art (Pillow)
│   └── xposed-stubs/          ← compile-time Xposed API surface
├── app/                       ← app source (no Gradle, no AndroidX, ~280 KB APK)
│   ├── AndroidManifest.xml
│   ├── assets/xposed_init
│   ├── res/                   ← theme, drawables, strings
│   └── src/io/nyx/app/
│       ├── core/              ← prefs+mirror, palettes, downloader, MediaStore
│       ├── ui/                ← console (custom dark design system, 4 tabs)
│       └── hook/              ← Xposed entry + Instagram attach layer
└── .github-workflow/build-apk.yml  ← copy to .github/workflows/ to build in CI
```

## What it does

**Companion console** (`io.nyx.app`, the app you install)

| Tab | Contents |
| --- | --- |
| **Console** | live status, 8 real feature toggles, open Instagram, setup wizard |
| **Theme** | 5 dark palettes, per-app accent lab, live preview, reset |
| **Media** | vault file count/size, folder rename, one-tap cleaner, open gallery |
| **System** | package/version/flavour, Instagram + LSPosed detection, diagnostics HUD, export settings JSON, wipe |

Everything is drawn with framework views and a small design system
(`AuroraView`, `SwitchView`) so the look is identical on every OEM ROM.

**Inside Instagram** (hooks are guarded — a failure logs and degrades, never crashes the host)

- **Floating dock** — detached translucent pill with a travelling accent bloom,
  raised save-core and theme badge. Tabs drive the host pager (auto-detected),
  the core saves the media on screen, the badge opens the console.
- **Media keeper** — download button injected beside like/share for reels,
  stories and feed posts; pulls the signed CDN URL immediately over TLS and
  publishes to MediaStore (gallery) + your Downloads folder.
- **Long-press to save** — hold any media view to pull it down.
- **Glass sheen** — slim themed gradient header.
- **Zen scroll** — dock slides away while you scroll, returns on scroll-up.
- **Diagnostics HUD** — palette, armed hooks, failure count, last resolved URL.

### White-label

Edit `white-label.json` (or point `NYX_CONFIG=/path/to/brand.json`) and rebuild:

```json
{
  "appName": "NYX",
  "tagline": "Instagram, reimagined.",
  "flavor": "obsidian",
  "version": "1.0.0",
  "versionCode": 100,
  "accent": "#7C5CFF",
  "secondary": "#22D3EE",
  "glyph": "orbit",              // orbit | bolt | prism | wave
  "signature": "Nights outlive empires.",
  "links": { "telegram": "", "github": "https://github.com/muqeetdag/DRK" },
  "defaults": {
    "floatingNav": true, "reelsDownloader": true, "storiesDownloader": true,
    "feedDownloader": true, "downloadFolder": "NYX"
  },
  "releaseChannel": "stable",
  "keyAlias": "nyx",
  "storePassword": "nyx-white-label"
}
```

Ready-made brand variants: `brands/aurora.json`, `brands/solarflare.json`,
`brands/verdant.json`.

`build.sh` rewrites **name, label, tagline, palette, accent colour, launcher
icon, adaptive icon, monochrome icon, splash art and every feature default**,
then builds and signs the APK. The keystore is generated once in
`nyx/keystore/` (git-ignored) — back it up; every update must be signed with
the same key.

## Build

```bash
cd nyx
./build.sh                       # → dist/nyx-1.0.0-obsidian.apk
NYX_CONFIG=brands/aurora.json ./build.sh
```

No Android Studio, Gradle or SDK install is needed. `build.sh` fetches a
portable toolchain from public mirrors into `.build/tools` (git-ignored):
Temurin JRE (`jdk4py` on PyPI), a static `aapt2` (`aaptjs3` on npm),
`android.jar` + `d8` + `apksigner` + ECJ (`@drxiaozhi/minapk` on npm) and
Pillow (PyPI). Output is signed with **v1+v2+v3** schemes and verified.

## Verified in a virtual phone (no emulator needed)

`nyx/.vphone` is a self-contained Android runtime (framework stubs + real
measure/layout/draw + Canvas-to-PNG) that loads the **shipped APK** and drives
it like a user. It has caught real bugs that a static check never would:
FrameLayout children stacked at `0,0`, a `MATCH_PARENT` collapse caused by the
wrong ScrollView child spec, missing `forceUniformWidth`, a 0px-high
HorizontalScrollView, un-distributed weighted margins, and a stale status chip
after a toggle flip.

```bash
cd nyx/.vphone
bash run.sh          # 73 checks, writes artifacts/*.png
```

The suite boots, onboards, opens every console tab, scrolls, flips toggles,
opens dialogs, changes the palette, presses HOME/BACK, survives a restart and
survives a device without Instagram/LSPosed. Every screenshot in
`artifacts/` is rendered by the app's own view tree.

> Caveat: `hook/*` classes are only class-loaded, never executed — there is no
> Instagram process to hook here. Everything in `app/` (console, onboarding,
> prefs, theme, media vault) is really executed.

For CI, copy `.github-workflow/build-apk.yml` to `.github/workflows/` in the
repository root (needs a token with the `workflow` scope) — it builds the APK
on every push and attaches it to a GitHub release.

## Install

1. Root: install **LSPosed** (Zygisk/KernelSU build) and reboot.
2. Install `nyx-<version>-<flavor>.apk`.
3. LSPosed → Modules → **NYX** → enable → scope **Instagram only** → force-stop Instagram.
4. Open Instagram once; open the console and flip whatever you want. Toggles
   apply live (the hook re-reads settings every few hundred ms).
5. No root? Use **LSPatch** in local mode with this APK as the embedded module.

## How settings reach Instagram

LSPosed modules can't write into another app. The console writes its prefs
`MODE_WORLD_READABLE` (LSPosed relocates them for `xposedsharedprefs` modules)
and also mirrors them to a plain XML file under the module's own `files/`.
The hook reads `XSharedPreferences` first, then the mirror, then build-time
defaults — so it keeps working under LSPosed, LSPatch and even a fresh install.

## Media pipeline

Instagram CDN URLs are signed and expire quickly, so pulls happen immediately:
`HTTPS → cache temp file → MediaStore (Pictures/Movies/<Folder>)`, plus a
mirrored copy in `Download/<Folder>`. Only `https://` media hosts are accepted;
everything else is rejected in `UrlResolver.sanitize()`.

## Compatibility

- Android 8.0+ (minSdk 26), targetSdk 35.
- Instagram is obfuscated and changes between releases. The attach layer hooks
  framework seams (`Activity#onCreate/onResume/onDestroy`, `View#onAttachedToWindow`,
  `View#onScrollChanged`) and matches host views by stable class-name keywords,
  not hard-coded obfuscated names. When Meta renames something the affected
  button simply doesn't appear; nothing breaks, and the HUD reports it.
- Built for **personal use on your own device**. Respect Instagram's terms and
  creators' rights when saving media.

## Credits

Based on the open-source MIT project
[`SmitroniX/insta-`](https://github.com/SmitroniX/insta-) (InstaMod). This
repository adds the white-label build system, companion console, theme engine,
media pipeline and the framework-level attach layer.
