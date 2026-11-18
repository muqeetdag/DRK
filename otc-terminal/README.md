# OTC Bot — Institutional Market Confidence Engine™

A four-market signal terminal and landing site that runs entirely in the browser:
41 OTC pairs, 41 forex pairs, 28 crypto coins and the Indian rupee market, each
scored by a five-factor confidence engine and rendered as the card grid in the
terminal.

**Live:** https://otc-signal-terminal.vercel.app

## Data

| Market | Source | Notes |
| --- | --- | --- |
| Crypto | Binance public REST (`data-api.binance.vision`, `api.binance.com`) | 24h tickers + 1m–15m klines |
| Crypto fallback | CoinGecko public API | prices, 24h change, INR quotes, 30m OHLC |
| Forex | Frankfurter (ECB) timeseries + `currency-api` (jsDelivr) | live spot + 120 days of daily closes |
| Indian market | Crypto feeds × live USD/INR | BTC/INR, ETH/INR … plus USD/INR |
| OTC pairs | local synthetic liquidity model | OTC has no public order book |

No API keys, no backend, no build step. Everything is fetched from the visitor's
browser; if a feed is unreachable the UI falls back to a local model and says so.

## Live URL

**https://otc-signal-terminal.vercel.app** — deployed on Vercel (Hobby), works on
any phone or desktop browser. Pages: `/` landing, `?p=terminal` dashboard,
`?p=scanner` live scanner, `?p=generator` Beast Mode signal generator,
`?p=checkout` upgrade, `?p=terms` legal.

How that deploy works: `shell/index.html` is a tiny (8 KB) bootstrap page hosted
on Vercel. On load it pulls the built app (`docs/`, pinned to a commit SHA) from
jsDelivr (with raw.githubusercontent.com as an automatic fallback), inlines the
CSS and runs the JS, then rewrites the internal links to `?p=` routes. That keeps
the Vercel payload tiny while the app itself stays in this repo.

Rebuild + republish after editing the app:

```bash
node build-static.js                 # public/ -> static/ (server-less build)
rm -rf docs && cp -r static docs && touch docs/.nojekyll
git add docs && git commit -m "Rebuild static site" && git push
# then bump the SHA constant in shell/index.html and redeploy it on Vercel
```

## Market clock (entry time + expiry)

`assets/clock.js` is the single source of truth for time. Every instrument
trades the candle that is open right now: the entry lands a few seconds into
the candle (staggered per pair) and the trade expires when that candle closes.
Because the schedule is derived from the pair id and the clock, the Live
Scanner and the Signals page always show the same entry stamp and the same
remaining seconds for the same instrument. Every stamp is rendered in IST
(`Asia/Kolkata`), so nothing shows the visitor's raw local clock.

Pairs rotate over four candles, so at any moment about a quarter of them are
in a live trade and the rest are waiting on a real future candle — the stamps
differ across cards instead of repeating the current minute. Stamps are
minute-precision IST (`04:32 PM`); the live countdown chip carries the seconds.

Scanner cards read: `Trend` plus one stamp — `Entry · IST 04:32 PM` for a live
trade and `Next entry · IST 04:34 PM` for a stand-by pair, with a small
`mm:ss` chip counting down to the entry or the close. The Signals page shows
`Take entry at Nov 17, 2026, 04:32 PM IST` with a live `ACTIVE · 00:56` badge,
and the OTC Bot grid (`/terminal`) carries a `Trend` row (zig-zag arrow),
`Trade Duration` and an IST `Entry Time` for every card.

Beast mode scans everything but only lists the setups the engine scores at
99%+ — usually two to four pairs, never a full grid.

## Charts

Forex, crypto and INR instruments render the official **TradingView Advanced
Chart** widget (`s3.tradingview.com`), mapped to the real symbol:
`FX:EURUSD`, `OANDA:XAUUSD`, `BINANCE:BTCUSDT`, `FX_IDC:USDINR`. The widget
follows the timeframe pills, uses the dark theme and the IST timezone. OTC
pairs are synthetic and have no public feed, so they keep the engine canvas
chart. If TradingView cannot be reached the panel falls back to the engine
chart automatically and says so in the footer.

## Live Scanner

`/scanner` is the dense scanner grid: every pair as a card with its status
(`WAIT`, or `BUY`/`SELL` with the confidence), the trend read from the
algorithmic-sequence factor (Uptrend / Downtrend / Sideways), the next execution
minute, a copy action and a real-time feed badge. Filters: market chips
(All / OTC / Forex / Crypto / India) plus Active / Waiting, and the master-key
chips in the sidebar toggle the Live (forex + crypto + INR) and OTC feeds, while
Beast floats the strongest setups to the top. Cards refresh in place every 5 s.

## Beast Mode (signal generator)

`/generator` is the Signal Generator module: a TradingView-style candle chart with
volume, MA(20), crosshair readout and eight timeframes (1m → 1M), an instrument
picker across all four markets, and a Generate Signal action that scores the
instrument with the same five-factor engine and renders a trade card —
direction, confidence, strength, AI accuracy, per-factor breakdown, entry time,
duration and a live expiry countdown.

The module is gated: it asks for the Beast Mode access key (`OXIDATIVE`) once and
remembers it in `localStorage` (`otc.beast`). Each session has 50 generated
signals (`otc.gen.used`); the generated feed is kept in `otc.gen.history`. A key
can also be passed as `?key=OXIDATIVE` for direct links.

## Run locally

```bash
cd otc-terminal
node server.js          # or: npm start
# http://localhost:3000
```

Static build (no server at all):

```bash
node build-static.js
cd static && python3 -m http.server 8080   # http://localhost:8080
```

Only Node 18+ is required — there is nothing to `npm install`.

## What's inside

| Route            | Description                                                        |
| ---------------- | ------------------------------------------------------------------ |
| `/`              | Landing page: live ticker, hero + counters, access gate, 6 engine cards, 3 pricing tiers, risk notice |
| `/terminal`      | Live dashboard: SSE signal feed with countdowns, canvas candlestick chart, 41-pair scanner, 5-factor meters, engine log, session info, alert sound |
| `/checkout`      | Upgrade page; issues a local simulated Pro license (no payment)     |
| `/404`           | Styled not-found page                                              |

### API

| Endpoint                | Purpose                                              |
| ----------------------- | ---------------------------------------------------- |
| `POST /api/auth`        | `{mode:"trial"}` → 5-signal seat, `{mode:"license", key}` → Pro seat |
| `GET  /api/feed`        | Server-sent events: `hello`, `signal`, `result`, `trial_end`, `paywall` |
| `GET  /api/pairs`       | Scanner snapshot for all 41 pairs                    |
| `GET  /api/candles?pair=X` | 72 simulated 3-second candles + last price        |
| `GET  /api/me`          | Session plan / signals seen                          |

Engine loop: one tick per second (random-walk prices, candle rollups), a new
confluence signal every 9–15 s, each resolving after a 60 s expiry.

## Try it

1. Open `/` and click **Try 5 Free Signals** — the trial is limited to 5 signals, then the paywall appears.
2. Or use license key `DEMO-KEY-2025` (any 8+ character key works in this demo) for unlimited access.
3. On `/checkout`, **Activate demo Pro access** issues a Pro seat instantly.

Sound is generated with the Web Audio API — press `M` in the terminal to mute.
