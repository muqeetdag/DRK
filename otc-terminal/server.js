'use strict';

/**
 * OTC Signal Terminal — a self-contained clone/recreation of the reference
 * "Quotex Signals" landing page + terminal.
 *
 * Zero dependencies. Serves the static front-end from ./public and exposes a
 * small JSON/SSE API backed by a SIMULATED market engine (no real broker data).
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.env.PORT || 3000);
const HOST = process.env.HOST || '0.0.0.0';
const PUBLIC_DIR = path.join(__dirname, 'public');

/* ------------------------------------------------------------------ */
/* Market universe                                                     */
/* ------------------------------------------------------------------ */

const PAIRS = [
  ['EUR/USD', 1.0852], ['GBP/USD', 1.2714], ['USD/JPY', 151.32], ['USD/INR', 83.41],
  ['AUD/CAD', 0.8891], ['EUR/GBP', 0.8537], ['BTC/USD', 67420], ['ETH/USD', 3285],
  ['USD/CAD', 1.3562], ['USD/CHF', 0.8874], ['NZD/USD', 0.6042], ['EUR/JPY', 164.21],
  ['GBP/JPY', 192.44], ['AUD/USD', 0.6581], ['AUD/JPY', 99.62], ['CAD/JPY', 111.58],
  ['CHF/JPY', 170.52], ['EUR/AUD', 1.6489], ['EUR/CAD', 1.4716], ['EUR/CHF', 0.9627],
  ['EUR/NZD', 1.7964], ['GBP/AUD', 1.9321], ['GBP/CAD', 1.7245], ['GBP/CHF', 1.1283],
  ['GBP/NZD', 2.1042], ['NZD/JPY', 91.44], ['AUD/CHF', 0.5837], ['AUD/NZD', 1.0897],
  ['CAD/CHF', 0.6543], ['NZD/CAD', 0.8155], ['NZD/CHF', 0.5362], ['USD/SGD', 1.3421],
  ['USD/ZAR', 18.742], ['USD/MXN', 16.684], ['USD/TRY', 32.18], ['USD/BRL', 5.132],
  ['USD/PLN', 3.981], ['USD/SEK', 10.442], ['XAU/USD', 2338.4], ['XAG/USD', 27.62],
  ['SOL/USD', 168.4],
];

const TAGS = [
  '5/5 Matrix', 'SMC Sweep', 'Kinetic Reversal', 'Order Block',
  'Trend Follow', 'Liquidity Grab', 'Breakout Shield',
];

const CRYPTO = new Set(['BTC/USD', 'ETH/USD', 'SOL/USD']);

function isForex(pair) {
  return !CRYPTO.has(pair) && !pair.startsWith('XAU') && !pair.startsWith('XAG');
}

function pairLabel(pair) {
  return isForex(pair) ? `${pair} (OTC)` : pair;
}

const dec = (pair) => (pair.startsWith('XAU') || pair.startsWith('XAG') ? 2 : pair.startsWith('USD/JPY') || pair.endsWith('/JPY') || pair === 'USD/INR' ? 3 : pair === 'BTC/USD' ? 1 : pair === 'ETH/USD' || pair === 'SOL/USD' ? 2 : 5);

/* ------------------------------------------------------------------ */
/* Simulated engine                                                    */
/* ------------------------------------------------------------------ */

const state = {
  prices: new Map(PAIRS.map(([p, v]) => [p, v])),
  candles: new Map(),
  signals: [],
  seq: 0,
  startedAt: Date.now(),
};

const CANDLE_MS = 3000;
const HISTORY = 72;

function seedCandles() {
  const now = Date.now();
  const start = now - HISTORY * CANDLE_MS;
  for (const [pair, base] of PAIRS) {
    let price = base;
    const arr = [];
    for (let i = 0; i < HISTORY; i++) {
      const o = price;
      const vol = base * 0.0006;
      const c = o + (Math.random() - 0.5) * vol * 2;
      const h = Math.max(o, c) + Math.random() * vol * 0.6;
      const l = Math.min(o, c) - Math.random() * vol * 0.6;
      arr.push({ t: start + i * CANDLE_MS, o, h, l, c });
      price = c;
    }
    state.candles.set(pair, arr);
    state.prices.set(pair, price);
  }
}
seedCandles();

function tick() {
  const now = Date.now();
  const bucket = Math.floor(now / CANDLE_MS) * CANDLE_MS;
  for (const [pair, arr] of state.candles) {
    const base = state.prices.get(pair) || arr[arr.length - 1].c;
    const vol = base * 0.00035;
    const drift = (Math.random() - 0.5) * vol * 2;
    const next = Math.max(base + drift, base * 0.5);
    state.prices.set(pair, next);

    let last = arr[arr.length - 1];
    if (last.t < bucket) {
      last = { t: bucket, o: next, h: next, l: next, c: next };
      arr.push(last);
      if (arr.length > HISTORY) arr.shift();
    } else {
      last.c = next;
      last.h = Math.max(last.h, next);
      last.l = Math.min(last.l, next);
    }
  }
}

function makeSignal() {
  // Weight recent volatility so the "engine" feels responsive.
  const pair = PAIRS[Math.floor(Math.random() * PAIRS.length)][0];
  const candles = state.candles.get(pair);
  const last = candles[candles.length - 1];
  const prev = candles[candles.length - 2] || last;
  const bull = last.c >= prev.c;
  const direction = Math.random() < 0.82 ? (bull ? 'CALL' : 'PUT') : (bull ? 'PUT' : 'CALL');
  const confidence = Math.round((92 + Math.random() * 7.4) * 10) / 10;
  const entryAt = Date.now() + 4000;
  const sig = {
    id: `SIG-${String(++state.seq).padStart(4, '0')}`,
    pair,
    label: pairLabel(pair),
    direction,
    confidence,
    tag: TAGS[Math.floor(Math.random() * TAGS.length)],
    timeframe: '1M',
    price: state.prices.get(pair),
    entryAt,
    expiresAt: entryAt + 60000,
    status: 'PENDING',
    confluence: 5,
  };
  state.signals.unshift(sig);
  if (state.signals.length > 60) state.signals.pop();
  return sig;
}

function resolveSignals() {
  const now = Date.now();
  for (const s of state.signals) {
    if (s.status === 'PENDING' && now >= s.expiresAt) {
      s.status = Math.random() < 0.874 ? 'WIN' : 'LOSS';
      broadcast('result', s);
    }
  }
}

const clients = new Set();

function broadcast(event, data) {
  const payload = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
  for (const res of clients) {
    try { res.write(payload); } catch (_) { clients.delete(res); }
  }
}

/* ------------------------------------------------------------------ */
/* Auth / licensing (demo only)                                        */
/* ------------------------------------------------------------------ */

const sessions = new Map();
const FREE_TRIAL_LIMIT = 5;

function newToken(plan) {
  const token = crypto.randomBytes(18).toString('hex');
  sessions.set(token, { plan, seen: 0, delivered: [], createdAt: Date.now() });
  return token;
}

function getSession(req, url) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : url.searchParams.get('token');
  if (!token) return null;
  return sessions.get(token) ? { token, ...sessions.get(token) } : null;
}

/* ------------------------------------------------------------------ */
/* HTTP helpers                                                        */
/* ------------------------------------------------------------------ */

function feedSnapshot(session) {
  // Trial seats only ever see the signals they were actually granted — the
  // global history is reserved for Pro so a reload can't yield free signals.
  const replay = session.plan === 'trial'
    ? state.signals
        .filter((sig) => session.delivered.includes(sig.id))
        .map((sig) => ({
          ...sig,
          trialIndex: session.delivered.indexOf(sig.id) + 1,
          trialLimit: FREE_TRIAL_LIMIT,
        }))
    : state.signals.slice(0, 8);
  return {
    plan: session.plan,
    remaining: session.plan === 'trial' ? Math.max(0, FREE_TRIAL_LIMIT - session.seen) : null,
    signals: replay,
    candles: state.candles.get('EUR/USD'),
    decimals: dec('EUR/USD'),
    price: state.prices.get('EUR/USD'),
    pairs: PAIRS.map(([p]) => p),
    serverTime: Date.now(),
  };
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.json': 'application/json; charset=utf-8',
  '.woff2': 'font/woff2',
};

function json(res, code, body) {
  const payload = JSON.stringify(body);
  res.writeHead(code, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'content-length': Buffer.byteLength(payload),
  });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (c) => {
      raw += c;
      if (raw.length > 1e6) req.destroy();
    });
    req.on('end', () => {
      try { resolve(JSON.parse(raw || '{}')); } catch { resolve({}); }
    });
    req.on('error', () => resolve({}));
  });
}

/* ------------------------------------------------------------------ */
/* Server                                                              */
/* ------------------------------------------------------------------ */

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = decodeURIComponent(url.pathname);

  /* --- API ------------------------------------------------------- */
  if (pathname === '/api/health') {
    return json(res, 200, { ok: true, uptime: Math.floor((Date.now() - state.startedAt) / 1000) });
  }

  if (pathname === '/api/pairs') {
    const rows = PAIRS.map(([pair]) => {
      const c = state.candles.get(pair);
      const last = c[c.length - 1];
      const first = c[c.length - 20] || c[0];
      const change = ((last.c - first.c) / first.c) * 100;
      return {
        pair,
        label: pairLabel(pair),
        price: state.prices.get(pair),
        decimals: dec(pair),
        change: Math.round(change * 100) / 100,
        volatility: Math.round((Math.abs(change) * 13 + 2) * 10) / 10,
        status: Math.random() < 0.14 ? 'CONSOLIDATING' : 'SCANNING',
      };
    });
    return json(res, 200, { rows, updatedAt: Date.now() });
  }

  if (pathname === '/api/candles') {
    const pair = url.searchParams.get('pair') || 'EUR/USD';
    const c = state.candles.get(pair);
    if (!c) return json(res, 404, { error: 'unknown pair' });
    return json(res, 200, { pair, decimals: dec(pair), candles: c, price: state.prices.get(pair) });
  }

  if (pathname === '/api/auth' && req.method === 'POST') {
    const body = await readBody(req);
    const mode = body.mode === 'trial' ? 'trial' : 'license';
    if (mode === 'trial') {
      const token = newToken('trial');
      return json(res, 200, { token, plan: 'trial', limit: FREE_TRIAL_LIMIT });
    }
    const key = String(body.key || '').trim().toUpperCase();
    if (!/^[A-Z0-9][A-Z0-9-]{7,40}$/.test(key)) {
      return json(res, 400, { error: 'Invalid license key format. Keys are 8+ characters, letters, numbers and dashes.' });
    }
    // Demo build: every well-formed key activates a simulated Pro seat.
    const token = newToken('pro');
    return json(res, 200, { token, plan: 'pro', key: key.slice(0, 4) + '-••••-' + key.slice(-4) });
  }

  if (pathname === '/api/live') {
    const s = getSession(req, url);
    if (!s) return json(res, 401, { error: 'unauthorized' });
    const session = sessions.get(s.token);

    // Polling fallback also grants fresh trial signals, otherwise a buffered
    // proxy would leave evaluation seats with an empty feed.
    if (session.plan === 'trial' && session.seen < FREE_TRIAL_LIMIT) {
      const latest = state.signals[0];
      const fresh = latest && Date.now() - latest.entryAt < 45000;
      if (fresh && !session.delivered.includes(latest.id)) {
        session.seen += 1;
        session.delivered.push(latest.id);
      }
    }
    return json(res, 200, feedSnapshot(session));
  }

  if (pathname === '/api/me') {
    const s = getSession(req, url);
    if (!s) return json(res, 401, { error: 'unauthorized' });
    return json(res, 200, { plan: s.plan, seen: s.seen, limit: FREE_TRIAL_LIMIT });
  }

  if (pathname === '/api/feed' || pathname === '/api/stream') {
    const s = getSession(req, url);
    if (!s) return json(res, 401, { error: 'unauthorized' });

    res.writeHead(200, {
      'content-type': 'text/event-stream',
      'cache-control': 'no-cache, no-transform',
      connection: 'keep-alive',
      'x-accel-buffering': 'no',
    });
    res.write('retry: 3000\n\n');
    clients.add(res);

    const session = sessions.get(s.token);
    const remaining = session.plan === 'trial' ? Math.max(0, FREE_TRIAL_LIMIT - session.seen) : Infinity;

    const send = (event, data) => res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);

    send('hello', feedSnapshot(session));

    if (session.plan === 'trial' && remaining <= 0) {
      send('paywall', { reason: 'trial_exhausted' });
    }

    const onSignal = (sig) => {
      if (session.plan === 'trial') {
        if (session.seen >= FREE_TRIAL_LIMIT) {
          send('paywall', { reason: 'trial_exhausted' });
          return;
        }
        session.seen += 1;
        session.delivered.push(sig.id);
        send('signal', { ...sig, trialIndex: session.seen, trialLimit: FREE_TRIAL_LIMIT });
        if (session.seen >= FREE_TRIAL_LIMIT) send('trial_end', { limit: FREE_TRIAL_LIMIT });
      } else {
        send('signal', sig);
      }
    };

    signalListeners.add(onSignal);

    const keepAlive = setInterval(() => {
      try { res.write(': ping\n\n'); } catch (_) { /* ignore */ }
    }, 20000);

    req.on('close', () => {
      clearInterval(keepAlive);
      signalListeners.delete(onSignal);
      clients.delete(res);
    });
    return;
  }

  /* --- static files ---------------------------------------------- */
  let filePath = path.join(PUBLIC_DIR, pathname === '/' ? 'index.html' : pathname);
  if (!filePath.startsWith(PUBLIC_DIR)) return json(res, 403, { error: 'forbidden' });

  fs.stat(filePath, (err, stat) => {
    if (err || stat.isDirectory()) {
      // extensionless route -> try .html, else SPA-ish 404 page
      if (!path.extname(pathname)) {
        const alt = path.join(PUBLIC_DIR, `${pathname.replace(/\/$/, '')}.html`);
        if (alt.startsWith(PUBLIC_DIR) && fs.existsSync(alt)) return serve(alt, res);
      }
      const notFound = path.join(PUBLIC_DIR, '404.html');
      if (fs.existsSync(notFound)) {
        res.writeHead(404, { 'content-type': 'text/html; charset=utf-8' });
        return fs.createReadStream(notFound).pipe(res);
      }
      res.writeHead(404, { 'content-type': 'text/plain' });
      return res.end('Not found');
    }
    serve(filePath, res);
  });
});

function serve(file, res) {
  const ext = path.extname(file).toLowerCase();
  res.writeHead(200, {
    'content-type': MIME[ext] || 'application/octet-stream',
    'cache-control': ext === '.html' ? 'no-store' : 'public, max-age=300',
  });
  fs.createReadStream(file).pipe(res);
}

/* Signal fan-out to SSE clients, respecting each session's trial budget. */
const signalListeners = new Set();

/* ------------------------------------------------------------------ */
/* Engine loop                                                         */
/* ------------------------------------------------------------------ */

let nextSignalAt = Date.now() + 6000;

setInterval(() => {
  tick();
  resolveSignals();
  if (Date.now() >= nextSignalAt) {
    const sig = makeSignal();
    for (const listener of signalListeners) {
      try { listener(sig); } catch (_) { /* ignore */ }
    }
    nextSignalAt = Date.now() + 9000 + Math.random() * 6000;
  }
}, 1000);

server.listen(PORT, HOST, () => {
  console.log(`[term] OTC signal terminal listening on http://${HOST}:${PORT}`);
});
