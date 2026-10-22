# DRK • HackiOS for Begonia — iOS on Redmi Note 8 Pro (Experimental)

**Credits:** Base by **[@OnyxAsh](https://github.com/OnyxAsh)** — iOS bring-up scaffold for begonia  
**Upstream:** [oosexgang/hackios-xnu-kernel](https://github.com/oosexgang/hackios-xnu-kernel) + [oosexgang/hackios-xnu-userland](https://github.com/oosexgang/hackios-xnu-userland) (XNU xnu-8796.121.2 / iOS 16.5)

> This is an **experimental research scaffold**, not a flashable zip. It reuses the HackiOS architecture (UEFI → xnuloader → patched XNU → unmodified iOS 16.5 userland extracted from Apple's IPSW) and adapts it for `begonia` (MT6785 / Helio G90T). Begonia is MediaTek, not Snapdragon 778G (redwood), so full port = rewriting SoC bring-up.

## What this achieves vs LineageOS
- **Lineage scaffold** (`../`) = stable Android ROM, daily-driver ready
- **iOS scaffold** (`./`) = HackiOS-style boot, iOS UI, for research / demo — camera/cellular/Bluetooth incomplete, charging quirks expected (see HackiOS status)

## Why iOS 16.5 and not iOS 18/26 latest?
Apple publishes XNU source with delay and incomplete drivers. The latest *open* XNU that boots with unmodified iOS userland is **xnu-8796.121.2 (iOS 16.5)**, which HackiOS uses. iOS 17/18/26 userland needs newer XNU + new SEP/FairPlay — not publicly buildable. We track `iOS 16.5` as the latest *verifiable* open target; when Apple drops newer XNU, bump `xnu/` and re-extract IPSW via `ios165/fetch.sh`.

> No Apple files are in this repo. `ios165/fetch.sh` downloads the IPSW from Apple's CDN to *your* machine and extracts it locally.

## Layout (mirrors HackiOS)
```
custom-rom/ios/
├── README.md              # this file
├── setup.sh               # clones hackios-xnu-kernel + hackios-xnu-userland as one tree
├── begonia/               # begonia-specific bring-up (MT6785)
│   ├── README.md          # porting checklist (clocks, GIC, timer, UFS, display, touch)
│   ├── adt.dts.template   # Apple Device Tree template for begonia (memory, MMIO)
│   └── uefi-notes.md      # UEFI port notes for MT6785 (vs redwood's UEFI)
└── docs/                  # status + flashing notes (from HackiOS docs)
```

## Quick start (build machine, Ubuntu 22.04+)

```bash
# 1. Get DRK scaffold
git clone https://github.com/muqeetdag/DRK.git drk-ios
cd drk-ios/custom-rom/ios

# 2. Clone HackiOS as one tree (kernel pulls userland via common/setup.sh)
./setup.sh
cd hackios

# 3. Inspect redwood (working) to understand what begonia needs
ls -R xnu/ uefi/ xnuloader/ common/ qemu/   # redwood/QEMU bring-up
cat docs/*.md                                 # HackiOS docs

# 4. Apply begonia templates (WIP — you complete the SoC port)
cp ../begonia/adt.dts.template xnu/platform/begonia/begonia.dts
# Edit clocks, GICv3, timer, UFS base, display, GTxxx touch, PMIC

# 5. Build kernel + loader + UEFI (see HackiOS xnu/README)
make -C xnu -j$(nproc)
make -C xnuloader -j$(nproc)
make -C uefi -j$(nproc)

# 6. Build iOS APFS images (on your machine, pulls IPSW from Apple)
cd userland  # via ugit or .userland.git
./build.sh   # outputs root.apfs + system.apfs (see userland/build.sh header for options)

# 7. Boot via QEMU first (no phone risk)
../qemu/run.sh

# 8. Phone boot (unlocked bootloader only, know the risk)
# See begonia/README.md for flash + fallback to Android
```

## Begonia port status

| Area | Redwood (HackiOS official) | Begonia (this scaffold) |
|------|----------------------------|-------------------------|
| Boot | UEFI → xnuloader → XNU | Templates only — MTK boot chain to adapt |
| CPU | 8-core 2.4GHz, touch, display, Wi-Fi WCN6750 | Needs MT6785 bring-up (A76/A55, GIC, Mali-G76 via Mesa) |
| iOS UI | Lock screen, gestures, Settings, Safari, photo | Same userland once kernel boots |
| Not working (upstream) | Cellular, BT, SEP/FairPlay, video record | Same + MTK-specific drivers |
| Storage | HFS+/APFS on UFS, persistent | Same layout once UFS driver ported |

## Safety
- Do NOT flash without TWRP backup + known rollback to MIUI/Lineage.
- QEMU `virt,aarch64` before phone.
- No thermal bypass, no overclock — respect HackiOS + DRKMUQEET safety stance.

## Credits
- **@OnyxAsh** — iOS begonia port base & templates
- **OOSEX GANG / oosexgang** — HackiOS upstream (kernel + userland)
- **Apple** — XNU (APSL 2.0) + iOS IPSW (extracted locally, not redistributed)
