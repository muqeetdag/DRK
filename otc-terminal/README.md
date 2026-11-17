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
`?p=checkout` upgrade.

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
