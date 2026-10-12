# DRKMUQEET — Device Tuning Console

A small, open Magisk module for **Redmi Note 8 Pro (begonia)**. It provides a local, mobile-friendly dashboard, three **reversible** tuning profiles, and a Wi-Fi control panel for the phone's own radio.

> This is intentionally not a “god module.” No legitimate module can reliably promise every kernel tweak on every ROM. DRKMUQEET checks whether a kernel node exists and is writable before changing it. It does **not** contain root hiding/integrity spoofing, DRM/banking circumvention, game cheats, premium-feature bypasses, thermal-safety removal, forced hardware overclocks, or fake status toggles.

## Included functionality

- **Performance profile:** switches to `performance` only when that governor is advertised by the installed kernel; sets block read-ahead to 1024 KiB; sets Android animation scales to 0.5x.
- **Balanced profile:** 512 KiB read-ahead and 0.5x UI animations.
- **Restore defaults:** uses `schedutil` when the kernel advertises it, returns read-ahead to 128 KiB, and restores 1x animations.
- **Wi-Fi control:** radio on/off/toggle, live connection readout (SSID, IP, gateway, DNS, band, link speed, RSSI, quality, security type, MAC), a nearby-network scan list, and saved-network review/forget.
- **Dashboard:** local-only (`http://127.0.0.1:8080`) status, profile controls, Wi-Fi panel, and developer contact: [@DRKMUQEETT](https://t.me/DRKMUQEETT).
- **Magisk action:** toggles balanced/performance and displays the selected profile.
- **Boot safety:** saves the selected profile in `/data/adb/drkmuqeet/profile`; all writes are guarded.

## Installation

1. Download `DRKMUQEET-v1.1.0.zip` from this repository’s build output.
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

drk wifi status          # JSON: radio state, SSID, IP/gateway/DNS, band, RSSI, link speed, security
drk wifi on | off        # radio on/off, then re-reads status
drk wifi toggle
drk wifi scan            # strongest AP per SSID, twelve rows, JSON
drk wifi saved           # configured network ids on this device
drk wifi forget <id>     # forget a saved network by id from `wifi saved`
```

`drk wifi status` reads the live `mWifiInfo` record from `dumpsys wifi` and falls back to `/sys/class/net/<iface>/address` and `ip route` where a field is missing. Any field the ROM does not publish comes back as `null`/`unknown` rather than a guess.

## Wi-Fi panel scope

The panel reads and switches **this phone’s own radio**, exactly like the Settings app does:

- radio state, current connection, and signal metrics for the network the phone is joined to;
- a scan list of visible access points (SSID, BSSID, band, RSSI, security label);
- the phone’s own saved networks, with a forget button.

It does **not** capture, inject, deauthenticate, jam, or crack anything, and it cannot act on another device’s network. Everything stays on `127.0.0.1`, so no other machine can reach the control surface.

## Compatibility and scope

This package is a shell-based Magisk module and does not bundle a Zygisk native injector, an APK, or a KernelSU-specific privileged UI bridge. Claiming these work without compiling, signing, testing on the target ROM, and obtaining the required user permissions would be misleading. It is suitable for modern Magisk-compatible environments where `busybox httpd` is available. Kernel governor availability varies by ROM/kernel.

Wi-Fi reads depend on ROM build variants: `cmd wifi status`/`list-scan-results`/`list-networks` exist on Android 11+ and `dumpsys wifi` output differs across versions. The panel degrades to `null` fields or an explanatory message instead of failing.

## Uninstall

Remove it in Magisk and reboot. `uninstall.sh` first requests a reset and then removes only `/data/adb/drkmuqeet`.

## Build

Run `./build.sh` from the repository root. The zip name follows `version=` in `module.prop`, and the generated ZIP is ignored by Git.
