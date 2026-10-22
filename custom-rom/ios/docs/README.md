# iOS Scaffold Docs — Begonia

**Credits: @OnyxAsh**

## Flash / Rollback (do not skip)
1. Backup: TWRP → Backup (boot, system, vendor, data)
2. Export vbmeta: `dd if=/dev/block/by-name/vbmeta of=/sdcard/vbmeta.stock.img`
3. Unlock already done? `fastboot oem device-info` must show `Device unlocked: true`
4. Test QEMU first: `hackios/qemu/run.sh` (ramfb) — XNU should reach `launchd` before phone test
5. Phone test: `fastboot boot hackios_begonia_uefi.fd` (or shim approach) — if hang, hard reboot to TWRP, restore

## What you will see (HackiOS status, per docs)
- Lock screen, swipe, 2-page home, iOS 16 wallpapers
- Settings, Clock, Calendar, Photos, Notes, Safari, Weather/Calendar widgets
- Touch (once panel + touch ported), power/volume keys, 8-core bring-up, ~20s to lock screen, Wi-Fi if WCN/MTK driver done, audio + camera preview (photo), persistent APFS on UFS

## Not yet (upstream gaps)
- Cellular, Bluetooth, SEP/FairPlay (no App Store/Apple ID), Metal accel partial (293/456 shaders on Adreno), video record, charging-only-discharge quirk on custom kernel

## IPSW extraction (no Apple files in repo)
```bash
cd hackios/userland  # via ugit mount
cat ios165/fetch.sh  # shows Apple CDN URL for iOS 16.5 iPhone X
./fetch.sh           # downloads to your machine, builds root.apfs + system.apfs per userland/build.sh header
```

See upstream docs after `./setup.sh`: `hackios/docs/` + `hackios/README.md`
