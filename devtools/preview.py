#!/usr/bin/env python3
"""Off-device harness for the DRKMUQEET module.

Builds a begonia-shaped stub of /sys, shims `settings` and `getprop`, serves
webroot/, and bridges /cgi-bin/control to the real `drk` and `drk-web`
contract. The thermal guard really runs against the stub, and /dev/* endpoints
let you drive the sensor values so you can watch it engage and release.

Nothing here ships in the module ZIP. Run:  python3 devtools/preview.py
"""

from __future__ import annotations

import json
import os
import signal
import shutil
import stat
import subprocess
import sys
import tempfile
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

REPO = Path(__file__).resolve().parent.parent
MODULE = REPO / "DRKMUQEET"
DRK = MODULE / "system" / "bin" / "drk"
WEBROOT = MODULE / "webroot"
HOST, PORT = "0.0.0.0", 8080

GOVERNORS = "performance schedutil powersave"

# Helio G90T-shaped: six little cores on policy0, two big cores on policy1.
POLICIES = {
    "policy0": dict(min=500_000, hw_max=2_000_000, cpus="0 1 2 3 4 5"),
    "policy1": dict(min=650_000, hw_max=2_050_000, cpus="6 7"),
}

ZONES = {
    "thermal_zone0": "cpu-0-0-usr",
    "thermal_zone1": "cpu-1-0-usr",
    "thermal_zone2": "skin-msm-therm-usr",
    "thermal_zone3": "battery",
    "thermal_zone4": "soc-0",
}

# Guard thresholds the harness runs with, in millidegrees.
GUARD = dict(soft=43_000, hard=47_000, cool=40_000, interval=2)


def build_stub(root: Path) -> tuple[Path, Path]:
    sysroot = root / "sys"
    state = root / "state"

    for pol, spec in POLICIES.items():
        d = sysroot / "devices/system/cpu/cpufreq" / pol
        d.mkdir(parents=True, exist_ok=True)
        (d / "scaling_governor").write_text("schedutil")
        (d / "scaling_available_governors").write_text(GOVERNORS)
        (d / "scaling_min_freq").write_text(str(spec["min"]))
        (d / "scaling_max_freq").write_text(str(spec["hw_max"]))
        (d / "cpuinfo_min_freq").write_text(str(spec["min"]))
        (d / "cpuinfo_max_freq").write_text(str(spec["hw_max"]))
        (d / "affected_cpus").write_text(spec["cpus"])
        (d / "energy_aware").write_text("1")

    # sda is deliberately read-only: the audit must surface it, not skip it.
    for dev, writable in (("mmcblk0", True), ("sda", False)):
        q = sysroot / "block" / dev / "queue"
        q.mkdir(parents=True, exist_ok=True)
        node = q / "read_ahead_kb"
        node.write_text("128")
        if not writable:
            node.chmod(0o444)

    for zone, kind in ZONES.items():
        z = sysroot / "class/thermal" / zone
        z.mkdir(parents=True, exist_ok=True)
        (z / "type").write_text(kind)

    ps = sysroot / "class/power_supply/battery"
    ps.mkdir(parents=True, exist_ok=True)
    (ps / "capacity").write_text("74")

    state.mkdir(parents=True, exist_ok=True)
    set_heat(sysroot, 38.4)
    # No profile file: fresh-install state, where `drk boot` must touch nothing.
    return sysroot, state


def set_heat(sysroot: Path, celsius: float) -> None:
    """Drive every thermal zone. Battery sits a few degrees under the SoC."""
    for zone, kind in ZONES.items():
        node = sysroot / "class/thermal" / zone / "temp"
        delta = 3.0 if kind == "battery" else 0.0
        node.write_text(str(int(round((celsius - delta) * 1000))))


def build_shims(root: Path, state: Path) -> Path:
    """`settings` and `getprop` do not exist off-device. Back them with a file."""
    shims = root / "shims"
    shims.mkdir(exist_ok=True)
    store = state / "settings.json"
    if not store.exists():
        store.write_text(json.dumps({
            "window_animation_scale": "1.0",
            "transition_animation_scale": "1.0",
            "animator_duration_scale": "1.0",
        }))

    settings = shims / "settings"
    settings.write_text(
        "#!/usr/bin/env python3\n"
        "import json,sys\n"
        f"store={str(store)!r}\n"
        "a=sys.argv[1:]\n"
        "d=json.load(open(store))\n"
        "if a and a[0]=='get' and len(a)==3:\n"
        "    print(d.get(a[2],'null'))\n"
        "elif a and a[0]=='put' and len(a)==4:\n"
        "    d[a[2]]=a[3]; json.dump(d,open(store,'w')); print('Success')\n"
    )

    getprop = shims / "getprop"
    getprop.write_text(
        "#!/usr/bin/env sh\n"
        '[ "$1" = ro.product.device ] && echo begonia || echo ""\n'
    )

    for f in (settings, getprop):
        f.chmod(f.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    return shims


class Handler(SimpleHTTPRequestHandler):
    env: dict[str, str] = {}
    sysroot: Path = None
    state: Path = None

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEBROOT), **kwargs)

    def log_message(self, fmt, *args):
        sys.stderr.write("  · " + fmt % args + "\n")

    def _json(self, obj, code=200):
        payload = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _drk(self, args, discard_stdout=False, reply=None):
        """Invoke the real script exactly as drk-web does."""
        proc = subprocess.run(["sh", str(DRK), *args], capture_output=True,
                              text=True, env=self.env, timeout=20)
        if proc.returncode != 0:
            return {"ok": False,
                    "message": proc.stderr.strip() or f"drk {' '.join(args)} failed",
                    "exit": proc.returncode}
        if discard_stdout:
            return {"ok": True, "message": reply}
        try:
            return json.loads(proc.stdout)
        except json.JSONDecodeError:
            return {"ok": False, "message": "drk returned non-JSON",
                    "raw": proc.stdout[:400]}

    def do_GET(self):
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)

        if parsed.path.startswith("/dev/"):
            return self.dev(parsed.path.rstrip("/"), query)
        if parsed.path.startswith("/cgi-bin/control"):
            return self.cgi(query)
        return super().do_GET()

    def cgi(self, query):
        action = (query.get("action") or ["stats"])[0]
        mutating = {"performance": "Performance profile applied",
                    "balanced": "Balanced profile applied",
                    "reset": "Defaults restored, thermal guard stopped",
                    "guard": "Thermal guard started",
                    "unguard": "Thermal guard stopped, clocks handed back"}
        if action in mutating:
            return self._json(self._drk([action], True, mutating[action]))
        if action in ("audit", "stats", "guard-status"):
            args = ["audit", "--json"] if action == "audit" else [action]
            return self._json(self._drk(args))
        # Mirrors drk-web's reject branch: literal case matching only.
        return self._json(self._drk([action]))

    def dev(self, path, query):
        """Harness-only controls. These do not exist on the device."""
        if path == "/dev/heat":
            try:
                c = float((query.get("c") or ["38.4"])[0])
            except ValueError:
                return self._json({"ok": False, "message": "c must be a number"}, 400)
            set_heat(self.sysroot, c)
            return self._json({"ok": True, "temp_c": c,
                               "note": "guard polls every "
                                       f"{GUARD['interval']}s"})

        if path == "/dev/overclock":
            on = (query.get("on") or ["0"])[0] in ("1", "true", "yes")
            for pol, spec in POLICIES.items():
                node = self.sysroot / "devices/system/cpu/cpufreq" / pol / "scaling_max_freq"
                node.write_text(str(spec["hw_max"] + (300_000 if on else 0)))
            return self._json({"ok": True, "overclocked": on,
                               "note": "simulates another module driving a policy "
                                       "past its hardware ceiling"})

        if path == "/dev/state":
            out = {}
            for name in ("profile", "guard", "guard.pid"):
                f = self.state / name
                out[name] = f.read_text().strip() if f.exists() else None
            out["settings"] = json.loads((self.state / "settings.json").read_text())
            for pol in POLICIES:
                d = self.sysroot / "devices/system/cpu/cpufreq" / pol
                out[pol] = {n: (d / n).read_text().strip()
                            for n in ("scaling_governor", "scaling_max_freq",
                                      "cpuinfo_max_freq") if (d / n).exists()}
            out["temps_c"] = {z: int((self.sysroot / "class/thermal" / z / "temp")
                                     .read_text()) / 1000 for z in ZONES}
            return self._json(out)

        return self._json({
            "ok": True,
            "endpoints": ["/dev/heat?c=44.0", "/dev/overclock?on=1",
                          "/dev/state"],
            "thresholds_c": {k: v / 1000 for k, v in GUARD.items()
                             if k != "interval"},
        })


def main() -> int:
    if not DRK.exists():
        print(f"missing {DRK}", file=sys.stderr)
        return 1

    root = Path(tempfile.mkdtemp(prefix="drkmuqeet-preview-"))
    sysroot, state = build_stub(root)
    shims = build_shims(root, state)

    env = dict(os.environ)
    env.update({
        "DRK_SYSROOT": str(sysroot),
        "DRK_STATE": str(state),
        "DRK_GUARD_SOFT": str(GUARD["soft"]),
        "DRK_GUARD_HARD": str(GUARD["hard"]),
        "DRK_GUARD_COOL": str(GUARD["cool"]),
        "DRK_GUARD_INTERVAL": str(GUARD["interval"]),
        "PATH": f"{shims}:{env.get('PATH', '')}",
    })
    Handler.env = env
    Handler.sysroot = sysroot
    Handler.state = state

    # Start the real thermal guard against the stub.
    subprocess.run(["sh", str(DRK), "guard"], env=env, capture_output=True)
    pidfile = state / "guard.pid"
    guard_pid = int(pidfile.read_text()) if pidfile.exists() else None

    print(f"stub sysroot : {sysroot}")
    print(f"state        : {state}")
    print(f"guard        : pid {guard_pid}, soft {GUARD['soft']/1000}C "
          f"hard {GUARD['hard']/1000}C cool {GUARD['cool']/1000}C "
          f"every {GUARD['interval']}s")
    print(f"serving      : {WEBROOT} on http://{HOST}:{PORT}")
    print("dev controls : /dev/heat?c=48  /dev/overclock?on=1  /dev/state")
    print("ctrl-c to stop; the stub tree is removed on exit\n")

    httpd = ThreadingHTTPServer((HOST, PORT), Handler)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nshutting down")
    finally:
        httpd.server_close()
        if guard_pid:
            try:
                os.kill(guard_pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
        shutil.rmtree(root, ignore_errors=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
