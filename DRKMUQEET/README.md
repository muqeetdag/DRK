# DRKMUQEET — Device Tuning Console

A small, open Magisk module for **Redmi Note 8 Pro (begonia)**. It provides a local, mobile-friendly dashboard and three **reversible** profiles.

> This is intentionally not a “god module.” No legitimate module can reliably promise every kernel tweak on every ROM. DRKMUQEET checks whether a kernel node exists and is writable before changing it. It does **not** contain root hiding/integrity spoofing, DRM/banking circumvention, game cheats, premium-feature bypasses, thermal-safety removal, forced hardware overclocks, or fake status toggles.

## Included functionality

- **Performance profile:** switches to `performance` only when that governor is advertised by the installed kernel; sets block read-ahead to 1024 KiB; sets Android animation scales to 0.5x.
- **Balanced profile:** 512 KiB read-ahead and 0.5x UI animations.
- **Restore defaults:** uses `schedutil` when the kernel advertises it, returns read-ahead to 128 KiB, and restores 1x animations.
- **Dashboard:** local-only (`http://127.0.0.1:8080`) status, profile controls, and developer contact: [@DRKMUQEETT](https://t.me/DRKMUQEETT).
- **Magisk action:** toggles balanced/performance and displays the selected profile.
- **Boot safety:** saves the selected profile in `/data/adb/drkmuqeet/profile`; all writes are guarded.

## Installation

1. Download `DRKMUQEET-v1.0.0.zip` from this repository’s build output.
2. Flash it in Magisk and reboot.
3. Tap the module’s **Action** button to toggle a profile, or open the local dashboard in a browser on the phone.

The service binds to `127.0.0.1`, not the LAN. A browser must run on the phone to reach it. If port 8080 is occupied, stop the conflicting local service before rebooting.

## CLI

As root:

```sh
drk performance
drk balanced
drk reset
drk stats
```

## Compatibility and scope

This package is a shell-based Magisk module and does not bundle a Zygisk native injector, an APK, or a KernelSU-specific privileged UI bridge. Claiming these work without compiling, signing, testing on the target ROM, and obtaining the required user permissions would be misleading. It is suitable for modern Magisk-compatible environments where `busybox httpd` is available. Kernel governor availability varies by ROM/kernel.

## Uninstall

Remove it in Magisk and reboot. `uninstall.sh` first requests a reset and then removes only `/data/adb/drkmuqeet`.

## Build

Run `./build.sh` from the repository root. The generated ZIP is ignored by Git.
