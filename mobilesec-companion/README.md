# DRK MobileSec companion

A small Android companion for [lu77U-MobileSec](https://github.com/sam-mg/lu77U-MobileSec), by Sam MG Harish. It opens the upstream dashboard on the phone through USB port forwarding; it does **not** include or independently run its Python analysis engine. Its opening animation credits **by @DRKMUQEET**. Requires Android 8.0 (API 26) or newer.

The provided `deliverables/DRK-MobileSec-debug.apk` is a development-signed build, not an audited production release. Enable installation from your chosen file manager only if you trust the source. The development signing key used in this environment is not included: another build signed with a different key cannot update this installation in place. Uninstall the development build first or sign future builds with your own stable key. No phone or emulator was available for end-to-end testing.

## Connect to the upstream server

1. On a trusted computer, follow the upstream README to install and run `lu77U-MobileSec`. Note the **actual port printed** on launch (usually 8000, but the server can choose another). Leave the upstream server bound to `127.0.0.1`.
2. Install the APK on the phone. Enable Android USB debugging, connect the phone to the computer by USB, and approve the computer's debugging key when prompted.
3. On the computer, run `adb devices` to confirm the device is authorized, then run `adb reverse tcp:8000 tcp:8000`, replacing **both** 8000 values with the server's actual port. If multiple devices are connected, use `adb -s SERIAL reverse ...`. Set the same port in the companion's **Settings**.
4. Tap **Open dashboard**. Use the upstream dashboard to select an APK, run supported scans, adjust upstream settings and retrieve reports. What works depends on the installed upstream version, its external dependencies and the connected device. The file picker and HTTP report downloads are integrated, but their end-to-end behavior has not been verified on a device; blob downloads are not supported.
5. When finished, run `adb reverse --remove tcp:8000` (using the configured port), stop the upstream server, and disable USB debugging if no longer needed. The reverse mapping can also disappear when the connection ends.

The app only accepts a plain HTTP dashboard at `127.0.0.1` and the configured port, not an arbitrary remote URL. The traffic is intended to stay on the phone/computer USB tunnel. Upstream's server has **no authentication**: do not bind it to a LAN/public interface or expose the reverse port to untrusted apps or websites. Other software on the phone may be able to reach its loopback port while the tunnel exists. A compromised computer or server can also control dashboard content; only connect to a server you trust. External links open in a separate browser and ordinary off-origin WebView resource requests are blocked; this is not a complete network firewall (for example, WebSockets may not pass through that interception). There is no JavaScript-to-native bridge.

## Root and device safety

**Root check** is optional and separately confirmed. It requests superuser permission and runs only `su -c id` to show the effective identity; it does not modify the phone or give the upstream scanner root access. Root availability depends on the device and its superuser manager.

Upstream's dynamic-analysis features run from the computer and may require ADB, Frida, mitmproxy and/or root, depending on the action. Some upstream readiness checks deploy instrumentation and install an interception CA or change a device proxy. Review the upstream documentation and device state before using them, use only devices/apps you own or are authorized to test, and remove any installed CA or proxy configuration afterwards. Root is not a guarantee that all features will work.

## Customization and scope

Settings allow changing the loopback port and selecting an accent color for the native chrome and next launch animation. The dashboard itself is provided by the upstream server, including its own settings. The native screen, logo and behavior are editable in `src/` and `res/`; further customization requires rebuilding. The project does not claim complete customization or full feature parity with upstream, and does not embed all upstream dependencies.

## Build from source

Install JDK 17, Android SDK platform 34 and build-tools 34.0.0. Then run from the repository root:

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_JAR=/path/to/android-sdk/platforms/android-34/android.jar
export ANDROID_BUILD_TOOLS=/path/to/android-sdk/build-tools/34.0.0
./mobilesec-companion/build.sh
```

The APK is written to `deliverables/DRK-MobileSec-debug.apk`. By default, the script creates a local development keystore under `.cache/`, which is ignored by Git. To keep update compatibility, preserve your own keystore securely and set `KEYSTORE`, `KEY_ALIAS`, `KEYSTORE_PASSWORD`, and `KEY_PASSWORD` when building. Never commit a private signing key. The included test build was built with a JDK 17 and Android SDK 34 toolchain and its APK signature and alignment verified; it has not been installed or exercised on hardware.
