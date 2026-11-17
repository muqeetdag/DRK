# DRK
Drk

---

## OTC Signal Terminal (`otc-terminal/`)

A zero-dependency Node app that recreates the paid "Quotex Signals" landing page
and live terminal as a **simulation** (synthetic signals, no broker, no payments).

```bash
cd otc-terminal && npm start     # http://localhost:3000
```

Routes: `/` landing + access gate, `/terminal` live dashboard,
`/checkout` simulated upgrade. Any 8+ character license key works (e.g.
`DEMO-KEY-2025`), or use the 5-signal free evaluation. See
[`otc-terminal/README.md`](otc-terminal/README.md) for the API and caveats.

**Live site:** https://otc-signal-terminal.vercel.app (static build, no backend —
the engine runs in the browser). `docs/` holds the published static build.
