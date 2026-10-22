# UEFI Port — Begonia (MT6785) Notes

**Credits: @OnyxAsh**

HackiOS boot chain (redwood): `Android bootloader (ABL) → UEFI (EDK2 port) → USB RAM loader → XNU`
Begonia does NOT have Qualcomm ABL the same way — it has MediaTek LK/Preloader → little kernel → Android boot.

## What to adapt from `uefi/` (redwood)
- **EDK2 platform pkg** — redwood's `uefi/` is for SM7325. For MT6785, create `Platform/Mediatek/Begonia/` with:
  - MTK clock init (topckgen, infracfg) vs QCOM GCC
  - MTK PMIC regulator setup vs QCOM PMIC
  - DDR init already done by Preloader — don't re-init, just preserve
  - Display pre-init for LKFB → hand off to XNU

## Boot options
1. **Fastboot boot** (safest): `fastboot boot begonia_uefi.fd` — like sweet/topaz shim `fastboot boot shim.img`. Nothing flashed.
2. **TWRP chainload**: boot TWRP, then execute UEFI payload from `userdata`
3. No permanent flash until QEMU + fastboot boot proven

## xnuloader (EFI app)
- Stays largely same — loads XNU + ADT + boot-args from `userdata` partition
- Adapt `uefi/` → `xnuloader/` handoff for MTK memory map (preserve modem/SCP carveouts if needed)

## References after setup
- `uefi/README.md`, `xnuloader/README.md` in `hackios/` tree
- Sweet/topaz `shim/` for bare-metal boot pattern (alternative to full UEFI, if EDK2 too heavy)
