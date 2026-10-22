# Begonia (MT6785) — HackiOS Port Checklist

**Device:** Redmi Note 8 Pro — codename `begonia` / `begoniain`  
**SoC:** MediaTek MT6785 (Helio G90T): 2x Cortex-A76 + 6x Cortex-A55, Mali-G76 MC4, MediaTek PMIC/SCP

> Redwood (HackiOS reference) is Qualcomm SM7325/SM7350 (Snapdragon 778G) with standard ARM GICv3 + Qualcomm clocks. Begonia is MediaTek — different clocks, power domains, interconnect, UFS, display pipeline, and touch. Port = real kernel work.

## What @OnyxAsh provides here
- `adt.dts.template` — Apple Device Tree for begonia (memory map, MMIO, interrupt parenting)
- `uefi-notes.md` — MTK boot chain vs redwood UEFI

## Bring-up order (do in QEMU before phone)

1. **Memory map + ADT**
   - Fill `adt.dts.template` with actual begonia DDR banks, carveouts, and MMIO bases from stock kernel DTS + datasheet
   - Validate with `qemu/virt` + ramfb first, then phone

2. **CPU / GIC / Timers**
   - GICv3 base, redistributors, SPI routing; ARM generic timer vs MTK timer
   - Park or bring up 6x A55 + 2x A76 (like topaz/sweet keep subset active initially)
   - PSCI for secondary cores

3. **Clocks / Power / PMIC**
   - MTK topckgen, infracfg, APMIXED clocks — redwood clocks ≠ MTK
   - Regulator bring-up for UFS, display, touch

4. **Storage (UFS)**
   - Begonia uses UFS 2.1 host (different from QCOM UFS). Port or write UFS host for MT6785
   - iOS root lives as HFS+/APFS volume in `userdata` — must be R/W, journaled

5. **Display + GPU**
   - Display: DSI pipeline for 1080x2340 panel (vs redwood panel) — kernel IOSurface + IOMobileFramebuffer shim
   - GPU: Mali-G76 via Mesa/freedreno equivalent or soft render for first boot (topaz does CPU render initially)

6. **Input: Touch + Keys**
   - Goodix GT9916 on HackiOS redwood; begonia uses different touch (check stock DTS: focaltech/goodix variant)
   - Power/volume keys via PMIC/GPIO

7. **Wi-Fi / Audio / Camera**
   - Wi-Fi: MT6625L / WCN combo on begonia — separate from redwood WCN6750 ath11k. Needs MTK Wi-Fi driver shim or leave disabled initially
   - Audio: MTK AFE + speaker/mic — port CoreAudio plugin (`userland/src/audio_plugin.c` pattern)
   - Camera: leave disabled initially — Metal/Mesa camera path is later work

8. **XNU platform code**
   - Duplicate `xnu/platform/redwood/` → `xnu/platform/begonia/` and adapt machine layer
   - Apply patch series like `sweet/topaz` do (see `xnu/patches/` ordering in HackiOS)

## Testing
- **QEMU first:** `./qemu/run.sh` — validate XNU boots to `launchd` with ramfb
- **Phone second:** unlocked bootloader → fastboot boot UEFI image (don't flash boot partition); keep TWRP + Android fallback
- **Storage:** iOS writes must persist across reboots (APFS on userdata) — verify journaling

## Known gaps (inherited from upstream)
- No cellular, no Bluetooth, no Secure Enclave / FairPlay → no App Store / Apple ID login, no Apple Pay
- Video / Live Photo record incomplete
- Metal acceleration partial — OpenGL ES via Mesa bridge first

## References
- HackiOS docs: `hackios/docs/` after `./setup.sh`
- Begonia stock DTS: extract from MIUI kernel or `kernel_xiaomi_begonia` (4.14) for MMIO bases
- MT6785 TRM / clocks: MediaTek docs + downstream kernel `drivers/clk/mediatek/`
