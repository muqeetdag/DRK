# Redmi Note 8 Pro (begonia) — Bring-up Notes

## Hardware
- SoC: MT6785 (Helio G90T) — MediaTek, not Qualcomm — needs MTK-specific blobs
- CPU: 2x Cortex-A76 + 6x Cortex-A55, Mali-G76 MC4
- Storage: UFS 2.1, Display: 6.53" 1080x2340, Camera: 64MP GW1, Fingerprint side-mounted
- Codename: `begonia` / `begoniain` (India)

## Common pitfalls
1. **Vendor blobs**: MediaTek HALs are closed. You MUST extract `vendor/` from MIUI fastboot zip or from device via `adb root + extract-files.sh`. Missing blobs = broken camera, IMS, wifi.
2. **Kernel**: Use 4.14.x base from Xiaomi. Don't try to rebase to 5.x — MTK drivers won't compile.
3. **SELinux**: Start permissive, then audit2allow. Enforcing too early hides real bugs as denials.
4. **AVB / vbmeta**: Begonia uses Android Verified Boot. For custom ROM, ship `vbmeta.img` with `--disable-verity --disable-verification` or flash vbmeta with those flags via fastboot.
5. **Fingerprint**: Side sensor needs correct `fpc`/`goodix` HAL + power management fix — often breaks on A14+ ports.
6. **OTA**: Don't enable OTA until you have your own signing keys.

## Recommended sources (check freshness)
- Device: https://github.com/LineageOS/android_device_xiaomi_begonia (or begonia-dev forks)
- Kernel: https://github.com/begonia-dev/kernel_xiaomi_begonia (4.14)
- Vendor: generated via extract, or mirror at https://github.com/begonia-dev/vendor_xiaomi_begonia
- Always pin commits in manifests/begonia.xml — don't use floating branches.

## Unlock & Flash flow (user-facing)
1. Mi Unlock Tool → unlock (wait 168h) → wipes data
2. `fastboot flash recovery twrp-begonia.img` → boot TWRP → format data
3. Flash ROM zip + GApps (NikGapps/MindTheGapps) + Magisk (optional) → reboot
4. Install DRKMUQEET Magisk module for tuning console

## HackiOS vs begonia note
HackiOS runs XNU on Snapdragon 778G (redwood) with custom UEFI. Porting XNU to MT6785 would require rewriting MTK boot chain, GIC/timer, UFS, display, and MediaTek SCP — entirely different SoC family. Treat as separate research, not a drop-in.

