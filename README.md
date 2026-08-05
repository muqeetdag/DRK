# DRKMUQEET 💀👻🗿

**Ultimate system optimization & privacy toolkit for Redmi Note 8 Pro (begonia) — as a Magisk module.**

DRKMUQEET is a from-scratch, shell-only Magisk module that ships a verified control center
(`drk`) with **~250 genuinely working tools**: CPU/GPU/memory/storage/network tuning,
privacy killswitches, per-app permission control, battery care, logging/debugging,
developer and accessibility settings.

**The rule that makes it different:** every single command writes a value and **reads it
back to verify**. Nothing is claimed to work without proof. Run `drk doctor` and you get an
honest ✓/✗/⚠ report of exactly which interfaces exist on *your* kernel — never a fake "done".

---

## ⚖️ Scope & honest limits (read this first)

This module contains **only legitimate system tools**. Three classes of items from the
original spec are intentionally absent, and per the module's own "no fake features" rule,
each is documented in `DRKMUQEET/docs/COMPATIBILITY.md`:

| Class | Items | Why excluded |
|---|---|---|
| 🚫 **Declined — illegal/facilitates wrongdoing** | IMEI spoofing | Altering a device's IMEI is illegal in most jurisdictions and is associated with fraud/stolen-device concealment. |
| 🚫 **Declined — cheating tools** | Aimbot, wallhack, ESP, chams, trigger bot, speed hack, teleport, fly hack, no clip, god mode, infinite ammo, no reload, rapid fire, no recoil, memory scanning/editing, auto farm/heal/shield | These exist to cheat in games against other players. "Game testing" does not make aimbots legitimate. |
| ❌ **Impossible — hardware absent** | ToF, heart-rate/SpO2/ECG/EEG/EMG/GSR/UV sensors, telephoto lens, OIS, wireless/Qi charging, notification LED, always-on display | The hardware does not exist on begonia. Shipping scripts for them would be fake features. |
| ❌ **Impossible — no interface** | Kernel voltage control, charge-limit (stock kernel), EQ/DSP effects, global camera-2 manual controls, DNSSEC/DoH/HTTP3/QUIC/WebRTC toggles, ... | No OS/kernel interface exists to control these; they are app-level or require custom kernels/HAL mods. |

**Feature count reality check:** the original spec lists ~470 lines, but after removing
exact duplicates (~40), declined items (~23), and impossible/hardware-absent items (~150),
the remainder is **~250 real tools — and that is exactly what this module ships, all
verified-able.** Any spec item that cannot exist is reported as such, never faked.

---

## 📦 Installation

1. Build the flashable zip (or use the prebuilt `DRKMUQEET.zip`):
   ```sh
   ./build.sh          # → DRKMUQEET.zip
   ```
2. Flash **DRKMUQEET.zip** in the Magisk app (or `magisk --install-module DRKMUQEET.zip`).
3. Reboot. The module applies `/data/adb/drk/drk.conf` at boot (`service.sh` → `drk --boot`,
   full audit log at `/data/adb/drk/logs/boot.log`) and starts the `drkd` monitor daemon.
4. Open a root shell and type:
   ```sh
   drk help        # everything
   drk doctor      # verify every interface on YOUR device
   ```

**Requirements:** Magisk 24+ (Zygisk/DenyList features need Zygisk enabled), root shell.
Works on begonia MIUI 11/12.5 (Android 10/11); most features are device-agnostic.

---

## 🧰 Quick tour

```sh
drk perf performance|balanced|battery|default     # presets
drk cpu governor schedutil                        # verified governor change
drk gpu freq max 800000000                        # GPU cap for thermals
drk mem zram 1536 lz4                             # 1.5 GB zram swap
drk mem swappiness 80                             # verified sysctl
drk fs trim                                       # fstrim now
drk net tcp bbr                                   # congestion control (honest: fails if kernel lacks bbr)
drk net vpnks 1                                   # VPN killswitch (iptables)
drk net fw block com.whatsapp                     # per-app network firewall
drk priv deny com.google.android.gms              # Magisk DenyList (root-hide)
drk priv killswitch cam on                        # deny CAMERA appop to every app
drk priv killswitch mic on                        # deny RECORD_AUDIO to every app
drk priv batch deny SMS                           # one-shot privacy sweep
drk priv report                                   # privacy audit report
drk batt info / health / eta / cycles             # battery diagnostics
drk hw temp all                                   # live thermal zones
drk game on                                       # gaming performance profile
drk log bugreport / crash / anr / trace gfx 10    # debugging
drk acc font 1.15 / invert 1 / dalton 1 2         # accessibility
drk dev adbnet 1 5555                             # wireless adb
drk config set SWAPPINESS 60 && drk config apply  # persistent settings
```

Every command prints `✓` (verified read-back), `✗` (interface missing on this
kernel/ROM — reported honestly), or `⚠` (accepted, effect OEM-dependent).

---

## 📁 Module layout

```
DRKMUQEET/
├── module.prop            module metadata
├── customize.sh           installer (seeds /data/adb/drk/drk.conf)
├── post-fs-data.sh        early memory tuning before zygote
├── service.sh             late boot: drk --boot + drkd
├── system.prop            safe boot-time props (dalvik heap, hwui, wifi scan)
├── sepolicy.rule          SELinux additions for sysfs writes
├── config/drk.conf        boot-time configuration (user-editable)
└── system/bin/
    ├── drk                control center (~250 tools, see drk help)
    ├── drkd               monitor daemon (battery CSV, temp alerts, weekly fstrim)
    └── drk.d/*.sh         per-category engines (lib, cpu, gpu, mem, fs, net, priv,
                           hw, sens, audio, cam, disp, batt, proc, log, dev, acc,
                           game, perf, boot, maint)
```

Runtime state lives under `/data/adb/drk/`:
`drk.conf` (config), `logs/` (boot.log, events.log, battery.csv, reports), `state/` (pid, spoof backup).

---

## 🔍 Documentation

- `DRKMUQEET/docs/COMPATIBILITY.md` — the full feature matrix: every requested feature, its
  status (✅ implemented / ⚠️ device-dependent / ❌ impossible / 🚫 declined) and why.
- `drk help` — complete command reference.

## 🧪 Tested

All scripts pass `sh -n`; the CLI was functionally exercised against a stubbed Android
environment plus **real** kernel interfaces where the sandbox allowed it (sysctls,
`drop_caches`, block-device `read_ahead_kb`, zswap `Y/N` boolean read-back, fstrim).

## ⚠️ Disclaimer

Root tooling can always be misused; use responsibly on devices you own. CPU core offlining,
zram sizing and iptables rules can affect stability — every action is reversible
(`drk reset`, `drk net vpnks 0`, `drk priv spoof 0`, reboot). This module is provided
as-is for personal, legitimate use on your own device.
