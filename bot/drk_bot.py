#!/usr/bin/env python3
"""DRK EXIF Bot — the DRK EXIF Viewer app, living inside Telegram.

Menu like the app (/start /help /about + a persistent reply keyboard), and the
core loop: send a photo (as Photo or as an image Document) → the bot downloads
it, extracts ALL EXIF metadata, and replies with:

    📍 GPS coordinates (decimal + DMS + altitude)
    📍 a native Telegram location pin (live map in chat)
    🗺 a direct Google Maps link (inline button)
    📱 device make/model/software   🕐 time taken
    📷 ISO / ƒ / exposure / focal / flash / orientation
    🖼 dimensions + file size       📄 full raw EXIF dump (as .txt)

No third-party networking: stdlib urllib only. The only pip dependency is
Pillow (EXIF parsing).

Run modes:
    --once      process pending updates and exit (GitHub Actions cron runner)
    --poll      long-poll forever (any always-on host / Termux)

Config via env:
    TELEGRAM_BOT_TOKEN   (required — never hardcode it here!)

State: the getUpdates offset is kept in a small text file so --once runs can
resume (committed by the Actions workflow). Run exactly ONE consumer of the
bot's updates at a time.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys
import time
import urllib.parse
import urllib.request

API = "https://api.telegram.org/bot{token}/{method}"
FILE_API = "https://api.telegram.org/file/bot{token}/{path}"

COMMANDS = [
    {"command": "start", "description": "🛡 Open the evidence desk"},
    {"command": "help", "description": "❓ How to extract EXIF"},
    {"command": "about", "description": "ℹ️ About DRK bot"},
]
MENU_KEYBOARD = {
    "keyboard": [[{"text": "📷 Upload photo"}], [{"text": "ℹ️ Help"}]],
    "resize_keyboard": True,
}

HELP_TEXT = (
    "🛡 DRK · EXIF Bot\n"
    "\n"
    "Send me any photo (or an image file as a document) and I'll report:\n"
    "📍 where it was taken — decimal + DMS, a live map pin right here in chat,\n"
    "    and a direct Google Maps link\n"
    "📱 the device that shot it, 🕐 when, 📷 camera settings\n"
    "🖼 dimensions + file size, and 📄 the complete raw EXIF dump as a file\n"
    "\n"
    "Privacy: your photo is read by the bot worker and forgotten — nothing is\n"
    "stored or shared. Tap 📷 Upload photo below to begin."
)

ABOUT_TEXT = (
    "DRK EXIF Bot — Telegram build of the DRK EXIF Viewer app "
    "(com.drk.exifviewer, github.com/muqeetdag/DRK). "
    "EXIF read with Pillow; Google Maps link is key-less. Knight out. 🫡"
)


# --------------------------------------------------------------------------- #
# Bot API plumbing
# --------------------------------------------------------------------------- #

class Bot:
    def __init__(self, token: str, api_base: str | None = None,
                 file_api_base: str | None = None, http_timeout: int = 40):
        self.token = token
        self.api_base = api_base or API
        self.file_api_base = file_api_base or FILE_API
        self.http_timeout = http_timeout

    def call(self, method: str, **fields):
        """GET-style call (works through URL relays too)."""
        url = self.api_base.format(token=self.token, method=method)
        if fields:
            url += "?" + urllib.parse.urlencode(fields)
        req = urllib.request.Request(url, headers={"User-Agent": "drk-exif-bot/1.0"})
        with urllib.request.urlopen(req, timeout=self.http_timeout) as r:
            payload = json.loads(r.read().decode("utf-8", "replace"))
        if not payload.get("ok"):
            raise RuntimeError(f"{method} failed: {payload}")
        return payload["result"]

    def post(self, method: str, **fields):
        """Form POST call."""
        url = self.api_base.format(token=self.token, method=method)
        body = urllib.parse.urlencode(fields).encode()
        req = urllib.request.Request(url, data=body,
                                     headers={"User-Agent": "drk-exif-bot/1.0"})
        with urllib.request.urlopen(req, timeout=self.http_timeout) as r:
            payload = json.loads(r.read().decode("utf-8", "replace"))
        if not payload.get("ok"):
            raise RuntimeError(f"{method} failed: {payload}")
        return payload["result"]

    def post_document(self, chat_id: int, filename: str, content: bytes,
                      caption: str = ""):
        """multipart/form-data sendDocument (stdlib-only)."""
        boundary = "----drkboundary7f3a1c"
        parts = []
        for name, value in (("chat_id", str(chat_id)), ("caption", caption)):
            parts.append(
                f"--{boundary}\r\nContent-Disposition: form-data; "
                f'name="{name}"\r\n\r\n{value}\r\n'.encode())
        parts.append(
            f"--{boundary}\r\nContent-Disposition: form-data; "
            f'name="document"; filename="{filename}"\r\n'
            f"Content-Type: text/plain\r\n\r\n".encode()
            + content + b"\r\n")
        parts.append(f"--{boundary}--\r\n".encode())
        body = b"".join(parts)
        url = self.api_base.format(token=self.token, method="sendDocument")
        req = urllib.request.Request(
            url, data=body,
            headers={"Content-Type": f"multipart/form-data; boundary={boundary}",
                     "User-Agent": "drk-exif-bot/1.0"})
        with urllib.request.urlopen(req, timeout=self.http_timeout) as r:
            payload = json.loads(r.read().decode("utf-8", "replace"))
        if not payload.get("ok"):
            raise RuntimeError(f"sendDocument failed: {payload}")
        return payload["result"]

    def send_text(self, chat_id: int, text: str, inline_url: str | None = None,
                  inline_text: str = "🗺 Open in Google Maps", menu: bool = False):
        fields = {"chat_id": chat_id, "text": text[:4096],
                  "disable_web_page_preview": "true"}
        if inline_url:
            fields["reply_markup"] = json.dumps(
                {"inline_keyboard": [[{"text": inline_text, "url": inline_url}]]})
        elif menu:
            fields["reply_markup"] = json.dumps(MENU_KEYBOARD)
        return self.call("sendMessage", **fields)

    def send_location(self, chat_id: int, lat: float, lon: float):
        return self.call("sendLocation", chat_id=chat_id,
                         latitude=f"{lat:.7f}", longitude=f"{lon:.7f}")

    def download(self, file_id: str) -> bytes:
        info = self.call("getFile", file_id=file_id)
        url = self.file_api_base.format(token=self.token, path=info["file_path"])
        req = urllib.request.Request(url, headers={"User-Agent": "drk-exif-bot/1.0"})
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.read()


# --------------------------------------------------------------------------- #
# EXIF extraction (app-parity: the same headline fields + full raw dump)
# --------------------------------------------------------------------------- #

def _rat(v, nd=2):
    try:
        return float(v)
    except Exception:
        try:
            return float(v.numerator) / float(v.denominator or 1)
        except Exception:
            return None


def _gps_dms_to_deg(dms, ref):
    try:
        d, m, s = (_rat(dms[0]) or 0, _rat(dms[1]) or 0, _rat(dms[2]) or 0)
    except Exception:
        return None
    deg = d + m / 60.0 + s / 3600.0
    if ref in ("S", "W"):
        deg = -deg
    return deg


def fmt_dms(value, is_lat):
    hemi = ("N" if value >= 0 else "S") if is_lat else ("E" if value >= 0 else "W")
    v = abs(value)
    d = int(v)
    mf = (v - d) * 60
    m = int(mf)
    s = (mf - m) * 60
    return f"{d}°{m:02d}'{s:05.2f}\"{hemi}"


def extract_exif(data: bytes):
    """Returns dict with headline fields + raw tag list (like ExifExtractor)."""
    from PIL import Image, ExifTags

    im = Image.open(io.BytesIO(data))
    im.load()
    base = im.getexif()
    TAGS = ExifTags.TAGS
    GPSTAGS = ExifTags.GPSTAGS

    def sval(v):
        if isinstance(v, bytes):
            if len(v) > 64 or any(b < 9 or (13 < b < 32) for b in v[:64]):
                return f"<{len(v)} bytes>"
            try:
                return v.decode("ascii", "replace").strip("\x00")
            except Exception:
                return f"<{len(v)} bytes>"
        return str(v).strip("\x00").strip()

    def ifd_dict(ifd, names):
        out = {}
        for k, v in dict(ifd).items():
            if isinstance(v, dict):
                continue
            name = names.get(k, TAGS.get(k, str(k)))
            out[name] = sval(v)
        return out

    raw = {}
    # IFD pointer offsets (ExifOffset/GPSInfo/InteropOffset) are parser plumbing,
    # not evidence — keep them out of the raw dump.
    raw.update({k: v for k, v in ifd_dict(base, TAGS).items()
                if k not in ("ExifOffset", "GPSInfo", "InteropOffset")})
    exif_ifd = base.get_ifd(0x8769)    # ExifIFD
    gps_ifd = base.get_ifd(0x8825)     # GPSInfo
    raw.update(ifd_dict(exif_ifd, TAGS))
    for k, v in dict(gps_ifd).items():
        if not isinstance(v, dict):
            raw.setdefault(f"GPS.{GPSTAGS.get(k, k)}", sval(v))

    out = {"raw": raw, "tags": len(raw),
           "width": im.size[0], "height": im.size[1],
           "bytes": len(data), "format": im.format,
           "make": None, "model": None, "software": None, "datetime": None,
           "iso": None, "fnumber": None, "exposure": None, "focal": None,
           "flash": None, "orientation": None,
           "lat": None, "lon": None, "alt": None, "has_exif": bool(raw)}

    out["make"] = sval(base.get(271)) if base.get(271) else None
    out["model"] = sval(base.get(272)) if base.get(272) else None
    out["software"] = sval(base.get(305)) if base.get(305) else None
    out["datetime"] = sval(exif_ifd.get(36867) or exif_ifd.get(36868) or base.get(306)) \
        if (exif_ifd.get(36867) or exif_ifd.get(36868) or base.get(306)) else None

    iso = exif_ifd.get(34855)
    out["iso"] = f"ISO {iso}" if iso else None
    fn = _rat(exif_ifd.get(33437))
    out["fnumber"] = f"ƒ/{fn:g}" if fn else None
    et = exif_ifd.get(33434)
    if et is not None:
        n, d = (et.numerator, et.denominator) if hasattr(et, "numerator") else (None, None)
        if n and d and n <= d and n > 0:
            out["exposure"] = f"{n}/{d}s"
        else:
            v = _rat(et)
            out["exposure"] = f"{v:g}s" if v else None
    fl = _rat(exif_ifd.get(37386))
    out["focal"] = f"{fl:g}mm" if fl else None
    flash = exif_ifd.get(37385)
    if flash is not None:
        out["flash"] = "flash fired" if (flash & 1) else "no flash"
    orientations = {1: "normal", 3: "rotated 180°", 6: "rotated 90°", 8: "rotated 270°"}
    ori = base.get(274)
    if ori in orientations:
        out["orientation"] = orientations[ori]

    lat = _gps_dms_to_deg(gps_ifd.get(2), gps_ifd.get(1)) if gps_ifd.get(2) else None
    lon = _gps_dms_to_deg(gps_ifd.get(4), gps_ifd.get(3)) if gps_ifd.get(4) else None
    out["lat"] = lat
    out["lon"] = lon
    alt = _rat(gps_ifd.get(6))
    if alt is not None and gps_ifd.get(5) == 1:
        alt = -alt
    out["alt"] = alt
    out["has_exif"] = bool(raw)
    return out


def format_report(d: dict) -> tuple[str, str | None]:
    """Returns (message_text, maps_url_or_None). App-mirrored layout."""
    if not d["has_exif"]:
        return "✖ No EXIF data found in this image.", None
    lines = [f"✔ EXIF parsed — {d['tags']} tags captured"]
    maps_url = None
    if d["lat"] is not None and d["lon"] is not None:
        loc = f"{d['lat']:.6f}, {d['lon']:.6f}\n" \
              f"{fmt_dms(d['lat'], True)}  {fmt_dms(d['lon'], False)}"
        if d["alt"] is not None:
            loc += f"  ·  alt {d['alt']:.0f} m"
        lines.append(f"📍 Location: {loc}")
        maps_url = f"https://www.google.com/maps?q={d['lat']:.6f},{d['lon']:.6f}"
        lines.append(f"🗺 Google Maps: {maps_url}")
    else:
        lines.append("📍 Location: not embedded in file")
    device = " ".join(x for x in (d["make"], d["model"]) if x).strip() or "unknown"
    if d["software"]:
        device += f"  ·  sw: {d['software']}"
    lines.append(f"📱 Device: {device}")
    lines.append(f"🕐 Taken: {d['datetime'] or 'unknown'}")
    cam = "   ".join(x for x in (d["iso"], d["fnumber"], d["exposure"], d["focal"]) if x)
    extras = "  ·  ".join(x for x in (d["orientation"], d["flash"]) if x)
    cam = cam or "unknown"
    if extras:
        cam = f"{cam}\n{extras}" if cam != "unknown" else extras
    lines.append(f"📷 Camera: {cam}")
    mb = d["bytes"] / 1048576.0
    size = f"{mb:.1f} MB" if mb >= 0.1 else f"{d['bytes'] / 1024.0:.1f} KB"
    lines.append(f"🖼 {d['width']}×{d['height']}px ({d['format']})   💾 {size}")
    return "\n".join(lines), maps_url


def build_raw_dump(d: dict) -> bytes:
    if not d["raw"]:
        return b"(no EXIF tags present)"
    pad = min(max(len(k) for k in d["raw"]), 34)
    text = "\n".join(k.ljust(pad) + "  " + v for k, v in d["raw"].items())
    return text.encode("utf-8", "replace")


# --------------------------------------------------------------------------- #
# Update handling
# --------------------------------------------------------------------------- #

def ensure_menu(bot: Bot):
    try:
        bot.call("setMyCommands", commands=json.dumps(COMMANDS),
                 scope=json.dumps({"type": "all_private_chats"}))
        bot.call("setChatMenuButton", menu_button=json.dumps({"type": "commands"}))
    except Exception as exc:  # never let menu setup kill the run
        print(f"[warn] menu setup: {exc}")


def handle_message(bot: Bot, msg: dict):
    chat_id = msg["chat"]["id"]
    text = (msg.get("text") or "").strip()

    if text.startswith("/start"):
        bot.send_text(chat_id, "🛡 Evidence desk open, chief.\n" + HELP_TEXT, menu=True)
        return
    if text.startswith("/help"):
        bot.send_text(chat_id, HELP_TEXT, menu=True)
        return
    if text.startswith("/about"):
        bot.send_text(chat_id, ABOUT_TEXT, menu=True)
        return

    file_id = None
    if msg.get("photo"):
        file_id = msg["photo"][-1]["file_id"]          # largest size
        note = None
    elif msg.get("document") and str(msg["document"].get("mime_type", "")).startswith("image/"):
        file_id = msg["document"]["file_id"]
        note = None
    else:
        if text or msg:
            bot.send_text(chat_id,
                          "📷 Send me a *photo* (or an image file as a document) "
                          "and I'll extract its EXIF metadata + Google Maps location.",
                          menu=True)
        return

    bot.send_text(chat_id, "⏳ Parsing metadata…")
    data = bot.download(file_id)
    try:
        d = extract_exif(data)
    except Exception as exc:
        bot.send_text(chat_id,
                      f"✖ Couldn't read EXIF from this file ({type(exc).__name__}). "
                      "Try the original JPEG straight from the camera.", menu=True)
        return

    report, maps_url = format_report(d)
    bot.send_text(chat_id, report, inline_url=maps_url)

    if d["lat"] is not None and d["lon"] is not None:
        try:
            bot.send_location(chat_id, d["lat"], d["lon"])
        except Exception as exc:
            print(f"[warn] sendLocation: {exc}")

    raw = build_raw_dump(d)
    if len(raw) <= 3500:
        bot.send_text(chat_id, "📄 Raw EXIF:\n" + raw.decode("utf-8", "replace"))
    else:
        try:
            bot.post_document(chat_id, "raw-exif.txt", raw,
                              caption="📄 Full raw EXIF dump")
        except Exception as exc:
            print(f"[warn] raw dump send failed, truncating: {exc}")
            bot.send_text(chat_id, "📄 Raw EXIF (truncated):\n"
                          + raw.decode("utf-8", "replace")[:3500])

    if msg.get("photo"):
        bot.send_text(chat_id,
                      "🔍 Tip: Telegram re-compresses photos and strips most "
                      "EXIF on the way. Send the same image as a File "
                      "(paperclip → File) and I'll dissect the original "
                      "bytes — every tag, untouched.", menu=True)


# --------------------------------------------------------------------------- #
# Runner
# --------------------------------------------------------------------------- #

def read_offset(path: str) -> int:
    try:
        return int(open(path).read().strip())
    except Exception:
        return 0


def write_offset(path: str, offset: int):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w") as f:
        f.write(str(offset))


def main() -> int:
    ap = argparse.ArgumentParser(description="DRK EXIF Bot worker")
    ap.add_argument("--once", action="store_true", help="process pending updates and exit")
    ap.add_argument("--poll", action="store_true", help="long-poll forever")
    ap.add_argument("--api-base", help="override Bot API base (relay)")
    ap.add_argument("--file-api-base", help="override Bot API file base (relay)")
    ap.add_argument("--state-file", default="bot/state/offset.txt")
    args = ap.parse_args()

    token = os.environ.get("TELEGRAM_BOT_TOKEN")
    if not token:
        sys.exit("TELEGRAM_BOT_TOKEN env var is required")

    bot = Bot(token, api_base=args.api_base, file_api_base=args.file_api_base)
    me = bot.call("getMe")
    print(f"bot: @{me.get('username')} (id {me.get('id')})")
    ensure_menu(bot)

    offset = read_offset(args.state_file)
    print(f"state: offset={offset} ({args.state_file})")

    def one_batch(timeout: int) -> int:
        nonlocal offset
        updates = bot.call("getUpdates", offset=offset, limit=100, timeout=timeout)
        n = 0
        for upd in updates:
            offset = max(offset, upd["update_id"] + 1)
            msg = upd.get("message") or upd.get("edited_message")
            if not msg:
                continue
            try:
                handle_message(bot, msg)
            except Exception as exc:
                print(f"[error] update {upd['update_id']}: {exc}")
                try:
                    bot.send_text(msg["chat"]["id"],
                                  "⚠ Something went sideways reading that file. Try again, chief.")
                except Exception:
                    pass
            n += 1
        write_offset(args.state_file, offset)
        return n

    if args.poll:
        print("long-polling (Ctrl-C to stop)…")
        while True:
            try:
                one_batch(timeout=25)
            except Exception as exc:
                print(f"[warn] poll error: {exc}; retrying in 5s")
                time.sleep(5)
        return 0

    handled = one_batch(timeout=0)
    print(f"done: {handled} message(s) processed; new offset={offset}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
