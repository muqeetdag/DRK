#!/usr/bin/env python3
"""Off-device harness for the DRKMUQEET dashboard.

Builds a begonia-shaped stub of /sys, shims `settings` and `getprop`, then
serves webroot/ and bridges /cgi-bin/control to the real `drk` script. Profile
buttons actually write to the stub tree, so `drk audit` reflects them.

Nothing here ships in the module zip. Run:  python3 devtools/preview.py
"""

from __future__ import annotations

import json
import os
import shutil
import stat
import subprocess
import sys
import tempfile
import threading
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

REPO = Path(__file__).resolve().parent.parent
MODULE = REPO / "DRKMUQEET"
DRK = MODULE / "system" / "bin" / "drk"
WEBROOT = MODULE / "webroot"
HOST, PORT = "0.0.0.0", 8080

GOVERNORS = "performance schedutil powersave"


def build_stub(root: Path) -> Path:
    sysroot = root / "sys"
    state = root / "state"

    for policy, governor in (("policy0", "schedutil"), ("policy1", "schedutil")):
        d = sysroot / "devices/system/cpu/cpufreq" / policy
        d.mkdir(parents=True, exist_ok=True)
        (d / "scaling_governor").write_text(governor)
        (d / "scaling_available_governors").write_text(GOVERNORS)

    # sda is deliberately read-only: the audit must surface it, not skip it.
    for dev, ahead, writable in (("mmcblk0", "128", True), ("sda", "128", False)):
        q = sysroot / "block" / dev / "queue"
        q.mkdir(parents=True, exist_ok=True)
        node = q / "read_ahead_kb"
        node.write_text(ahead)
        if not writable:
            node.chmod(0o444)

    for zone, kind, milli in (("thermal_zone0", "cpu-0-0-usr", 38_400),
                              ("thermal_zone1", "battery", 29_100),
                              ("thermal_zone2", "skin-msm-therm-usr", 33_700)):
        z = sysroot / "class/thermal" / zone
        z.mkdir(parents=True, exist_ok=True)
        (z / "type").write_text(kind)
        (z / "temp").write_text(str(milli))

    ps = sysroot / "class/power_supply/battery"
    ps.mkdir(parents=True, exist_ok=True)
    (ps / "capacity").write_text("74")

    state.mkdir(parents=True, exist_ok=True)
    # No profile file: this is the fresh-install state, where `drk boot` must
    # leave the kernel exactly as it came up.
    return sysroot


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

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEBROOT), **kwargs)

    def log_message(self, fmt, *args):
        sys.stderr.write("  · " + fmt % args + "\n")

    def do_GET(self):
        parsed = urlparse(self.path)
        if not parsed.path.startswith("/cgi-bin/control"):
            return super().do_GET()

        action = (parse_qs(parsed.query).get("action") or ["stats"])[0]
        # Mirror system/bin/drk-web exactly: profile actions answer with a fixed
        # JSON body and their own stdout is discarded (`settings put` prints "Success").
        replies = {"performance": "Performance profile applied",
                   "balanced": "Balanced profile applied",
                   "reset": "Defaults restored"}
        argv = ["sh", str(DRK), *(["audit", "--json"] if action == "audit" else [action])]
        try:
            proc = subprocess.run(argv, capture_output=True, text=True,
                                  env=self.env, timeout=20)
            if action in replies:
                body = json.dumps({"ok": proc.returncode == 0, "message": replies[action]})
            else:
                body = proc.stdout
            code = 200
            if proc.returncode != 0:
                body = json.dumps({"ok": False,
                                   "message": proc.stderr.strip() or "drk failed",
                                   "action": action})
        except Exception as exc:  # surfaced, never swallowed
            body, code = json.dumps({"ok": False, "message": str(exc)}), 500

        payload = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


def main() -> int:
    if not DRK.exists():
        print(f"missing {DRK}", file=sys.stderr)
        return 1

    root = Path(tempfile.mkdtemp(prefix="drkmuqeet-preview-"))
    sysroot = build_stub(root)
    shims = build_shims(root, root / "state")

    env = dict(os.environ)
    env.update({
        "DRK_SYSROOT": str(sysroot),
        "DRK_STATE": str(root / "state"),
        "PATH": f"{shims}:{env.get('PATH', '')}",
    })
    Handler.env = env

    print(f"stub sysroot : {sysroot}")
    print(f"state        : {root / 'state'}")
    print(f"shims        : {shims}")
    print(f"serving      : {WEBROOT} on http://{HOST}:{PORT}")
    print("ctrl-c to stop; the stub tree is removed on exit\n")

    httpd = ThreadingHTTPServer((HOST, PORT), Handler)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nshutting down")
    finally:
        httpd.server_close()
        shutil.rmtree(root, ignore_errors=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
