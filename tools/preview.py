#!/usr/bin/env python3
"""Preview the DRKMUQEET dashboard on a desktop browser.

Serves webroot/ plus a mock of the CGI bridge with canned responses, so the UI can be
checked without flashing the module. The real dashboard is served by busybox httpd on
the phone at 127.0.0.1:8080; this is a development stand-in only.

    python3 tools/preview.py [port]
"""

import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

WEBROOT = Path(__file__).resolve().parent.parent / "DRKMUQEET" / "webroot"

STATE = {"enabled": True, "forgotten": set()}

NEARBY = [
    ("HomeNet_5G", "3c:37:86:aa:bb:cc", -52, 5745, "WPA3"),
    ("Neighbour_2G", "12:34:56:78:9a:bd", -45, 2437, "WPA2"),
    ("OpenCafe", "aa:bb:cc:dd:ee:ff", -84, 2412, "Open"),
    ("Campus_Guest", "9c:1c:12:aa:04:11", -61, 5180, "WPA2"),
    ("IOT-Bulbs", "50:02:91:11:22:33", -73, 2462, "WPA2"),
    ("Hotel_Lobby", "de:ad:be:ef:00:01", -88, 2412, "Open"),
]

SAVED = {0: "HomeNet_5G", 3: "Campus_Guest", 7: "Neighbour_2G"}


def band(freq):
    return "2.4 GHz" if freq < 2500 else ("5 GHz" if freq < 5925 else "6 GHz")


def quality(rssi):
    return max(0, min(100, (rssi + 100) * 2))


def wifi_status():
    enabled = STATE["enabled"]
    return {
        "ok": True,
        "enabled": "on" if enabled else "off",
        "iface": "wlan0",
        "ssid": "HomeNet_5G" if enabled else "unknown",
        "state": "COMPLETED" if enabled else "DISCONNECTED",
        "ip": "192.168.1.42" if enabled else "",
        "gateway": "192.168.1.1" if enabled else "",
        "dns": "8.8.8.8,1.1.1.1" if enabled else "",
        "mac": "02:00:00:00:00:00",
        "security": "WPA/WPA2-PSK",
        "rssi": -58 if enabled else None,
        "quality": quality(-58) if enabled else -1,
        "band": "5 GHz" if enabled else "?",
        "frequency": 5745 if enabled else None,
        "link_speed": 433 if enabled else None,
    }


def respond(action):
    if action in ("stats", ""):
        return {"profile": "balanced", "battery": "78", "temperature": "37200", "uptime": "18394"}
    if action == "performance":
        return {"ok": True, "message": "Performance profile applied"}
    if action == "balanced":
        return {"ok": True, "message": "Balanced profile applied"}
    if action == "reset":
        return {"ok": True, "message": "Defaults restored"}
    if action == "wifi-status":
        return wifi_status()
    if action in ("wifi-toggle", "wifi-on", "wifi-off"):
        STATE["enabled"] = action != "wifi-off" and (action == "wifi-on" or not STATE["enabled"])
        return wifi_status()
    if action == "wifi-scan":
        nets = [
            {
                "ssid": ssid if STATE["enabled"] else ssid + " (radio off)",
                "bssid": bssid,
                "rssi": rssi,
                "quality": quality(rssi),
                "band": band(freq),
                "frequency": freq,
                "security": sec,
            }
            for ssid, bssid, rssi, freq, sec in NEARBY
        ]
        return {"ok": True, "count": len(nets), "networks": nets}
    if action == "wifi-saved":
        nets = [{"id": i, "ssid": s} for i, s in SAVED.items() if i not in STATE["forgotten"]]
        return {"ok": True, "count": len(nets), "networks": nets}
    return {"ok": False, "message": "Unsupported action"}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        url = urlparse(self.path)
        query = parse_qs(url.query)
        if url.path == "/cgi-bin/control":
            action = (query.get("action") or [""])[0]
            if action == "wifi-forget" and (query.get("id") or [""])[0].isdigit():
                STATE["forgotten"].add(int(query["id"][0]))
                payload = {"ok": True, "message": f"Forgot saved network {query['id'][0]}"}
            else:
                payload = respond(action)
            body = json.dumps(payload).encode()
            ctype = "application/json"
        else:
            name = "index.html" if url.path in ("/", "") else url.path.lstrip("/")
            path = (WEBROOT / name).resolve()
            if not str(path).startswith(str(WEBROOT)) or not path.is_file():
                self.send_error(404, "not found")
                return
            body = path.read_bytes()
            ctype = {
                ".html": "text/html; charset=utf-8",
                ".css": "text/css; charset=utf-8",
                ".js": "text/javascript; charset=utf-8",
                ".svg": "image/svg+xml",
            }.get(path.suffix, "application/octet-stream")
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (self.address_string(), fmt % args))


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    print(f"DRKMUQEET preview on http://0.0.0.0:{port}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
