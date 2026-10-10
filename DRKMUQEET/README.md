# DRKMUQEET — Device Tuning Console

A small, open Magisk module for **Redmi Note 8 Pro (begonia)**. It provides a local, mobile-friendly dashboard, three **reversible** profiles, a **thermal guard** that backs tuning off before the kernel has to intervene, and an **applied-state audit** that proves whether a tweak actually took.

> This is intentionally not a “god module.” No legitimate module can reliably promise every kernel tweak on every ROM. DRKMUQEET checks whether a kernel node exists and is writable before changing it, and `drk audit` reports the ones it could not change. It does **not** contain root hiding/integrity spoofing, DRM/banking circumvention, game cheats, anti-cheat evasion, premium-feature bypasses, thermal-safety removal, forced hardware overclocks, or fake status toggles.

## Included functionality

- **Performance profile:** switches to `performance` only when that governor is advertised by the installed kernel; sets block read-ahead to 1024 KiB; sets Android animation scales to 0.5x.
- **Balanced profile:** returns the governor to `schedutil` when advertised, sets 512 KiB read-ahead and 0.5x UI animations.
- **Restore defaults:** uses `schedutil` when the kernel advertises it, returns read-ahead to 128 KiB, restores 1x animations, stops the thermal guard, and records the state as `default`.
- **Thermal guard:** watches the hottest thermal zone while the performance profile is active and backs the tuning off ahead of the kernel's own thermal engine, then hands it back once the device has genuinely cooled.
- **Applied-state audit:** `drk audit` re-reads every node the active profile targets and reports `clean`, `partial`, `degraded`, `unsafe`, or `blocked`. Also a button on the dashboard.
- **Dashboard:** local-only (`http://127.0.0.1:8080`) status, profile controls, guard controls and journal, audit readout, and developer contact: [@DRKMUQEETT](https://t.me/DRKMUQEETT).
- **Magisk action:** toggles balanced/performance and displays the selected profile.
- **Boot safety:** saves the selected profile in `/data/adb/drkmuqeet/profile`; all writes are guarded.

## Thermal guard

A pinned `performance` governor is exactly the wrong thing to hold through a long gaming session on a Helio G90T. The kernel's thermal engine will eventually throttle, but by then the device has already spent time fighting it. The guard backs off first, in two stages, with hysteresis so the governor cannot flap between states:

| stage | trigger | action |
| --- | --- | --- |
| `soft` | hottest zone ≥ 43 °C | governor → `schedutil` |
| `hard` | hottest zone ≥ 47 °C | governor → `schedutil`, read-ahead → 128 KiB |
| release | hottest zone < 40 °C | performance handed back in full |

What the guard deliberately does **not** do:

- It never writes to a thermal cooling device, a trip point, or anything under `/sys/class/thermal` that could weaken the kernel's own protection. It reads temperatures and adjusts **this module's** tuning. Nothing else.
- It never rewrites your chosen profile. `$STATE/profile` stays `performance`; the temporary override is recorded separately in `$STATE/guard`, and the audit reads that override as the real expectation while it is in force.
- It does nothing at all unless the profile is `performance`. On `balanced` or `default` it stays idle.
- It respects an explicit `drk unguard`. If you stop it, the clocks stay where you put them, however hot the device gets.

Every intervention is journalled to `/data/adb/drkmuqeet/guard.log`, readable through `drk guard-log` or the dashboard. The journal rotates at 64 KiB.

Thresholds are overridable for testing via `DRK_GUARD_SOFT`, `DRK_GUARD_HARD`, `DRK_GUARD_COOL` and `DRK_GUARD_INTERVAL`. Nothing in the module sets them, so on a phone you get the defaults above.

## Why the audit exists

Every write in this module is guarded: a node that does not exist or is not writable is skipped rather than forced. That is correct behaviour on a device, but a silent skip is indistinguishable from success. `drk audit` closes that gap by re-reading the tree and comparing it against what the recorded profile is supposed to have left behind.

It also turns two README claims into things you can verify on your own kernel:

- **No governor is set unless the kernel advertises it** — each policy reports `offered: yes/no` alongside what it is actually running.
- **No policy is driven past its hardware ceiling** — `scaling_max_freq` is compared against `cpuinfo_max_freq`. A policy above its own ceiling reports `freq_limits: exceeded` and the whole audit reports `unsafe`. This catches any module that did it, not only this one.

| status | meaning |
| --- | --- |
| `clean` | every targeted node holds the expected value |
| `partial` | some node is unwritable, but nothing writable was missed |
| `degraded` | a writable node refused the value, or drifted back after being set |
| `unsafe` | a policy is capped above its own hardware ceiling |
| `blocked` | no tunable nodes were found at all |

The audit is read-only. It never writes to a kernel node.

## Installation

1. Download `DRKMUQEET-v1.2.0.zip` from this repository’s build output.
2. Flash it in Magisk and reboot.
3. Tap the module’s **Action** button to toggle a profile, or open the local dashboard in a browser on the phone.

`service.sh` waits for `sys.boot_completed` (capped at 120 s) before starting the dashboard and the guard, rather than sleeping a fixed 15 s that fires too early on a slow boot and wastes time on a fast one.

The service binds to `127.0.0.1`, not the LAN. A browser must run on the phone to reach it. If port 8080 is occupied, stop the conflicting local service before rebooting.

## CLI

As root:

```sh
drk performance    # governor (if advertised) + 1024 KiB read-ahead + 0.5x animations
drk balanced       # schedutil (if advertised) + 512 KiB read-ahead + 0.5x animations
drk reset          # schedutil + 128 KiB + 1x animations, guard stopped; records `default`
drk toggle         # performance <-> balanced
drk stats          # one-line JSON for the dashboard, including guard level
drk guard          # start the thermal guard loop
drk unguard        # stop it and hand the clocks back to the selected profile
drk guard-status   # JSON: running, level, hottest zone, thresholds
drk guard-log [n]  # last n journal lines as JSON (default 25)
drk audit          # human-readable applied-state report; also writes audit.json
drk audit --json   # the same report as JSON on stdout
```

### Profile states

Three states are recorded in `/data/adb/drkmuqeet/profile`:

- `performance`
- `balanced`
- `default` — written by `drk reset`, meaning “stock values, nothing pinned”

If the file does not exist, the module treats the state as `default` and `drk boot` leaves the kernel exactly as it came up. Nothing is applied on a fresh install until you choose a profile.

## Development harness

`devtools/preview.py` runs the real `drk` script off-device against a Helio G90T-shaped stub of `/sys`, with `settings` and `getprop` shimmed, and serves `webroot/` under the same CGI contract as `system/bin/drk-web`. The thermal guard really runs against the stub.

```sh
python3 devtools/preview.py   # serves on 0.0.0.0:8080
```

Harness-only controls, none of which exist on a device:

```sh
curl 'localhost:8080/dev/heat?c=48'        # drive every thermal zone
curl 'localhost:8080/dev/overclock?on=1'   # push scaling_max_freq past cpuinfo_max_freq
curl 'localhost:8080/dev/state'            # dump the whole stub tree
```

A full cycle to watch: apply performance, `/dev/heat?c=44`, wait two seconds, and the guard tile on the dashboard flips to `soft` while the governor drops to `schedutil`. Heat to 48 for `hard`. Cool to 39 and performance is handed back. `action=guard-log` narrates the whole thing.

The script honours two QA hooks, both of which fall back to the real paths on a phone:

- `DRK_STATE` — where profile state, guard state and audit reports are written
- `DRK_SYSROOT` — where kernel nodes are read from and written to. A value that does not contain `devices/system/cpu` is ignored, so the module can never no-op itself on a real device.

Neither variable is set by `post-fs-data.sh`, `service.sh`, or `action.sh`.

## Compatibility and scope

This package is a shell-based Magisk module and does not bundle a Zygisk native injector, an APK, or a KernelSU-specific privileged UI bridge. Claiming these work without compiling, signing, testing on the target ROM, and obtaining the required user permissions would be misleading. It is suitable for modern Magisk-compatible environments where `busybox httpd` is available. Kernel governor availability varies by ROM/kernel — `drk audit` reports what your specific kernel actually accepted.

## Uninstall

Remove it in Magisk and reboot. `uninstall.sh` stops the thermal guard, requests a reset so the kernel is left at stock values, kills the dashboard, and then removes only `/data/adb/drkmuqeet`.

## Build

Run `./build.sh` from the repository root. It reads `version=` from `module.prop`, so the ZIP name cannot drift from the module version. The generated ZIP is ignored by Git.
