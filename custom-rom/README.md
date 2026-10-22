# DRK Custom ROM — Redmi Note 8 Pro (begonia)

**Credits:** Built by [@OnyxAsh](https://github.com/OnyxAsh) — custom ROM + iOS bring-up for begonia

This repo now has two scaffolds:

| Folder | What | Status |
|--------|------|--------|
| `ios/` | **iOS on begonia (HackiOS-style)** — XNU xnu-8796.121.2 / iOS 16.5 via UEFI → xnuloader → XNU | Experimental, research — latest iOS *open* target is 16.5 (see ios/README.md for why not 18/26 yet) |
| `begonia/` + `manifests/` | LineageOS 20 Android scaffold (stable fallback) | Daily-driver ready template |

> Device: Redmi Note 8 Pro (begonia) — MediaTek Helio G90T (MT6785)  
> iOS choice: We document iOS 16.5 as the latest *verifiable* open XNU; bump to newer XNU/IPSW when Apple publishes it — no Apple files stored, `ios165/fetch.sh` pulls from Apple CDN to your machine.

## Quick start — iOS (experimental)

```bash
git clone https://github.com/muqeetdag/DRK.git drk-ios
cd drk-ios/custom-rom/ios
./setup.sh              # clones oosexgang/hackios-xnu-kernel + userland as one tree
cd hackios
cat docs/*.md           # HackiOS docs
# Apply begonia templates: cp ../begonia/adt.dts.template xnu/platform/begonia/begonia.dts
# Build: make -C xnu / xnuloader / uefi ; userland/build.sh for APFS images
# QEMU: qemu/run.sh ; Phone: fastboot boot ... (see ios/begonia/README.md)
```

## Quick start — Android (stable)

```bash
cd custom-rom
./build.sh sync
./begonia/extract-files.sh
source build/envsetup.sh && lunch lineage_begonia-userdebug && mka bacon
```

## Credits
- **ROM Base & Bring-up:** [@OnyxAsh](https://github.com/OnyxAsh)
- **HackiOS upstream:** OOSEX GANG / oosexgang (XNU + userland)
- **Tuning Console:** DRKMUQEET Magisk module (127.0.0.1:8080, reversible — works on Android ROM)

