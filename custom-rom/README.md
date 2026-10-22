# DRK Custom ROM — Redmi Note 8 Pro (begonia) Scaffold

This is a **source-build scaffold** for a begonia custom ROM, created inside the DRK repo.
It does NOT replace DRKMUQEET (the Magisk tuning console) — it lives alongside it.

> Device: Redmi Note 8 Pro (begonia) — MediaTek Helio G90T (MT6785)
> Target: Android 13/14 based (LineageOS 20 style) — easiest to adapt to 14/15 later

## What you get here
- `begonia/` — placeholder device tree + vendor extraction helper
- `manifests/` — repo manifest to sync AOSP + device/kernel/vendor
- `build.sh` — one-command sync helper
- `device-begonia.md` — bring-up notes for MTK specifics (blobs, camera, fingerprint, SELinux)

## Not included (you must add)
- Real vendor blobs — extract from stock MIUI with `extract-files.sh` (see device-begonia.md)
- Full kernel source — clone `kernel_xiaomi_begonia` separately
- 200GB build env — do the final compile on a proper Ubuntu build machine, not in this tiny sandbox

## Quick start on a build machine (Ubuntu 22.04+)

```bash
# 1. Install deps
sudo apt update
sudo apt install git git-lfs repo bc bison build-essential ccache \
  curl flex g++-multilib gcc-multilib gnupg gperf imagemagick \
  lib32readline-dev lib32z1-dev libelf-dev liblz4-tool libncurses5-dev \
  libsdl1.2-dev libssl-dev libxml2 libxml2-utils lzop pngcrush \
  rsync schedtool squashfs-tools xsltproc zip zlib1g-dev python3 openjdk-11-jdk

# 2. Get scaffold
git clone https://github.com/muqeetdag/DRK.git drk-rom
cd drk-rom/custom-rom

# 3. Sync sources (100GB+)
./build.sh sync

# 4. Extract blobs from your phone or MIUI zip
./begonia/extract-files.sh

# 5. Build
source build/envsetup.sh
lunch lineage_begonia-userdebug
mka bacon -j$(nproc --all)
```

Result: `out/target/product/begonia/lineage-*.zip` — flash via TWRP.

## DRKMUQEET integration
DRKMUQEET module will work on top of this ROM as a Magisk add-on for reversible tuning (balanced/performance via 127.0.0.1:8080). No kernel overclock, no thermal bypass.

## HackiOS relation
HackiOS (XNU on POCO X5 Pro) is a separate research project in `oosexgang/hackios-xnu-*`.
This scaffold is traditional Android AOSP — not XNU — but documents where HackiOS-style bring-up (UEFI, loader) differs from MTK Android bring-up.

## Next steps
- Tell me which base you want: LineageOS 20 (A13), LineageOS 21 (A14), crDroid, or PixelExperience
- I'll fill `begonia/device.mk` + `manifests/begonia.xml` for that base
