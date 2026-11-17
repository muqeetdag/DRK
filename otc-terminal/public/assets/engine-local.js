/* ==========================================================================
   Static / offline engine shim
   --------------------------------------------------------------------------
   Lets the terminal run on a pure static host (Vercel, Pages, any CDN) with
   no backend at all. It shims window.fetch for /api/* and window.EventSource,
   re-implementing the demo engine in the browser.

   Everything is simulated client-side: no broker, no market data, no network.
   ========================================================================== */
(function () {
  if (window.__OTC_STATIC__) return;
  window.__OTC_STATIC__ = true;

  /* ---------------------------------------------------------------- */
  /* Market universe (mirrors the Node engine)                         */
  /* ---------------------------------------------------------------- */
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
  const TAGS = ['5/5 Matrix', 'SMC Sweep', 'Kinetic Reversal', 'Order Block',
    'Trend Follow', 'Liquidity Grab', 'Breakout Shield'];
  const CRYPTO = new Set(['BTC/USD', 'ETH/USD', 'SOL/USD']);
  const isForex = (p) => !CRYPTO.has(p) && !p.startsWith('XAU') && !p.startsWith('XAG');
  const label = (p) => (isForex(p) ? p + ' (OTC)' : p);
  const dec = (p) => (p.startsWith('XAU') || p.startsWith('XAG') ? 2
    : p.endsWith('/JPY') || p === 'USD/INR' ? 3
      : p === 'BTC/USD' ? 1 : p === 'ETH/USD' || p === 'SOL/USD' ? 2 : 5);

  const FREE_TRIAL_LIMIT = 5;
  const CANDLE_MS = 3000;
  const HISTORY = 72;

  const prices = new Map(PAIRS.map(([p, v]) => [p, v]));
  const candles = new Map();
  const signals = [];
  let seq = 0;

  (function seed() {
    const start = Date.now() - HISTORY * CANDLE_MS;
    PAIRS.forEach(([pair, base]) => {
      let price = base;
      const arr = [];
      for (let i = 0; i < HISTORY; i++) {
        const o = price;
        const vol = base * 0.0006;
        const c = o + (Math.random() - 0.5) * vol * 2;
        arr.push({
          t: start + i * CANDLE_MS,
          o,
          h: Math.max(o, c) + Math.random() * vol * 0.6,
          l: Math.min(o, c) - Math.random() * vol * 0.6,
          c,
        });
        price = c;
      }
      candles.set(pair, arr);
      prices.set(pair, price);
    });
  })();

  function tick() {
    const bucket = Math.floor(Date.now() / CANDLE_MS) * CANDLE_MS;
    candles.forEach((arr, pair) => {
      const base = prices.get(pair) || arr[arr.length - 1].c;
      const vol = base * 0.00035;
      const next = Math.max(base + (Math.random() - 0.5) * vol * 2, base * 0.5);
      prices.set(pair, next);
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
    });
  }

  function makeSignal() {
    const pair = PAIRS[Math.floor(Math.random() * PAIRS.length)][0];
    const arr = candles.get(pair);
    const last = arr[arr.length - 1];
    const prev = arr[arr.length - 2] || last;
    const bull = last.c >= prev.c;
    const direction = Math.random() < 0.82 ? (bull ? 'CALL' : 'PUT') : (bull ? 'PUT' : 'CALL');
    const entryAt = Date.now() + 4000;
    const sig = {
      id: 'SIG-' + String(++seq).padStart(4, '0'),
      pair,
      label: label(pair),
      direction,
      confidence: Math.round((92 + Math.random() * 7.4) * 10) / 10,
      tag: TAGS[Math.floor(Math.random() * TAGS.length)],
      timeframe: '1M',
      price: prices.get(pair),
      entryAt,
      expiresAt: entryAt + 60000,
      status: 'PENDING',
      confluence: 5,
    };
    signals.unshift(sig);
    if (signals.length > 60) signals.pop();
    return sig;
  }

  /* ---------------------------------------------------------------- */
  /* Sessions (persisted in localStorage so reloads don't reset)       */
  /* ---------------------------------------------------------------- */
  const SESSIONS_KEY = 'otc.static.sessions';

  function readSessions() {
    try { return JSON.parse(localStorage.getItem(SESSIONS_KEY) || '{}'); } catch { return {}; }
  }
  function writeSessions(map) {
    try { localStorage.setItem(SESSIONS_KEY, JSON.stringify(map)); } catch { /* ignore */ }
  }
  function newToken() {
    const t = 'st_' + Math.random().toString(36).slice(2) + Date.now().toString(36);
    const map = readSessions();
    map[t] = { plan: 'trial', seen: 0, delivered: [], createdAt: Date.now() };
    // keep the store small
    const keys = Object.keys(map);
    if (keys.length > 12) keys.sort((a, b) => map[a].createdAt - map[b].createdAt)
      .slice(0, keys.length - 12).forEach((k) => delete map[k]);
    writeSessions(map);
    return t;
  }
  function getSession(token) {
    const map = readSessions();
    return map[token] ? { token, ...map[token] } : null;
  }
  function patchSession(token, patch) {
    const map = readSessions();
    if (!map[token]) return null;
    map[token] = { ...map[token], ...patch };
    writeSessions(map);
    return map[token];
  }

  function snapshot(session) {
    const replay = session.plan === 'trial'
      ? signals.filter((s) => session.delivered.includes(s.id))
        .map((s) => ({ ...s, trialIndex: session.delivered.indexOf(s.id) + 1, trialLimit: FREE_TRIAL_LIMIT }))
      : signals.slice(0, 8);
    return {
      plan: session.plan,
      remaining: session.plan === 'trial' ? Math.max(0, FREE_TRIAL_LIMIT - session.seen) : null,
      signals: replay,
      candles: candles.get('EUR/USD'),
      decimals: dec('EUR/USD'),
      price: prices.get('EUR/USD'),
      pairs: PAIRS.map(([p]) => p),
      serverTime: Date.now(),
    };
  }

  /* ---------------------------------------------------------------- */
  /* fetch() shim for /api/*                                          */
  /* ---------------------------------------------------------------- */
  const errorsOffline = () => Promise.reject(new Error('offline'));

  function jsonResponse(body, status) {
    return new Response(JSON.stringify(body), {
      status: status || 200,
      headers: { 'content-type': 'application/json' },
    });
  }

  const nativeFetch = window.fetch ? window.fetch.bind(window) : null;

  window.fetch = function (input, init) {
    const raw = typeof input === 'string' ? input : (input && input.url) || '';
    let url;
    try { url = new URL(raw, location.href); } catch { return nativeFetch ? nativeFetch(input, init) : errorsOffline(); }
    if (!url.pathname.startsWith('/api/')) {
      return nativeFetch ? nativeFetch(input, init) : errorsOffline();
    }

    const body = (() => {
      try { return init && init.body ? JSON.parse(init.body) : {}; } catch { return {}; }
    })();
    const authHeader = (init && init.headers && (init.headers.authorization || init.headers.Authorization)) || '';
    const token = url.searchParams.get('token') ||
      (authHeader.startsWith('Bearer ') ? authHeader.slice(7) : null);

    switch (url.pathname) {
      case '/api/health':
        return Promise.resolve(jsonResponse({ ok: true, uptime: Math.floor(performance.now() / 1000) }));

      case '/api/auth': {
        const mode = body.mode === 'trial' ? 'trial' : 'license';
        const t = newToken();
        if (mode === 'trial') {
          return Promise.resolve(jsonResponse({ token: t, plan: 'trial', limit: FREE_TRIAL_LIMIT }));
        }
        const key = String(body.key || '').trim().toUpperCase();
        if (!/^[A-Z0-9][A-Z0-9-]{7,40}$/.test(key)) {
          return Promise.resolve(jsonResponse({
            error: 'Invalid license key format. Keys are 8+ characters, letters, numbers and dashes.',
          }, 400));
        }
        patchSession(t, { plan: 'pro' });
        return Promise.resolve(jsonResponse({
          token: t, plan: 'pro', key: key.slice(0, 4) + '-••••-' + key.slice(-4),
        }));
      }

      case '/api/pairs': {
        const rows = PAIRS.map(([pair]) => {
          const arr = candles.get(pair);
          const last = arr[arr.length - 1];
          const first = arr[arr.length - 20] || arr[0];
          return {
            pair,
            label: label(pair),
            price: prices.get(pair),
            decimals: dec(pair),
            change: Math.round(((last.c - first.c) / first.c) * 10000) / 100,
            volatility: Math.round((Math.abs(((last.c - first.c) / first.c) * 100) * 13 + 2) * 10) / 10,
            status: Math.random() < 0.14 ? 'CONSOLIDATING' : 'SCANNING',
          };
        });
        return Promise.resolve(jsonResponse({ rows, updatedAt: Date.now() }));
      }

      case '/api/candles': {
        const pair = url.searchParams.get('pair') || 'EUR/USD';
        if (!candles.has(pair)) return Promise.resolve(jsonResponse({ error: 'unknown pair' }, 404));
        return Promise.resolve(jsonResponse({
          pair, decimals: dec(pair), candles: candles.get(pair), price: prices.get(pair),
        }));
      }

      case '/api/live': {
        const session = getSession(token);
        if (!session) return Promise.resolve(jsonResponse({ error: 'unauthorized' }, 401));
        // polling also grants fresh evaluation signals
        if (session.plan === 'trial' && session.seen < FREE_TRIAL_LIMIT) {
          const latest = signals[0];
          if (latest && Date.now() - latest.entryAt < 45000 && !session.delivered.includes(latest.id)) {
            patchSession(session.token, {
              seen: session.seen + 1,
              delivered: session.delivered.concat(latest.id),
            });
            session.seen += 1;
            session.delivered = session.delivered.concat(latest.id);
          }
        }
        return Promise.resolve(jsonResponse(snapshot(session)));
      }

      case '/api/me': {
        const session = getSession(token);
        if (!session) return Promise.resolve(jsonResponse({ error: 'unauthorized' }, 401));
        return Promise.resolve(jsonResponse({
          plan: session.plan, seen: session.seen, limit: FREE_TRIAL_LIMIT,
        }));
      }

      default:
        return Promise.resolve(jsonResponse({ error: 'unknown endpoint' }, 404));
    }
  };

  /* ---------------------------------------------------------------- */
  /* EventSource shim                                                 */
  /* ---------------------------------------------------------------- */
  const streams = new Set();

  class LocalEventSource {
    constructor(url) {
      this.url = url;
      this.listeners = {};
      this.readyState = 0;
      this.onopen = null;
      this.onerror = null;
      this.onmessage = null;

      const token = new URL(url, location.href).searchParams.get('token');
      this.session = getSession(token);
      streams.add(this);

      setTimeout(() => {
        if (this.readyState === 2) return;
        this.readyState = 1;
        if (typeof this.onopen === 'function') this.onopen({ type: 'open' });
        this.emit('open', {});
        if (!this.session) {
          this.emit('error', {});
          return;
        }
        this.emit('hello', snapshot(this.session));
        if (this.session.plan === 'trial' && this.session.seen >= FREE_TRIAL_LIMIT) {
          this.emit('paywall', { reason: 'trial_exhausted' });
        }
      }, 30);
    }

    addEventListener(type, fn) {
      (this.listeners[type] = this.listeners[type] || []).push(fn);
    }

    removeEventListener(type, fn) {
      this.listeners[type] = (this.listeners[type] || []).filter((f) => f !== fn);
    }

    emit(type, data) {
      const evt = { type, data: JSON.stringify(data) };
      (this.listeners[type] || []).forEach((fn) => {
        try { fn(evt); } catch (err) { console.error(err); }
      });
      if (type === 'message' && typeof this.onmessage === 'function') this.onmessage(evt);
    }

    close() {
      this.readyState = 2;
      streams.delete(this);
    }
  }

  window.EventSource = LocalEventSource;

  function broadcast(type, data) {
    streams.forEach((s) => {
      if (s.readyState === 1) s.emit(type, data);
    });
  }

  /* ---------------------------------------------------------------- */
  /* Engine loop                                                      */
  /* ---------------------------------------------------------------- */
  let nextSignalAt = Date.now() + 5000;

  setInterval(() => {
    tick();

    // resolve expired signals
    const now = Date.now();
    signals.forEach((s) => {
      if (s.status === 'PENDING' && now >= s.expiresAt) {
        s.status = Math.random() < 0.874 ? 'WIN' : 'LOSS';
        broadcast('result', s);
      }
    });

    if (now >= nextSignalAt) {
      const sig = makeSignal();
      streams.forEach((stream) => {
        const session = stream.session && getSession(stream.session.token);
        if (!session || stream.readyState !== 1) return;
        if (session.plan === 'trial') {
          if (session.seen >= FREE_TRIAL_LIMIT) {
            stream.emit('paywall', { reason: 'trial_exhausted' });
            return;
          }
          const seen = session.seen + 1;
          const delivered = session.delivered.concat(sig.id);
          patchSession(session.token, { seen, delivered });
          stream.session = { ...session, seen, delivered };
          stream.emit('signal', { ...sig, trialIndex: seen, trialLimit: FREE_TRIAL_LIMIT });
          if (seen >= FREE_TRIAL_LIMIT) stream.emit('trial_end', { limit: FREE_TRIAL_LIMIT });
        } else {
          stream.emit('signal', sig);
        }
      });
      nextSignalAt = now + 9000 + Math.random() * 6000;
    }
  }, 1000);
})();
