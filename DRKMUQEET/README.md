# DRKMUQEET — Device Tuning Console

A small, open Magisk module for **Redmi Note 8 Pro (begonia)**. It provides a local, mobile-friendly dashboard, three **reversible** profiles, and an **applied-state audit** that tells you whether a tweak actually took.

> This is intentionally not a “god module.” No legitimate module can reliably promise every kernel tweak on every ROM. DRKMUQEET checks whether a kernel node exists and is writable before changing it, and `drk audit` reports the ones it could not change. It does **not** contain root hiding/integrity spoofing, DRM/banking circumvention, game cheats, anti-cheat evasion, premium-feature bypasses, thermal-safety removal, forced hardware overclocks, or fake status toggles.

## Included functionality

- **Performance profile:** switches to `performance` only when that governor is advertised by the installed kernel; sets block read-ahead to 1024 KiB; sets Android animation scales to 0.5x.
- **Balanced profile:** returns the governor to `schedutil` when advertised, sets 512 KiB read-ahead and 0.5x UI animations.
- **Restore defaults:** uses `schedutil` when the kernel advertises it, returns read-ahead to 128 KiB, restores 1x animations, and records the state as `default`.
- **Applied-state audit:** `drk audit` re-reads every node the active profile targets and reports `clean`, `partial`, `degraded`, or `blocked`. Also available as a button on the dashboard.
- **Dashboard:** local-only (`http://127.0.0.1:8080`) status, profile controls, audit readout, and developer contact: [@DRKMUQEETT](https://t.me/DRKMUQEETT).
- **Magisk action:** toggles balanced/performance and displays the selected profile.
- **Boot safety:** saves the selected profile in `/data/adb/drkmuqeet/profile`; all writes are guarded.

## Why the audit exists

Every write in this module is guarded: a node that does not exist or is not writable is skipped rather than forced. That is the correct behaviour on a device, but a silent skip is indistinguishable from success. `drk audit` closes that gap by re-reading the tree and comparing it against what the recorded profile is supposed to have left behind.

| status | meaning |
| --- | --- |
| `clean` | every targeted node holds the expected value |
| `partial` | some node is unwritable, but nothing writable was missed |
| `degraded` | a writable node refused the value, or drifted back after being set |
| `blocked` | no tunable nodes were found at all |

The audit is read-only. It never writes to a kernel node.

## Installation

1. Download `DRKMUQEET-v1.1.0.zip` from this repository’s build output.
2. Flash it in Magisk and reboot.
3. Tap the module’s **Action** button to toggle a profile, or open the local dashboard in a browser on the phone.

The service binds to `127.0.0.1`, not the LAN. A browser must run on the phone to reach it. If port 8080 is occupied, stop the conflicting local service before rebooting.

## CLI

As root:

```sh
drk performance   # governor (if advertised) + 1024 KiB read-ahead + 0.5x animations
drk balanced      # schedutil (if advertised) + 512 KiB read-ahead + 0.5x animations
drk reset         # schedutil + 128 KiB read-ahead + 1x animations; records `default`
drk toggle        # performance <-> balanced
drk stats         # one-line JSON for the dashboard
drk audit         # human-readable applied-state report; also writes /data/adb/drkmuqeet/audit.json
drk audit --json  # the same report as JSON on stdout
```

### Profile states

Three states are recorded in `/data/adb/drkmuqeet/profile`:

- `performance`
- `balanced`
- `default` — written by `drk reset`, meaning “stock values, nothing pinned”

If the file does not exist, the module treats the state as `default` and `drk boot` leaves the kernel exactly as it came up. Nothing is applied on a fresh install until you choose a profile.

## Development harness

`devtools/preview.py` runs the real `drk` script off-device against a stub `/sys` tree, with `settings` and `getprop` shimmed, and serves `webroot/` with the same CGI contract as `system/bin/drk-web`. Profile buttons write to the stub, so the audit reflects them.

```sh
python3 devtools/preview.py   # serves on 0.0.0.0:8080
```

The script honours two QA hooks, both of which fall back to the real paths on a phone:

- `DRK_STATE` — where profile state and audit reports are written
- `DRK_SYSROOT` — where kernel nodes are read from and written to. A value that does not contain `devices/system/cpu` is ignored, so the module can never no-op itself on a real device.

Neither variable is set by `post-fs-data.sh`, `service.sh`, or `action.sh`.

## Compatibility and scope

This package is a shell-based Magisk module and does not bundle a Zygisk native injector, an APK, or a KernelSU-specific privileged UI bridge. Claiming these work without compiling, signing, testing on the target ROM, and obtaining the required user permissions would be misleading. It is suitable for modern Magisk-compatible environments where `busybox httpd` is available. Kernel governor availability varies by ROM/kernel — `drk audit` reports what your specific kernel actually accepted.

## Uninstall

Remove it in Magisk and reboot. `uninstall.sh` first requests a reset and then removes only `/data/adb/drkmuqeet`.

## Build

Run `./build.sh` from the repository root. It reads `version=` from `module.prop`, so the ZIP name cannot drift from the module version. The generated ZIP is ignored by Git.
