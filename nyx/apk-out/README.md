# NYX 1.1.0 — all-in-one

Single APK, package `io.nyx.app`, minSdk 26 / targetSdk 35, signed v2+v3.

| file | sha256 |
| --- | --- |
| `nyx-1.1.0-obsidian.apk` | `e7a6facb9148f28c80eeb5c56f12fe5a316b924fe90cad1d6a151639f2ca43e0` |

## What changed from 1.0.0
- **First-run chooser** — the launcher is now `OnboardingActivity`: pick ROOT (LSPosed)
  or NON-ROOT (LSPatch) and the console shows the matching 4-step attach guide.
- **All-in-one TWEAKS tab** — every toggle on one page (dock, glass header, zen scroll,
  reels/story/feed keeper, save button, long-press save, haptics, diagnostics HUD)
  plus quick actions, instead of being scattered across tabs.
- **Credits** — "APP MADE BY @DRKMUQEETT" card with a direct Telegram button, on both
  the onboarding screen and the console.
- **Crash hardening** (this is what fixes the 1.0.0 crash reports):
  - global `View#onAttachedToWindow` / `View#onScrollChanged` hooks removed — they fired
    thousands of times per second inside Instagram and caused ANR/jank; decoration is now
    activity-scoped, debounced and budget-limited;
  - zero-size `RadialGradient` guard in the console background, the splash and the dock
    (a 0×0 frame used to throw `IllegalArgumentException`);
  - the dock's glow no longer re-invalidates itself at 60 fps (capped at ~30 and only
    while visible), and listeners are dropped on `destroy()`;
  - `SafePrefs` (never throws on corrupt prefs) + `CrashLog` + a global uncaught handler
    that logs and repairs instead of dying;
  - SYSTEM → "Last crash report" copies the log, "Repair settings" rebuilds prefs.

## Install
Root: LSPosed → enable NYX → scope Instagram only → force-stop Instagram → reopen.
Non-root: LSPatch → manage → Instagram → local mode + add NYX → patch → install patched IG.

Signature differs from 1.0.0 (keystore was regenerated), so uninstall the old
`io.nyx.app` before installing over it.

## Checks that ran before shipping
`tests/apk_checks.py` — 17/17 pass (activities present in dex, launcher = onboarding,
all resources resolve, guards present, unsafe hooks absent, no unbaked `__TOKEN__`,
credit baked). Preference/onboarding state logic: 15/15 unit tests. No device or
emulator was available in this sandbox — see `.ci/emulator-smoke.yml`.
