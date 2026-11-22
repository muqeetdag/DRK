#!/usr/bin/env python3
"""Static acceptance checks for the built APK (no device required).

Run:  python3 tests/apk_checks.py dist/nyx-1.0.0-obsidian.apk
Covers the failure classes that actually cause "crashes on open":
missing resources, missing activity classes, zero-size gradients in draw code,
and unbounded per-view hooks inside the host app.
"""
import glob
import re
import subprocess
import sys
import zipfile

fails = []


def check(name, ok, detail=""):
    print(f"  {'PASS' if ok else 'FAIL'}  {name}{(' — ' + detail) if detail and not ok else ''}")
    if not ok:
        fails.append(name)


def main(apk):
    aapt2 = ".build/tools/aapt2"
    res = subprocess.run([aapt2, "dump", "resources", apk], capture_output=True, text=True).stdout
    z = zipfile.ZipFile(apk)
    dex = z.read("classes.dex").decode("latin-1")

    # every activity declared must exist in the dex
    manifest = subprocess.run([aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
                              capture_output=True, text=True).stdout
    activities = set(re.findall(r'android:name\(0x01010003\)="\.?(ui\.[A-Za-z]+)"', manifest))
    check("manifest declares onboarding + console", {"ui.OnboardingActivity", "ui.MainActivity"} <= activities,
          str(activities))
    for act in activities:
        desc = ("Lio/nyx/app/" + act.replace(".", "/") + ";").encode().decode("latin-1")
        check(f"activity {act} present in dex", desc in dex)

    # launcher must be onboarding
    check("launcher activity is onboarding", "launchable-activity: name='io.nyx.app.ui.OnboardingActivity'" in
          subprocess.run([aapt2, "dump", "badging", apk], capture_output=True, text=True).stdout)

    # every own resource reference resolves
    declared = re.findall(r"resource 0x[0-9a-f]+ (\w+)/(\w+)", res)
    own = {f"{t}/{n}" for t, n in declared if t not in ("anim", "id")}
    check("app resources declared", len(own) > 8, str(len(own)))
    for need in ("style/Nyx", "color/nyx_accent", "string/app_name", "drawable/nyx_dialog_bg"):
        check(f"resource {need} present", need in own)

    # code-level crash guards: scan sources, since dex only keeps string constants
    sources = ""
    for path in glob.glob("app/src/**/*.java", recursive=True):
        with open(path, encoding="utf-8") as fh:
            sources += fh.read()
    check("zero-size radial gradient guard (console + splash + dock)",
          sources.count("w <= 0") >= 3, str(sources.count("w <= 0")))
    for guard, why in [
        ("SafePrefs", "crash-safe preference access"),
        ("CrashLog", "on-device crash log"),
        ("setDefaultUncaughtExceptionHandler", "uncaught handler"),
    ]:
        check(why, guard in sources)

    # unsafe host hooks must not come back
    for bad in ("onAttachedToWindow", "onScrollChanged"):
        check(f"no global {bad} hook", bad not in dex)

    # white-label tokens must all be baked
    leftovers = re.findall(r"__[A-Z0-9_]+__", dex)
    check("no unbaked __TOKEN__ in dex", not leftovers, str(set(leftovers)))

    # credits present
    check("developer credit baked", "@DRKMUQEETT" in dex)

    print()
    if fails:
        print(f"{len(fails)} check(s) failed")
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "dist/nyx-1.0.0-obsidian.apk"))
