#!/usr/bin/env python3
"""Send a file to whoever last messaged a Telegram bot — over MTProto.

Why MTProto? Plain-bot-API delivery needs nothing but curl, but this script
keeps working in environments where only Telegram's raw TCP endpoints are
reachable (the MTProto datacenters listen on 443 without TLS SNI).

A bot can never message someone first: at least one DM must already exist.
If nobody has messaged the bot yet, the script waits (default 15 minutes)
for a live incoming message, then delivers the file there.

    pip install telethon
    TELEGRAM_BOT_TOKEN=123:abc python3 tools/telegram-send.py FILE [CAPTION]

The public Telegram Desktop API credentials below are the standard, widely
documented pair (see opentele docs / Telethon community) — they identify the
*client application*, the bot token still scopes everything to *your bot*.
"""
import asyncio
import os
import sys

from telethon import TelegramClient, events

API_ID = 2040
API_HASH = "b18441a1ff607e10a989891a5462e627"  # Telegram Desktop (public)

WAIT_SECONDS = int(os.environ.get("TG_WAIT_SECONDS", "900"))


async def main() -> None:
    token = os.environ["TELEGRAM_BOT_TOKEN"]
    path = sys.argv[1]
    caption = sys.argv[2] if len(sys.argv) > 2 else None

    client = TelegramClient("drk-bot-session", API_ID, API_HASH)
    await client.start(bot_token=token)
    me = await client.get_me()
    print(f"authenticated bot: @{me.username} (id {me.id})")

    chat = None
    try:
        async for dialog in client.iter_dialogs(limit=20):
            if dialog.is_user:
                chat = dialog.entity
                break
    except Exception as exc:  # some bot sessions disallow dialogs
        print(f"dialog scan unavailable: {exc}")

    if chat is None:
        print(f"no dialogs yet — waiting up to {WAIT_SECONDS}s for a DM to the bot…")
        ready = asyncio.Event()
        holder = {}

        @client.on(events.NewMessage(incoming=True))
        async def _grab(event):
            holder["chat"] = await event.get_chat()
            ready.set()

        try:
            await asyncio.wait_for(ready.wait(), WAIT_SECONDS)
        except asyncio.TimeoutError:
            await client.disconnect()
            sys.exit("TIMEOUT: nobody messaged the bot in time")
        chat = holder["chat"]

    label = getattr(chat, "username", None) or getattr(chat, "id", "?")
    print(f"delivering to chat: {label}")
    await client.send_file(chat, path, caption=caption)
    print("SENT ✔")
    await client.disconnect()


if __name__ == "__main__":
    asyncio.run(main())
