# DRKMUQEET — Feature Compatibility Matrix (begonia)

This is the **complete, honest accounting** of every feature in the original DRKMUQEET
specification. DRKMUQEET's core rule is *no fake features*: a feature is shipped only if it
can be applied **and verified by read-back** on a real Android kernel. Everything else is
marked below with its exact reason.

## Status legend

| Mark | Meaning |
|---|---|
| ✅ | Implemented — writes a real interface and verifies by reading it back |
| ⚠️ | Implemented — but device/kernel/ROM-dependent; it tries real interfaces and honestly reports if they are absent (e.g. stock kernel lacks charge-control nodes) |
| ❌ | Not possible — hardware absent on begonia, kernel/OS exposes no interface, or it is app-level (not an OS setting) |
| 🚫 | Declined — illegal in most jurisdictions and/or exists to cheat other users; intentionally not built |

Every ✅/⚠️ row maps to one or more `drk` commands (see `drk help`).

---

## Category 1 — System Performance

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | CPU Governor Optimization | ✅ | `drk cpu governor <gov>` (all clusters, verified) |
| 2 | CPU Frequency Management | ✅ | `drk cpu freq <min\|max> <khz>` (verified) |
| 3 | CPU Core Control | ✅ | `drk cpu cores <n>` / `cores on\|off <cpu>` (verified) |
| 4 | CPU Hotplug | ⚠️ | Hotplug policy is kernel-managed on 4.14 EAS; we control online/offline + boost: `drk cpu hotplug status`, `drk cpu boost` |
| 5 | CPU Boost Control | ✅ | `drk cpu boost <0\|1>` (cpu_boost module, verified) |
| 6 | CPU Input Boost | ✅ | same interface (`input_boost_enabled` / `sched_boost_on_input`) |
| 7 | CPU Scheduler Tuning (CFS, BFQ) | ✅ | `drk cpu sched boost` (schedtune/EAS) + sysctl scheduler tunables in presets; BFQ is an I/O scheduler → `drk fs sched bfq` |
| 8 | GPU Governor Control | ✅ | `drk gpu governor <gov>` (kgsl devfreq, verified) |
| 9 | GPU Frequency Management | ✅ | `drk gpu freq <min\|max> <hz>` (verified) |
| 10 | GPU Renderer Selection | ✅ | `drk gpu renderer <gl\|skia\|skiavk>` (`debug.hwui.renderer`, verified prop) |
| 11 | GPU Skia Rendering | ✅ | same command (`skia`) |
| 12 | GPU Vulkan Enable | ✅ | `drk gpu vulkan <0\|1>` (skiavk renderer + vulkan flags, verified) |
| 13 | GPU Profiling | ✅ | `drk gpu profile <0\|1\|2>` (`profile_gpu_rendering` + `debug.hwui.profile`) |
| 14 | RAM Management (cache clearing) | ✅ | `drk mem clean` (drop_caches trigger) |
| 15 | RAM Swappiness Tuning | ✅ | `drk mem swappiness <n>` (verified sysctl) |
| 16 | RAM Dirty Ratio Tuning | ✅ | `drk mem dirty <n> [bg]` (verified) |
| 17 | RAM VFS Cache Pressure | ✅ | `drk mem vfs <n>` (verified) |
| 18 | RAM Min Free Kbytes | ✅ | `drk mem minfree <kb>` (kernel may clamp — reported, not faked) |
| 19 | RAM Overcommit Control | ✅ | `drk mem overcommit <0\|1\|2> [ratio]` (verified) |
| 20 | RAM OOM Killer Tuning | ✅ | `drk mem oom <task\|panic>` (verified) |
| 21 | Storage Read-Ahead Tuning | ✅ | `drk fs readahead <kb>` (all block devices, verified) |
| 22 | Storage I/O Scheduler (cfq/noop/deadline/fiops/bfq/kyber) | ✅ | `drk fs sched <algo>` — writes only schedulers actually present in the kernel, verified |
| 23 | Storage F2FS/EXT4 Tuning | ⚠️ | `drk fs f2fs <key> <val>` / `ext4 <key> <val>` (generic sysfs, key list via `... keys`) |
| 24 | Storage Auto-Trim | ✅ | `drk fs trim` + weekly automatic fstrim by `drkd` |
| 25 | Storage Journaling Control | ⚠️ | `drk fs journal status` — ext4 journal is read-only while mounted; f2fs uses its own checkpointing (no toggle) |
| 26 | Storage Compression Control | ❌ | f2fs file compression is a **mount-time** option; cannot be changed on a mounted filesystem |
| 27 | Storage Encryption Control | ⚠️ | `drk fs encrypt status` — real state report; toggling encryption requires a factory reset |
| 28 | Storage Deduplication | ❌ | No dm-dedup / similar facility exists in Android kernels |
| 29 | Storage ZRAM Control | ✅ | `drk mem zram <mb> [algo]` / `zram off` (full setup + /proc/swaps verification) |
| 30 | Storage ZRAM Size Management | ✅ | size argument of `drk mem zram` (verified disksize) |
| 31 | ZRAM Compression Algorithm (lz4/lzo/zstd) | ✅ | `drk mem zram <mb> <algo>` — first algorithm supported by the kernel is used, verified |
| 32 | Storage ZSWAP Control | ✅ | `drk mem zswap <0\|1> [algo]` (verified, incl. Y/N boolean read-back) |
| 33 | Storage ZCACHE Control | ❌ | zcache was removed from the kernel (v3.11+); no interface exists |
| 34 | Storage CLEANCACHE Control | ❌ | legacy kernel subsystem, removed; no interface |
| 35 | Storage FRONTSWAP Control | ❌ | boot-time parameter only; no runtime sysfs/sysctl interface |

## Category 2 — Network Advanced

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | TCP Congestion Control (cubic/reno/bbr) | ✅ | `drk net tcp <algo>` — checks `tcp_available_congestion_control` first, verified; honest failure if kernel lacks bbr |
| 2 | TCP Fast Open | ✅ | `drk net fastopen <0-3>` (verified) |
| 3 | TCP SACK / FACK / DSACK | ✅ | `drk net sack <0\|1>` (fack/dsack only where the kernel exposes them) |
| 4 | TCP ECN | ✅ | `drk net ecn <0\|1>` (+ `ecn_fallback` where present) |
| 5 | TCP Timestamps | ✅ | `drk net timestamps <0\|1>` |
| 6 | TCP Window Scaling | ✅ | `drk net window <0\|1>` |
| 7 | TCP MTU Discovery | ✅ | `drk net mtuprobe <0\|1>` (RFC 4821 probing) |
| 8 | TCP Keepalive Tuning | ✅ | `drk net keepalive <time> <intvl> <probes>` |
| 9 | TCP SYN Retries / Backlog | ✅ | `drk net syn <n> [backlog]` (+synack retries) |
| 10 | TCP FIN Timeout | ✅ | `drk net fin <sec>` |
| 11 | TCP Time Wait | ✅ | `drk net tw <0\|1>` (reuse; recycle was removed from the kernel) |
| 12 | TCP Orphan Count | ✅ | `drk net orphan <n>` |
| 13 | TCP Memory Pressure | ✅ | `drk net mem <min> <press> <max>` (pages, verified) |
| 14 | UDP Buffer Size | ✅ | `drk net udp <min> <press> <max>` (+ rmem/wmem min via `drk net buf`) |
| 15 | UDP Checksum Control | ❌ | NIC offload feature (ethtool); not available on stock Android interfaces |
| 16 | IP Forwarding | ✅ | `drk net forward <0\|1>` |
| 17 | IP Redirect Control | ✅ | `drk net redirect <0\|1>` (accept + send, all interfaces) |
| 18 | IP Source Route | ✅ | `drk net srcroute <0\|1>` |
| 19 | IP Reverse Path Filter | ✅ | `drk net rpfilter <0\|1\|2>` |
| 20 | IP Accept/Send Redirects | ✅ | `drk net redirect` (both directions) |
| 21 | IPv6 Support | ✅ | `drk net ipv6 <0\|1>` (all interfaces) |
| 22 | IPv6 Privacy Extensions | ✅ | `drk net v6priv <0\|1\|2>` (use_tempaddr) |
| 23 | IPv6 Router Advertisements | ✅ | `drk net v6ra <0\|1>` |
| 24 | IPv6 Neighbor Discovery | ✅ | `drk net v6dad <n>` (dad_transmits) + retrans_time via sysctl |
| 25 | IPv6 DAD | ✅ | `drk net v6dad <n>` |
| 26 | IPv6 Hop Limit | ✅ | `drk net v6hop <n>` |
| 27 | IPv6 MTU | ✅ | `drk net v6mtu <n>` |
| 28 | DNS Cache | ❌ | Android's resolver (netd) has no user-accessible cache size/behaviour toggle |
| 29 | DNS Prefetch | ❌ | browser/app-level feature; no OS interface |
| 30 | DNS TTL | ❌ | no OS interface on Android |
| 31 | DNS Override (custom DNS) | ✅ | `drk net dns set <ip>` + Private DNS (`drk net dns dot`) |
| 32 | DNS over TLS | ✅ | `drk net dns dot <host>` (`private_dns_mode`/`private_dns_specifier`, verified) |
| 33 | DNS over HTTPS | ❌ | not a system-level setting on Android 10/11 (browser/app level) |
| 34 | DNSSEC | ❌ | no system toggle on stock Android (resolver-side, Google-managed) |
| 35 | HTTP/2 | ❌ | application-level protocol; there is no OS-wide toggle |
| 36 | HTTP/3 | ❌ | application-level |
| 37 | QUIC | ❌ | application-level |
| 38 | WebRTC Control | ❌ | browser/app-level; **leak protection** is covered via `drk net vpnks` |
| 39 | WebSocket Control | ❌ | protocol feature of apps, not an OS setting |
| 40 | MQTT Control | ❌ | protocol-level (app feature) |
| 41 | CoAP Control | ❌ | protocol-level |
| 42 | gRPC Control | ❌ | protocol-level |
| + | VPN Killswitch | ✅ | `drk net vpnks <0\|1>` (iptables: only local/established/tun+ traffic) |
| + | DNS/IP/WebRTC leak protection | ✅/⚠️ | covered by the VPN killswitch + Private DNS |

## Category 3 — Security & Privacy

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | Root Detection Bypass | ✅ | `drk priv deny <pkg>` — Magisk DenyList integration (the official root-hide mechanism) |
| 2 | Magisk Hide | ✅ | DenyList is Magisk's successor to MagiskHide: `drk priv deny enable/disable/list/remove` |
| 3 | Zygisk Hide | ✅ | same DenyList, requires Zygisk (state shown by `drk priv hide status`) |
| 4 | SafetyNet Pass | ⚠️ | `drk priv integrity` + DenyList + optional fingerprint spoof maximize compatibility. **No module can guarantee a pass** (server-side, hardware-backed attestation) — we state the truth instead |
| 5 | Play Integrity Pass | ⚠️ | same approach |
| 6 | Device Spoofing | ✅ | `drk priv spoof <0\|1> [pixel\|begonia]` — resetprop with backup/restore, every prop verified |
| 7 | IMEI Spoofing | 🚫 | **Declined.** Altering a device's IMEI is illegal in most jurisdictions (e.g. 47 U.S.C. § 301 / FCC rules, EU directives) and is a hallmark of fraud and stolen-device concealment — even on your own device |
| 8 | Android ID Spoofing | ✅ | `drk priv androidid <id>` (verified) |
| 9 | Advertising ID Reset | ✅ | `drk priv adid reset` (removes GMS adid.xml + caches, verified) |
| 10 | MAC Randomization | ✅ | `drk priv mac <0\|1>` (framework randomization keys, verified) |
| 11 | WiFi MAC Spoofing | ⚠️ | `drk priv wmac <addr>` / `wmac restore` — kernel-level `ip link`, verified; the Wi-Fi framework may restore its own MAC on reconnect (reported honestly) |
| 12 | Bluetooth MAC Spoofing | ⚠️ | `drk priv bmac <addr>` / `bmac restore` — `btmac.txt` (Qualcomm/Xiaomi method), verified; needs Bluetooth restart |
| 13 | Location Spoofing | ✅ | `drk priv mock <0\|1>` (mock-location developer option; a mock provider app supplies the actual coordinates) |
| 14 | Location Control | ✅ | `drk priv loc <grant\|deny> <pkg>` (FINE/COARSE appops, verified) |
| 15 | Camera Killswitch | ✅ | `drk priv killswitch cam <on\|off>` — CAMERA appop denied/allowed for **every app**, sampled verification |
| 16 | Microphone Killswitch | ✅ | `drk priv killswitch mic` (RECORD_AUDIO appop) |
| 17 | Sensor Killswitch | ⚠️ | `drk priv killswitch body` — BODY_SENSORS appop (heart-rate class); Android exposes no generic per-sensor kill |
| 18 | USB Killswitch | ⚠️ | `drk priv killswitch usb` — sysfs gadget enable/UDC, verified where the kernel exposes it |
| 19 | Bluetooth Killswitch | ✅ | `drk priv killswitch bt` (`svc bluetooth` + settings state) |
| 20 | NFC Killswitch | ✅ | `drk priv killswitch nfc` |
| 21 | WiFi Killswitch | ✅ | `drk priv killswitch wifi` |
| 22 | Mobile Data Killswitch | ✅ | `drk priv killswitch data` |
| 23 | App Ops Management | ✅ | `drk priv appops <pkg> <op> <allow\|deny\|ignore>` + `appops list` |
| 24 | Permission Monitor | ✅ | `drk priv audit` — diff of the full AppOps state since the last run |
| 25 | Permission Auto-Deny | ✅ | `drk priv batch deny <op>` — one AppOp denied for every user app |
| 26 | Permission Auto-Grant | ✅ | `drk priv batch allow <op>` (trusted apps) |
| 27 | Privacy Dashboard | ✅ | `drk priv report` — written privacy report (denied ops, location/camera/mic users) |
| 28 | Data Leak Prevention | ✅ | `drk net fw <block\|allow> <pkg\|uid>` per-app iptables firewall (verified) |
| 29 | Screen Capture Protection | ❌ | No global toggle exists; FLAG_SECURE is per-app (Android 12+ OEM features vary) |
| 30 | Screen Recording Protection | ❌ | same as above |
| 31 | Audio Recording Protection | ✅ | covered by mic killswitch (appops RECORD_AUDIO) |
| 32 | Camera Recording Protection | ✅ | covered by camera killswitch |
| 33 | Clipboard Protection | ⚠️ | `drk priv notif` (clipboard-access notifications on supported ROMs) + `drk priv clip clear` |
| 34 | Clipboard Auto-Clear | ⚠️ | `drk priv clip clear` — binder interface varies by Android version; reports honestly if unsupported |
| 35 | Notification Privacy | ✅ | `drk priv notif <0\|1>` — hides notification content on the lock screen (verified) |
| 36 | Lock Screen Privacy | ✅ | same keys (`lock_screen_allow_private_notifications`) |
| 37 | Recent Apps Privacy | ❌ | no AOSP/MIUI shell interface (launcher-level feature) |
| 38 | Status Bar Privacy | ❌ | no generic interface on stock MIUI |
| 39 | Network Privacy | ✅ | per-app firewall + VPN killswitch + private DNS |
| 40 | File Metadata Protection | ❌ | no OS-level interface (app/file-manager level) |
| 41 | EXIF Removal | ❌ | app-level, at share time; no OS toggle exists |
| 42 | VPN Killswitch | ✅ | `drk net vpnks` |
| 43 | DNS Leak Protection | ✅ | VPN killswitch + `drk net dns dot` |
| 44 | IP Leak Protection | ✅ | VPN killswitch |
| 45 | WebRTC Leak Protection | ⚠️ | IP-level covered by the killswitch; the WebRTC toggle itself is browser-level |

## Category 4 — Hardware Control

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | CPU Core Control | ✅ | (dup of Cat 1) `drk cpu cores` |
| 2 | CPU Frequency Control | ✅ | (dup) `drk cpu freq` |
| 3 | CPU Voltage Control | ❌ | begonia stock kernel exposes **no** writable vdd interface (`drk hw volt cpu` lists what it searched); requires a custom kernel |
| 4 | CPU Temperature Monitor | ✅ | `drk hw temp cpu` (thermal zones, live) |
| 5 | CPU Throttling Control | ❌ | thermal-engine policy is not user-configurable on stock; `drk hw throttle status` reports real state |
| 6 | GPU Frequency Control | ✅ | (dup) `drk gpu freq` |
| 7 | GPU Voltage Control | ❌ | no interface on stock kernel (`drk hw volt gpu` shows the search) |
| 8 | GPU Temperature Monitor | ✅ | `drk hw temp gpu` |
| 9 | GPU Throttling Control | ❌ | same as CPU throttling |
| 10 | Memory Frequency Control | ⚠️ | `drk hw devfreq <name> <min\|max> <hz>` + `devfreq list` — devfreq nodes where the kernel exposes them |
| 11 | Memory Voltage Control | ❌ | no interface |
| 12 | Memory Temperature Monitor | ⚠️ | via thermal zones (`drk hw temp all`) where pmic/emmc zones exist |
| 13 | Storage Frequency Control | ❌ | UFS/eMMC clocks are driver-managed, not user-controllable |
| 14 | Storage Voltage Control | ❌ | no interface |
| 15 | Storage Temperature Monitor | ⚠️ | thermal zones if exposed (`drk hw temp all`) |
| 16 | Battery Charge Limit | ⚠️ | `drk batt limit <0-100>` — searches real charge-control nodes; **stock begonia kernel usually has none** and the command says so (custom kernel required) |
| 17 | Battery Charge Speed | ⚠️ | `drk batt speed <fast\|slow\|default>` — same honesty |
| 18 | Battery Temperature Monitor | ✅ | `drk batt info` + `drkd` over-temp alerts |
| 19 | Battery Voltage Monitor | ✅ | `drk batt info` (voltage_now live) |
| 20 | Battery Health Monitor | ✅ | `drk batt health` (charge_full vs design, verdict) |
| 21 | Battery Cycle Monitor | ⚠️ | `drk batt cycles` (cycle_count where exposed) |
| 22 | Display Brightness Control | ✅ | `drk hw brightness <0-255>` (sysfs + setting, verified) |
| 23 | Display Color Calibration | ⚠️ | `drk hw color <standard\|vivid\|natural\|cinema>` — MIUI/`cmd display` keys, verified read-back |
| 24 | Display Contrast Control | ⚠️ | via the same OEM color-mode keys where the ROM exposes them |
| 25 | Display Saturation Control | ⚠️ | same |
| 26 | Display Hue Control | ❌ | no shell interface on stock MIUI |
| 27 | Display Gamma Control | ⚠️ | partial: night-mode color temperature is a real gamma adjustment (`drk hw night <0\|1> <temp>`) |
| 28 | Display Temperature (blue light) | ✅ | `drk hw night` / `drk disp blue` |
| 29 | Display Refresh Rate Control | ⚠️ | `drk hw refresh <hz>` — real keys, but the panel is 60 Hz (values above 60 ignored by hardware, reported) |
| 30 | Display Resolution Control | ✅ | `drk hw res <WxH>\|reset` (`wm size`, verified) |
| 31 | Display DPI Control | ✅ | `drk hw dpi <n>\|reset` |
| 32 | Display Scaling Control | ✅ | `drk acc size <scale>` (`display_size_forced`) + `drk hw dpi` |
| 33 | Haptic Feedback Control | ✅ | `drk hw haptic <0\|1>` (verified) |
| 34 | Haptic Intensity Control | ⚠️ | `drk hw haptic intensity <0-100>` — vibrator sysfs where exposed |
| 35 | Haptic Frequency Control | ❌ | no user interface on Android |
| 36 | Vibration Intensity Control | ⚠️ | same as haptic intensity |
| 37 | Vibration Pattern Control | ❌ | app-level (no OS interface) |
| 38 | LED Notification Control | ❌ | begonia has **no notification LED** hardware (LCD panel, no RGB LED) |
| 39 | LED Color/Pattern/Brightness | ❌ | same — hardware absent |

## Category 5 — Sensor Calibration

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | Accelerometer Calibration | ⚠️ | `drk sens reset accel` — clears HAL calibration files (begonia paths), sensors re-calibrate |
| 2 | Gyroscope Calibration | ⚠️ | `drk sens reset gyro` |
| 3 | Magnetometer Calibration | ⚠️ | `drk sens reset mag` |
| 4 | Proximity Sensor Calibration | ⚠️ | `drk sens reset prox` |
| 5 | Light Sensor Calibration | ⚠️ | `drk sens reset light` |
| 6 | Pressure Sensor Calibration | ❌ | no barometer hardware on begonia |
| 7 | Temperature Sensor Calibration | ❌ | thermal zones are read-only monitors; no user calibration exists |
| 8 | Humidity Sensor Calibration | ❌ | hardware absent |
| 9 | Step Counter Calibration | ⚠️ | virtual sensor — no calibration exists; `drk sens info` lists it; fitness apps recalibrate via stride settings |
| 10 | Heart Rate Sensor Calibration | ❌ | hardware absent (no PPG sensor on begonia) |
| 11 | Blood Oxygen (SpO2) Calibration | ❌ | hardware absent |
| 12 | ECG Sensor Calibration | ❌ | hardware absent |
| 13 | EEG Sensor Calibration | ❌ | hardware absent |
| 14 | EMG Sensor Calibration | ❌ | hardware absent |
| 15 | GSR Sensor Calibration | ❌ | hardware absent |
| 16 | Skin Temperature Calibration | ❌ | hardware absent |
| 17 | UV Sensor Calibration | ❌ | hardware absent |
| 18 | IR Sensor Calibration | ❌ | no IR *sensor* (the IR blaster on begonia is an output-only emitter for remote control) |
| 19 | Ultrasonic Calibration | ❌ | hardware absent |
| 20 | Force Sensor Calibration | ❌ | hardware absent |
| 21 | Position/Orientation Calibration | ⚠️ | virtual sensors — `drk sens info` reports them; no calibration exists (they derive from accel/gyro/mag) |
| 22 | Gravity Calibration | ⚠️ | same |
| 23 | Linear Acceleration Calibration | ⚠️ | same |
| 24 | Game Rotation Calibration | ⚠️ | same |
| 25 | Geomagnetic Rotation Calibration | ⚠️ | same |
| 26 | Significant Motion Calibration | ⚠️ | same |
| 27 | Tilt Detector Calibration | ⚠️ | same |
| + | Sensor inventory | ✅ | `drk sens info` — every real sensor on the device via sensorservice dump |

## Category 6 — Audio Engine

begonia's stock kernel exposes **no user-level DSP/EQ interface** (no audio DSP sysfs).
EQ-class effects would require a vendor HAL modification or an external DSP audio module
(ViPER/JamesDSP) — a shell script that "enables" them would be a fake feature. `drk audio eq`
explains exactly this. Everything that *is* controllable is implemented:

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | Audio Codec Selection | ❌ | HAL-managed; no user interface |
| 2 | Audio Bitrate Control | ❌ | app-level (encoder settings) |
| 3 | Audio Sampling Rate | ❌ | HAL-managed; no stock interface |
| 4 | Audio Channel Control | ❌ | no stock interface (DSP) |
| 5 | Audio EQ (10-band) | ❌ | no DSP interface on stock — external DSP module required |
| 6 | Audio Bass Boost | ❌ | same |
| 7 | Audio Virtualizer | ❌ | same |
| 8 | Audio Reverb | ❌ | same |
| 9 | Audio Surround | ❌ | same |
| 10 | Audio Loudness | ❌ | same (HAL) |
| 11 | Audio Clarity | ❌ | same |
| 12 | Audio Stereo Widening | ❌ | same |
| 13 | Audio Time Stretch | ❌ | app-level (audio editing) |
| 14 | Audio Pitch Shift | ❌ | app-level |
| 15 | Audio Tempo Control | ❌ | app-level |
| 16 | Audio Key Control | ❌ | app-level |
| 17 | Audio Filter (LPF/HPF/BPF) | ❌ | DSP-level |
| 18 | Audio Compressor | ❌ | DSP-level |
| 19 | Audio Limiter | ❌ | DSP-level |
| 20 | Audio Gate | ❌ | DSP-level |
| 21 | Audio Delay | ❌ | DSP/app-level |
| 22 | Audio Volume/Balance/Pan | ⚠️ | `drk audio volume <stream> <0-15>` (verified); mono via `drk audio mono`; balance has no standard stock key |
| 23 | Audio Fade/Crossfade | ❌ | app-level |
| 24 | Audio Ducking | ❌ | framework-managed |
| 25 | Audio Sidechain | ❌ | app-level |
| 26 | Audio Multiband Compression | ❌ | DSP-level |
| 27 | Audio DeEsser | ❌ | DSP-level |
| 28 | Audio Noise Gate | ❌ | DSP-level |
| 29 | Audio Expander | ❌ | DSP-level |
| 30 | Audio Transient Shaper | ❌ | DSP-level |
| 31 | Audio Harmonic Exciter | ❌ | DSP-level |
| + | What IS implemented | ✅ | `drk audio info` (HAL dump), `mono`, `volume`, `dnd` (zen mode), `fluence` (real Qualcomm mic props: voicecall/voicerec/speaker/tmic), `props` (any persist.vendor.audio.*), `eq` (honest status) |

## Category 7 — Camera Engine

Manual photography controls (ISO, shutter, WB, focus, …) are **Camera2 API features of the
camera app** — Android exposes no OS-level interface to set them globally, so DRKMUQEET does
not fake them. The camera hardware on begonia: 64 MP wide + 8 MP ultrawide + 2 MP macro +
2 MP depth, no telephoto, no OIS, no ToF.

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | Camera ISO Control | ❌ | Camera2 app-level (Pro mode uses it) |
| 2 | Camera Shutter Speed | ❌ | app-level |
| 3 | Camera Aperture Control | ❌ | app-level; fixed lens |
| 4 | Camera Focus (AF/MF) | ❌ | app-level |
| 5 | Camera White Balance | ❌ | app-level |
| 6 | Camera Color Temperature | ❌ | app-level |
| 7 | Camera Exposure | ❌ | app-level |
| 8 | Contrast/Saturation/Sharpness | ❌ | app-level |
| 9 | Camera Noise Reduction | ❌ | app-level |
| 10 | Camera HDR | ❌ | app-level |
| 11 | Camera EIS | ⚠️ | `drk cam eis <0\|1>` — real vendor props, HAL-dependent |
| 12 | Camera OIS | ❌ | hardware absent |
| 13 | Camera AF/MF | ❌ | app-level |
| 14 | Camera Macro | ❌ | app feature (2 MP macro lens exists; not OS-controllable) |
| 15 | Camera Telephoto | ❌ | hardware absent |
| 16 | Camera UltraWide | ❌ | app feature (8 MP ultrawide exists) |
| 17 | Camera Depth | ❌ | app feature (2 MP depth lens) |
| 18 | Camera ToF | ❌ | hardware absent |
| 19 | Camera IR | ❌ | no IR camera |
| 20 | Camera Night Mode | ❌ | app feature |
| 21 | Camera Portrait Mode | ❌ | app feature |
| 22 | Camera Pro Mode | ❌ | app feature (uses Camera2 manual controls) |
| 23 | Raw/DNG/TIFF Capture | ❌ | app-level (begonia supports DNG via Camera2 RAW in capable apps) |
| 24 | Camera Video Stabilization | ⚠️ | EIS props (`drk cam eis`) |
| 25 | Camera Video HDR | ❌ | app feature |
| 26 | Camera Video SlowMo | ❌ | app feature |
| 27 | Camera Video TimeLapse | ❌ | app feature |
| + | What IS implemented | ✅ | `drk cam info` (real camera list/capabilities), `hal3 <0\|1>` (Camera2 full-HAL toggle, verified), `disable\|enable` (camera app), `props` (persist.vendor.camera.*) |

## Category 8 — Display Engine

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | Display Mode (Standard/Vivid/Natural/Cinema) | ⚠️ | `drk disp mode <mode>` — MIUI/`cmd display` keys, verified |
| 2 | Display Color Profile (sRGB/DCI-P3/Adobe RGB) | ❌ | no interface on stock MIUI |
| 3 | Display Color Space (RGB/YUV/HSV/LAB) | ⚠️ | `drk dev simcolor <0-3>` (simulate color space dev option) |
| 4 | Display Gamma | ⚠️ | partial: night color temperature (`drk hw night <0\|1> <temp>`) |
| 5 | Display Contrast | ⚠️ | OEM color-mode keys where exposed |
| 6 | Display Brightness | ✅ | `drk hw brightness` |
| 7 | Display Auto Brightness | ✅ | `drk disp autobright <0\|1>` |
| 8 | Display Night Mode | ✅ | `drk disp blue` / `drk hw night` |
| 9 | Display Blue Light Filter | ✅ | same |
| 10 | Display Dark Mode | ✅ | `drk disp dark <0\|1\|2>` |
| 11 | Display Always On | ❌ | LCD panel — no AOD support (hardware) |
| 12 | Display Ambient Display | ⚠️ | `drk disp ambient` (doze keys; LCD may not render — reported) |
| 13 | Display Edge Lighting | ❌ | MIUI feature with no shell interface |
| 14 | Display Notification Light | ❌ | no LED hardware |
| 15 | Display Screen Saver | ✅ | `drk disp saver <0\|1>` |
| 16 | Display Rotation Control | ✅ | `drk disp rotate <auto\|0\|90\|180\|270>` |
| 17 | Display Gesture Navigation | ⚠️ | `drk disp gesture <0\|1\|2>` (`navigation_mode`; MIUI may use its own) |
| 18 | 3/4/5-Finger Gesture | ❌ | no interface on stock MIUI |
| 19 | Display Palm Rejection | ⚠️ | `drk disp touch sensitive\|glove\|normal` — MIUI keys, try-verified |
| 20 | Display Glove Mode | ⚠️ | same |
| 21 | Display High Touch Sensitivity | ⚠️ | same |
| 22 | Display Touch Response | ⚠️ | same keys |
| 23 | Display Touch Sampling Rate | ❌ | no stock interface |
| 24 | Display HDR/HDR10/HDR10+ | ⚠️ | `drk disp hdr status` — real panel capability report; HDR output is app/HAL-driven |

## Category 9 — Battery & Charging

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | Charge Limit | ⚠️ | `drk batt limit <0-100>` — real charge-control nodes, honestly reported (stock begonia usually lacks them) |
| 2 | Charge Speed Control | ⚠️ | `drk batt speed <fast\|slow\|default>` — same honesty |
| 3 | Charge Voltage/Current Control | ⚠️ | same interfaces (input_current_limit / constant_charge_current_max) |
| 4 | Charge Temperature Monitoring | ✅ | `drk batt info` + `drkd` alerts |
| 5 | Charge Protection | ⚠️ | charger/thermal driver controlled; we monitor and alert (temp thresholds) |
| 6 | Fast Charge / Quick Charge / Dash / VOOC / SuperVOOC / Warp | ❌ | vendor charger-IC proprietary control; no generic interface (begonia uses QC3.0/4+ internally, not user-tunable) |
| 7 | USB-PD | ❌ | hardware/charger-driven; begonia supports Quick Charge, not USB-PD |
| 8 | Qi Wireless Charging | ❌ | no wireless coil hardware |
| 9 | Reverse Wireless Charging | ❌ | hardware absent |
| 10 | Battery Health Monitor | ✅ | `drk batt health` |
| 11 | Battery Cycle Counter | ⚠️ | `drk batt cycles` (where exposed) |
| 12 | Battery Capacity Calibration | ✅ | `drk batt calib` (battery stats reset + recalibration) |
| 13 | Voltage/Current/Power/Energy Monitoring | ✅ | `drk batt info` (live sysfs; power/energy derived) |
| 14 | Battery Temperature Monitoring | ✅ | `drk batt info` + daemon alerts |
| 15 | Battery Resistance/Impedance Monitoring | ❌ | not exposed on stock begonia kernel (diagnostic hardware feature) |
| 16 | Battery Age/Degradation Estimation | ✅ | `drk batt health` (charge_full vs design) |
| 17 | Battery Remaining/Time Estimation | ✅ | `drk batt eta` (computed from live current + capacity) |
| 18 | Battery Drain Monitoring | ✅ | `drk batt drain <start\|stop\|show>` + `drkd` CSV sampling every 5 min |

## Category 10 — Memory Management

(Items marked *dup* are the same real tools as Category 1 — repeated in the original spec.)

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | Memory Cleaner | ✅ | `drk mem clean` |
| 2 | Memory Compactor | ✅ | `drk mem compact` |
| 3 | Memory Defragmenter | ❌ | no such interface exists on Linux/Android; compaction + reclaim are the real equivalents |
| 4 | Memory Optimizer | ✅ | `drk perf <preset>` |
| 5 | Memory Booster | ✅ | `drk mem clean` + `drk game on` |
| 6 | Memory Swappiness | ✅ | *dup* — `drk mem swappiness` |
| 7 | Memory VFS Cache | ✅ | *dup* — `drk mem vfs` |
| 8 | Memory Dirty Ratio | ✅ | *dup* — `drk mem dirty` |
| 9 | Memory OOM Killer | ✅ | *dup* — `drk mem oom` |
| 10 | Memory ZRAM | ✅ | *dup* — `drk mem zram` |
| 11 | Memory ZSWAP | ✅ | *dup* — `drk mem zswap` |
| 12 | Memory ZCACHE | ❌ | *dup* — removed from kernel |
| 13 | Memory CLEANCACHE | ❌ | *dup* — removed from kernel |
| 14 | Memory FRONTSWAP | ❌ | *dup* — boot-param only |
| 15 | Memory KSM | ✅ | `drk mem ksm <0\|1>` (verified) |
| 16 | Memory CMA | ❌ | boot-time allocator configuration; no runtime interface on stock |
| 17 | Memory HugeTLB | ✅ | `drk mem hugepages <n>` (verified) |
| 18 | Memory Transparent Huge Pages | ✅ | `drk mem thp <always\|madvise\|never>` (verified) |
| 19 | Memory Page Clustering | ❌ | removed from the kernel (legacy) |
| 20 | Memory Page Reclaim | ✅ | `drk mem clean` / `compact` + `vfs_cache_pressure` |

## Category 11 — Process Management

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | Process Priority Control | ✅ | `drk proc nice <pid\|pkg> <delta>` (verified via /proc stat) |
| 2 | Process Nice Value Control | ✅ | same |
| 3 | Process Scheduling | ✅ | `drk proc nice` + `boost` (schedtune) + `standby` (app standby buckets) |
| 4 | Process Affinity | ✅ | `drk proc affinity <pid> <mask>` (taskset, verified) |
| 5 | Process Isolation | ⚠️ | background ban via AppOps + force-stop; full isolation is the work-profile model, not scriptable |
| 6 | Process Sandboxing | ⚠️ | app sandbox is kernel-enforced; we manage background execution & standby |
| 7 | Process Monitoring | ✅ | `drk proc list` (top CPU/RAM) |
| 8 | Process Kill | ✅ | `drk proc kill <pid\|pkg>` |
| 9 | Process Kill All | ✅ | `drk proc killall` |
| 10 | Process Battery Optimization | ✅ | `drk proc whitelist` + `restrict` + `standby` |
| 11 | Process Background Restriction | ✅ | `drk proc restrict <pkg> <on\|off>` (RUN_ANY_IN_BACKGROUND) |
| 12 | Process Auto-Start Control | ⚠️ | AppOps background ban + standby buckets; MIUI's autostart manager has no shell API |
| 13 | Process Wakeup Control | ⚠️ | `drk proc alarms <pkg> <deny\|allow>` (WAKE_ALARM appop) |
| 14 | Process Alarm Control | ⚠️ | same |
| 15 | Process Job Control | ⚠️ | via standby buckets + background ban (no direct job-scheduler shell API) |
| 16 | Process Sync Control | ⚠️ | via standby/restrict (sync is framework-scheduled) |
| 17 | Process Location Control | ✅ | `drk priv loc <grant\|deny> <pkg>` |
| 18 | Process Sensor Control | ⚠️ | BODY_SENSORS appop only (no generic sensor appop) |
| 19 | Process Network Control | ✅ | `drk net fw <block\|allow> <pkg\|uid>` |

## Category 12 — Logging & Debugging

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | Logcat Viewer | ✅ | `drk log cat [filter]` |
| 2 | Logcat Filter | ✅ | `drk log cat <filter>` |
| 3 | Logcat Save | ✅ | `drk log cat -f <filter>` |
| 4 | Kernel Log Viewer | ✅ | `drk log kmsg [lines]` |
| 5 | Kernel Log Filter | ✅ | `drk log kmsg [lines]` (tail + grep as needed) |
| 6 | Kernel Log Save | ✅ | `drk log kmsg -f` |
| 7 | System Log Viewer | ✅ | `drk log sys [service]` |
| 8 | System Log Filter | ✅ | `drk log sys <service>` |
| 9 | System Log Save | ✅ | `drk log sys -f <service>` |
| 10 | Crash Log Viewer | ✅ | `drk log crash` (DropBox + tombstones) |
| 11 | Crash Log Filter | ✅ | `drk log crash` (per-app via grep) |
| 12 | Crash Log Save | ✅ | shell redirect / `drk log dumpstate` |
| 13 | ANR Log Viewer | ✅ | `drk log anr` |
| 14 | ANR Log Filter | ✅ | per-app filtering on the ANR dump |
| 15 | ANR Log Save | ✅ | files copied from /data/anr |
| 16 | Trace Viewer | ✅ | `drk log trace <cat> <sec> <file>` (atrace) |
| 17 | Trace Filter | ✅ | category argument (gfx/view/sched/...) |
| 18 | Trace Save | ✅ | written to /data/adb/drk/logs/ |
| 19 | Dumpstate Viewer | ✅ | `drk log sys` + `drk log dumpstate` |
| 20 | Dumpstate Filter | ✅ | per-service dumps |
| 21 | Dumpstate Save | ✅ | `drk log dumpstate` |
| 22 | Bug Report Generation | ✅ | `drk log bugreport` (real `bugreport` with dumpstate fallback) |
| 23 | Bug Report Save | ✅ | saved under /data/adb/drk/logs/ |
| 24 | Bug Report Share | ❌ | no shell "share" intent exists; the file is at a known path for manual sharing |
| + | Housekeeping | ✅ | `drk log clear`, `drk log size` |

## Category 13 — Developer Tools

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | USB Debugging | ✅ | `drk dev usb <0\|1>` (verified) |
| 2 | ADB Over Network | ✅ | `drk dev adbnet <0\|1> [port]` (restarts adbd, verified) |
| 3 | OEM Unlocking | ⚠️ | `drk dev oem status` — the toggle is **fastboot-only** (`fastboot oem unlock`); runtime key is read-only |
| 4 | Developer Options Access | ✅ | `drk dev devopts <0\|1>` |
| 5 | GPU Rendering (2D) | ✅ | `drk gpu forcegpu <0\|1>` |
| 6 | 4x MSAA | ⚠️ | `drk gpu msaa <0\|1>` (`multisample` key; effect is ROM/GPU-driver dependent) |
| 7 | HW Overlays | ✅ | `drk gpu overlay <0\|1>` (`disable_hw_overlays`) |
| 8 | Don't Keep Activities | ✅ | `drk dev keep <0\|1>` |
| 9 | Background Process Limit | ✅ | `drk dev bglimit <n>` (device_config `max_cached_processes`, verified) |
| 10 | Window Animation Scale | ✅ | `drk dev anim <scale>` |
| 11 | Transition Animation Scale | ✅ | same |
| 12 | Animator Duration Scale | ✅ | same |
| 13 | Force GPU Rendering | ✅ | `drk gpu forcegpu` |
| 14 | Force 4x MSAA | ⚠️ | `drk gpu msaa` |
| 15 | Disable HW Overlays | ✅ | `drk gpu overlay` |
| 16 | Simulate Secondary Displays | ✅ | `drk dev simdisp <on\|off> [WxH/dpi]` |
| 17 | Simulate Color Space | ⚠️ | `drk dev simcolor <0-3>` |
| 18 | Show All ANRs | ✅ | `drk dev anr <0\|1>` |
| 19 | Show All Crashes | ✅ | `drk dev hwerr <0\|1>` (error dialogs) |
| 20 | Verbose Logging | ✅ | `drk log cat` / `log sys` |
| 21 | Strict Mode | ⚠️ | `drk dev strict <0\|1>` (prop; effective mainly on debuggable builds) |
| 22 | Profile GPU Rendering | ✅ | `drk gpu profile <0\|1\|2>` |
| 23 | Debug GPU Overdraw | ✅ | `drk dev overdraw <0-3>` |
| 24 | Debug Non-Rectangular Clips | ✅ | `drk dev cliprect <0\|1>` |
| 25 | Force RTL Layout | ✅ | `drk dev rtl <0\|1>` |
| 26 | Stay Awake While Charging | ✅ | `drk dev stayon <0\|1>` |

## Category 14 — Accessibility

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | Font Size Control | ✅ | `drk acc font <scale>` (verified) |
| 2 | Display Size Control | ✅ | `drk acc size <scale>` (`display_size_forced`) |
| 3 | Bold Text | ⚠️ | `drk acc bold <0\|1>` — OEM key, try-verified |
| 4 | High Contrast Text | ⚠️ | `drk acc contrast <0\|1>` |
| 5 | Color Inversion | ✅ | `drk acc invert <0\|1>` |
| 6 | Color Correction | ✅ | `drk acc dalton <0\|1> [mode]` |
| 7 | Grayscale | ⚠️ | `drk acc gray <0\|1>` — OEM key with inversion fallback |
| 8 | Magnification Gesture | ⚠️ | `drk acc magnify <0\|1>` |
| 9 | Large Mouse Pointer | ⚠️ | `drk acc pointer <0\|1>` |
| 10 | Click After Hover | ❌ | desktop input feature; not present in Android |
| 11 | Power Button Ends Call | ✅ | `drk acc powercall <0\|1>` |
| 12 | Auto-Rotate Screen | ✅ | `drk acc rotate <0\|1>` |
| 13 | Speak Passwords | ✅ | `drk acc speakpw <0\|1>` |
| 14 | Accessibility Menu | ⚠️ | framework feature; enable via `drk acc service <component> 1` |
| 15 | Switch Access | ⚠️ | `drk acc service <component> <0\|1>` (e.g. com.android.talkback/...SwitchAccessService) |
| 16 | Voice Access | ⚠️ | Google app service; enable via `drk acc service` |
| 17 | Select to Speak | ⚠️ | same mechanism |
| 18 | TalkBack | ✅ | `drk acc talkback <0\|1>` |
| 19 | Braille Support | ❌ | requires an external braille display + TalkBack braille backend; no toggle |
| 20 | Captioning | ✅ | `drk acc captions <0\|1> [size]` |

## Category 15 — Game Development

| # | Feature | Status | Implementation / reason |
|---|---|---|---|
| 1 | Game Mode | ✅ | `drk game <on\|off>` — legitimate performance profile (schedtune boost, CPU/GPU headroom, animations off, background apps stopped) |
| 2 | Performance Mode | ✅ | `drk perf performance` |
| 3 | Gaming Optimization | ✅ | `drk game on` |
| 4 | Memory Scanning | 🚫 | **Declined.** Game-memory scanning/editing exists to cheat in games (and is malware-adjacent). Not "testing". |
| 5 | Value Editing | 🚫 | Declined — same reason |
| 6 | Speed Hacking | 🚫 | Declined |
| 7 | Teleport | 🚫 | Declined |
| 8 | Fly Hack | 🚫 | Declined |
| 9 | No Clip | 🚫 | Declined |
| 10 | God Mode | 🚫 | Declined |
| 11 | Infinite Ammo | 🚫 | Declined |
| 12 | No Reload | 🚫 | Declined |
| 13 | Rapid Fire | 🚫 | Declined |
| 14 | No Recoil | 🚫 | Declined |
| 15 | Aimbot | 🚫 | Declined — exists solely to gain unfair advantage over other players |
| 16 | Wallhack | 🚫 | Declined |
| 17 | ESP | 🚫 | Declined |
| 18 | Chams | 🚫 | Declined |
| 19 | Trigger Bot | 🚫 | Declined |
| 20 | Auto Farm | 🚫 | Declined |
| 21 | Auto Heal | 🚫 | Declined |
| 22 | Auto Shield | 🚫 | Declined |

---

## Summary

| Status | Meaning | Approx. count |
|---|---|---|
| ✅ implemented & verified | real, working tools | ~170 |
| ⚠️ implemented, device-dependent | tries real interfaces, reports honestly | ~55 |
| ❌ not possible | hardware absent / no interface / app-level | ~170 |
| 🚫 declined | illegal or cheating tools | ~23 |
| (exact duplicates in the spec, merged) | | ~40 |

**Bottom line:** every feature that can truthfully exist on a begonia with the stock kernel
is implemented and verifiable — nothing is stubbed, claimed, or faked. Run `drk doctor` on
your device for the live report.
