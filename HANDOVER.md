# HANDOVER — OTC Bot signal terminal

Paste this file (or its raw link) into a new AI chat to continue the work with full context.

- **Live site:** https://otc-signal-terminal.vercel.app
  (`/scanner`, `/generator`, `/terminal`, `/checkout`, `/terms`)
- **Repo:** https://github.com/muqeetdag/DRK (public) — branch **`arena/14218c51-drk`**
- **Source zip:** https://raw.githubusercontent.com/muqeetdag/DRK/arena/14218c51-drk/otc-signal-terminal-source.zip
- **Vercel:** project `otc-signal-terminal` (`prj_xJRDzygvfL6nyMZojFPuzs8Fkmcf`), team `team_JJl3rkuEAQGdG1mrCGWMrHDl`

## What this is

A static OTC/forex/crypto/INR signal terminal: landing page, **OTC Bot** grid
(`/terminal`), **Signals / Beast Mode** (`/generator`), **Live Scanner** (`/scanner`),
checkout and terms. No framework, no backend — one Node build script produces a
`static/` folder, and GitHub's CDN (jsDelivr) serves it to a tiny Vercel boot shell.

```
otc-terminal/
├── public/            # app source (html + assets/*.js + assets/*.css)
├── shell/             # Vercel boot shell: index.html + shell.js + 5 alias pages
├── build-static.js    # public/ -> static/ (also emits robots.txt, vercel.json)
├── server.js          # local static server
docs/                  # built mirror of static/ that the shell loads from the CDN
```

Key files: `public/assets/clock.js` (shared market clock), `market.js` (data +
5-factor confidence engine), `terminal.js`, `scanner.js`, `generator.js`,
`landing.js`, `scanner.css`, `terminal.css`, `generator.css`.

## Non-negotiable product rules (from the user)

1. Entry times, countdowns and chart data must be **real** — never the ticking wall clock.
2. Stamps are **IST**, **minute precision, no seconds** (`12:49 PM`). Seconds are allowed
   only inside the small `mm:ss` countdown chip.
3. Pairs sit on a **4-candle rotation** (`ROTATION` in `clock.js`): ~a quarter are live at
   any moment, the rest show a genuine future candle. Stamps must differ across cards —
   never one identical value on all 118 cards.
4. Scanner and Signals share the same clock, so the same pair shows the same schedule
   on both pages.
5. **Beast mode** scans everything but lists only **99%+ confidence** setups
   (max 4; falls back to top 2 so the grid is never empty).
6. **Trend** = zig-zag arrow: green up (Uptrend), red down (Downtrend), amber (Sideways).
   A live signal must never contradict its own direction (BUY can't show Downtrend).
7. Charts must be **real TradingView** (`FX:EURUSD`, `OANDA:XAUUSD`, `BINANCE:BTCUSDT`,
   `FX_IDC:USDINR`); OTC pairs have no public feed so they keep the engine canvas chart.
8. Pretty URLs must work (`/scanner`, `/generator`, `/terminal`, `/checkout`, `/terms`).
9. Scanner cards stay compact: symbol, WAIT/BUY/SELL + %, countdown chip, Trend + one
   time stamp. No price row, no progress bar.
10. Ask before large changes; the user approves direction first.

## Build & deploy chain (follow exactly)

```bash
cd otc-terminal && node build-static.js            # -> static/ (22 files)
cd .. && rm -rf docs && cp -r otc-terminal/static docs && touch docs/.nojekyll
git add -A && git commit -m "..." && git push origin HEAD      # commit A
# put commit A's SHA into otc-terminal/shell/index.html  (var SHA = '...')
git add -A && git commit -m "Pin shell to ..." && git push origin HEAD   # commit B
```

Then create a **Vercel production deployment with 6 static files**:
`index.html`, `scanner.html`, `generator.html`, `terminal.html`, `checkout.html`,
`terms.html` (all from `otc-terminal/shell/`). Verify with
`get_deployment` → `ready_state: READY`, `readySubstate: PROMOTED`.

Notes that cost time before:
- Vercel file paths are literal: `/tvtest.html` 404s, `/tvtest` works. There is no
  `vercel.json` in the deployment — do not add one.
- The shell is versioned: `shell/index.html` sets `window.OTC_SHELL_SHA` and loads
  `docs/assets/shell.js` from that SHA (jsDelivr, raw.githubusercontent fallback).
  Every content change needs a new SHA pin, otherwise the site serves the old build.
- `docs/` must be rebuilt and committed before the SHA pin commit.
- Verify the deployed `index.html` by comparing `sha1sum otc-terminal/shell/index.html`
  with the Vercel file listing `uid`.
- jsDelivr can 404 a brand-new commit for a minute; retry with a cache-busting query.

## Current state (branch tip `60eee22`)

Recent work, newest first:
- `60eee22` source archive zip
- `36c3837` pin shell to minute-stamp build
- `6d17b14` minute-precision stamps on the 4-candle rotation, Beast 99 %+ only,
  zig-zag trend arrows, price row removed from OTC Bot cards
- `2b…`-era commits: seconds stamps (later reverted), Trend on `/terminal`, shell split

Latest Vercel deployment: `dpl_3D5pLeaXPknT2YYFPkkBXsqTBxN1` (READY / PROMOTED).

Keys: Beast/Signals `OXIDATIVE`, trial `DEMO-KEY-2025`, 50 signals per session
(`otc.gen.used`), history `otc.gen.history`, flags `otc.token`, `otc.plan`, `otc.beast`.

## Testing without internet

The sandbox/CI has **no outbound internet** (TradingView/CDN calls fail — that is
expected, ignore `ERR_CONNECTION_CLOSED`). Test with Playwright against a local
static server on `:8090` and mocked feeds; check `overflow === 0`, card counts
(118 scanner cards, 41 OTC cards) and that stamps differ across cards.

## Open items / likely next asks

- Beast mode also on the Signals page (currently scanner only).
- User may ask for the price row back, or a 24-hour clock format.
- Deploy is manual (files via API). Wiring the Vercel project to this branch in git
  would remove the SHA-pin dance.
