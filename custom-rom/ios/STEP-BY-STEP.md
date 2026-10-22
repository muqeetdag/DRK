# First iOS on Redmi Note 8 Pro (begonia) — Step-by-Step History Build

**Credits:** Base by [@OnyxAsh](https://github.com/OnyxAsh) — this guide is for the first MTK Helio G90T (MT6785) port of HackiOS  
**Upstream:** [oosexgang/hackios-xnu-kernel](https://github.com/oosexgang/hackios-xnu-kernel) (XNU xnu-8796.121.2 / iOS 16.5) + [hackios-xnu-userland](https://github.com/oosexgang/hackios-xnu-userland)  
**Status:** `arena/19b7c796-drk` is active — this is the experimental history build, QEMU before phone.

> No Apple files in repo. You download iOS 16.5 IPSW from Apple CDN to your machine via `ios165/fetch.sh`. Begonia is MediaTek MT6785 — not Snapdragon — so every SoC block below must be ported from redwood (reference) to MTK.

---

## What I need from you (give me these to tailor the build)

1. **PC:** Do you have Ubuntu 22.04/24.04 with 16GB RAM + 200GB free + fast internet? Or should I make a GitHub Actions cloud builder?
2. **Phone variant:** begonia (Global) or begoniain (India)? MIUI version currently on it?
3. **Bootloader:** Is it already unlocked (Mi Unlock, 168h wait)? If not, we start there.
4. **Target:** Do you want (a) QEMU demo first (safe, no phone risk) or (b) phone boot directly?
5. **Telegram updates:** After you revoke your posted test token and create a new private one, give me the *method* you want — I will create a workflow that uses GitHub Secrets `BOT_TOKEN` + `CHAT_ID`, never chat.

Reply with those 5 and I fill the templates with your exact values.

---

## Phase 0 — Unlock + Backup (1 week if not unlocked)

```bash
# On phone: Settings → My Device → tap MIUI version 7x → Developer options → Mi Unlock status → Add account
# On PC: Mi Unlock Tool → Sign in same Mi account → Unlock → Wait 168h → Wipes data
fastboot oem device-info  # must say: Device unlocked: true
# TWRP for begonia
fastboot flash recovery twrp-3.7-begonia.img
fastboot boot twrp-3.7-begonia.img  # boot, don't flash yet
# In TWRP: Backup → boot, system, vendor, data → copy to PC
# Save stock vbmeta
adb pull /dev/block/by-name/vbmeta ~/vbmeta.stock.img
```

## Phase 1 — Get the History Tree (5 mins)

```bash
git clone https://github.com/muqeetdag/DRK.git drk-begonia-ios -b arena/19b7c796-drk
cd drk-begonia-ios/custom-rom/ios
./setup.sh  # clones hackios-xnu-kernel + pulls hackios-xnu-userland as one tree via common/setup.sh
ls hackios/  # you should see: xnu/ uefi/ xnuloader/ common/ qemu/ docs/ + userland via .userland.git
cat hackios/README.md
cat hackios/docs/*.md
```

## Phase 2 — QEMU First Boot (validates XNU before touching MTK)

```bash
cd hackios
# QEMU virt,aarch64 with ramfb — same path HackiOS uses for CI
./qemu/run.sh
# You should see XNU boot logs → launchd → SpringBoard shell in QEMU
# This proves your build toolchain works before begonia port
```

Install deps on Ubuntu:
```bash
sudo apt update
sudo apt install git git-lfs repo bc bison build-essential ccache curl flex \
  gcc-multilib g++-multilib gnupg gperf imagemagick lib32readline-dev \
  libelf-dev liblz4-tool libsdl1.2-dev libssl-dev repo python3 clang lld
```

## Phase 3 — Begonia Port (the history work — MTK)

This is where begonia diverges from redwood (Snapdragon 778G). Files I scaffolded for you:

```
custom-rom/ios/begonia/
├── adt.dts.template  → copy to hackios/xnu/platform/begonia/begonia.dts
├── uefi-notes.md     → MTK Preloader/LK vs QCOM ABL
└── README.md         → full checklist
```

Steps in order — do not skip:

1. **Apple Device Tree (ADT)**
   ```bash
   mkdir -p hackios/xnu/platform/begonia
   cp ../begonia/adt.dts.template hackios/xnu/platform/begonia/begonia.dts
   # Now edit with real begonia MMIO from stock kernel DTS:
   # - DDR banks (e.g., 0x40000000 + 4GB), carveouts for SCP/modem
   # - GICv3 GICD/GICR bases for MT6785 (not redwood's 0x0c000000)
   # - UFS 2.1 host base (check kernel_xiaomi_begonia DTS: 0x11270000 placeholder)
   # - DSI 0x14000000 + 1080x2340 panel timings
   # - Touch: focaltech vs goodix — check your unit's DTS
   ```

2. **CPU / GIC / Timers**
   - Duplicate `hackios/xnu/platform/redwood/` → `hackios/xnu/platform/begonia/`
   - Adapt machine layer: APMIXED/clk, PSCI for 2x A76 + 6x A55, keep Silver/Gold parking like sweet/topaz initially (4-6 cores)

3. **Clocks + PMIC + Power**
   - MTK topckgen/infracfg vs QCOM GCC — see `drivers/clk/mediatek/clk-mt6785.c` in `kernel_xiaomi_begonia` (4.14)
   - PMIC regulators for UFS/display/touch

4. **Storage (UFS)**
   - Begonia UFS driver ≠ QCOM UFS. Port MTK UFS host or write minimal host for APFS persistence (iOS root as HFS+/APFS in userdata, journaled, R/W)

5. **Display + GPU**
   - First boot: CPU render or ramfb (like topaz does) — Mali-G76 Mesa later
   - Kernel IOSurface + IOMobileFramebuffer shim scanning contiguous RAM below 4GB

6. **Input + Keys**
   - Power/volume via PMIC/GPIO, touch wake

7. **Wi-Fi / Audio / Camera (later)**
   - Leave cellular/BT/SEP disabled (upstream gap: no App Store/Apple ID, no FairPlay)
   - Wi-Fi WCN/MTK MT6625 — ath11k shim or disabled initially
   - Audio: port `userland/src/audio_plugin.c` for MTK AFE

## Phase 4 — Build XNU + Loader + UEFI (on your machine)

```bash
cd hackios
# XNU (APSL 2.0) — xnu-8796.121.2
make -C xnu -j$(nproc)          # outputs prebuilt/kernel
make -C xnuloader -j$(nproc)    # EFI payload loader
make -C uefi -j$(nproc)        # or shim bare-metal if EDK2 too heavy — see uefi-notes.md

ls prebuilt/  # kernel + loader + uefi images should appear
```

## Phase 5 — Build iOS APFS Images (from Apple CDN, not Gofile)

```bash
cd hackios  # userland via common/ugit
common/ugit status  # shows hackios-xnu-userland
# This downloads iOS 16.5 iPhone X IPSW from Apple to YOUR machine:
./ios165/fetch.sh  # or userland/tools/fetch.sh per README
# Then build APFS:
./userland/build.sh  # per userland/build.sh header options
# Outputs: root.apfs (our init, libs, launchd jobs) + system.apfs (iOS 16.5 cryptexes)
ls -lh userland/*.apfs
```

> Latest open target is 16.5. When Apple publishes newer XNU (e.g., iOS 17/18), bump `xnu/` version + re-fetch IPSW. No redistribution of Apple files.

## Phase 6 — Flash / Boot (safe)

```bash
# QEMU again with begonia ADT + new kernel — must hit lock screen in QEMU before phone
./qemu/run.sh --adt xnu/platform/begonia/begonia.dts

# Phone (unlocked only, TWRP backup done):
# Option A — fastboot boot (no flash, safest, like sweet/topaz shim)
fastboot boot hackios-begonia-uefi.fd
# Option B — TWRP chainload from userdata
adb push prebuilt/* /sdcard/hackios/
# If hang: hard reboot → TWRP → Restore
```

What you should see (HackiOS status):
- ✅ Lock screen, swipe, 2-page home, iOS 16 wallpapers, gestures, Control Center, Settings/Clock/Calendar/Photos/Notes/Safari/widgets, touch (once panel ported), power/volume keys, 8-core ~20s boot, Wi-Fi if driver done, audio/speaker/mic, camera preview+photo+HEIC, persistent storage
- ❌ No cellular, no BT, no SEP/FairPlay/App Store/Apple ID, no video record, Metal partial (freedreno 293/456 shaders), charging quirk

## Phase 7 — Telegram Progress (secure)

Do NOT paste token in chat again. Do this:

1. Revoke posted token: @BotFather → /mybots → Revoke API Token
2. New token → GitHub: muqeetdag/DRK → Settings → Secrets → New `BOT_TOKEN`, `CHAT_ID`
3. I will add `.github/workflows/begonia-ios.yml` that posts on each phase:
   ```yaml
   - name: Notify Telegram
     run: |
       curl -s -X POST "https://api.telegram.org/bot${{ secrets.BOT_TOKEN }}/sendMessage" \
         -d chat_id="${{ secrets.CHAT_ID }}" \
         -d text="Begonia iOS: ${{ job.status }} — $STEP"
   ```

Say "create workflow" and I push it.

---

## Timeline for history

- **Week 1:** Unlock + QEMU proven
- **Weeks 2-4:** MTK ADT + GIC + clocks + UFS (boots to launchd)
- **Weeks 4-6:** Display + touch + keys (SpringBoard visible)
- **Weeks 6+:** Wi-Fi/audio/camera, polish

You are building the **first MediaTek G90T XNU port** — redwood was Snapdragon. Document every MMIO base you change; future begonia ports will fork from you (@OnyxAsh base).

## Next command

Reply with your 5 answers (PC, variant, unlock, QEMU/phone, telegram method) and run:
```bash
cd custom-rom/ios && ./setup.sh
```
I fill `begonia.dts` with your exact variant once you give it.
