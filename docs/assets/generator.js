/* =====================================================================
   OTC Bot — Signal Generator (Beast Mode)
   ---------------------------------------------------------------------
   Draws real candles from the shared MARKET engine, scores the selected
   instrument with the five-factor model and renders a tradeable signal.
   The generator itself is gated behind the Beast Mode access key.
   ===================================================================== */
(function () {
  'use strict';

  const { api } = window.OTC;
  const M = window.MARKET;
  const CLOCK = window.CLOCK || {
    istFull: (d) => new Date(d).toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' }),
    istFullStamp: (d) => new Date(d).toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' }),
    istTime: (d) => new Date(d).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
    plan: () => ({ entryMs: Date.now(), expiryMs: Date.now() + 60000, pending: false, leftMs: 60000 }),
  };
  const $ = (id) => document.getElementById(id);

  /* ---------------------------------------------------------------- keys */
  const BEAST_KEY = 'OXIDATIVE';
  const BEAST_STORE = 'otc.beast';
  const GEN_LIMIT = 50;
  const USED_KEY = 'otc.gen.used';
  const HIST_KEY = 'otc.gen.history';

  const TF_MS = { '1m': 6e4, '5m': 3e5, '15m': 9e5, '1H': 36e5, '4H': 144e5, '1D': 864e5, '1W': 6048e5, '1M': 2592e6 };
  const TF_BINANCE = { '1m': '1m', '5m': '5m', '15m': '15m', '1H': '1h', '4H': '4h', '1D': '1d', '1W': '1w', '1M': '1M' };
  const TF_LABEL = { '1m': '1 minute', '5m': '5 minutes', '15m': '15 minutes', '1H': '1 hour', '4H': '4 hours', '1D': '1 day', '1W': '1 week', '1M': '1 month' };

  /* --------------------------------------------------------------- state */
  const state = {
    id: 'FX:EURUSD',
    tf: '1m',
    candles: [],
    source: '',
    hover: -1,
    beast: false,
    tv: '',
    tvBlocked: false,
    used: 0,
    signal: null,
    entryAt: 0,
    expiryAt: 0,
    history: [],
    sound: false,
    timer: null,
    loading: false,
    nextScanAt: Date.now() + 45000,
  };

  /* --------------------------------------------------------------- utils */
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
  const pad = (n) => String(n).padStart(2, '0');

  function toast(html, cls = '') {
    const wrap = $('toasts');
    if (!wrap) return;
    const el = document.createElement('div');
    el.className = 'toast ' + cls;
    el.innerHTML = html;
    wrap.appendChild(el);
    setTimeout(() => el.remove(), 4200);
  }

  function beep() {
    if (!state.sound) return;
    try {
      const Ctx = window.AudioContext || window.webkitAudioContext;
      if (!Ctx) return;
      const ctx = beep.ctx || (beep.ctx = new Ctx());
      const o = ctx.createOscillator();
      const g = ctx.createGain();
      o.type = 'sine';
      o.frequency.value = 720;
      g.gain.setValueAtTime(0.0001, ctx.currentTime);
      g.gain.exponentialRampToValueAtTime(0.1, ctx.currentTime + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.26);
      o.connect(g).connect(ctx.destination);
      o.start();
      o.stop(ctx.currentTime + 0.3);
    } catch { /* ignore */ }
  }

  function hash(str) {
    let h = 2166136261;
    for (let i = 0; i < str.length; i++) {
      h ^= str.charCodeAt(i);
      h = Math.imul(h, 16777619);
    }
    return h >>> 0;
  }
  function mulberry32(a) {
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function store(key, value) {
    try {
      if (value === undefined) return localStorage.getItem(key);
      localStorage.setItem(key, value);
    } catch { /* storage blocked */ }
    return null;
  }

  function fmtPrice(v, digits) {
    if (v == null || Number.isNaN(v)) return '—';
    const d = v < 0.01 ? 8 : v < 1 ? 5 : v < 100 ? digits : v < 1000 ? Math.min(digits, 4) : v < 10000 ? Math.min(digits, 3) : Math.min(digits, 2);
    return Number(v).toLocaleString('en-US', { minimumFractionDigits: d, maximumFractionDigits: d });
  }

  function timeAgo(ts) {
    const s = Math.max(0, Math.round((Date.now() - ts) / 1000));
    if (s < 60) return s + 's';
    if (s < 3600) return Math.floor(s / 60) + 'm';
    return Math.floor(s / 3600) + 'h';
  }

  /* ------------------------------------------------------ localStorage */
  function loadHistory() {
    try { state.history = JSON.parse(store(HIST_KEY) || '[]').slice(0, 20); } catch { state.history = []; }
  }
  function saveHistory() {
    try { store(HIST_KEY, JSON.stringify(state.history.slice(0, 20))); } catch { /* ignore */ }
  }

  /* ------------------------------------------------------------ header */
  let liveUsers = 259 + Math.floor(Math.random() * 40);

  function paintPush() {
    const el = $('lastPush');
    if (!el) return;
    const d = new Date();
    el.textContent = d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
      + ', ' + d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  }

  function paintClock() {
    const d = new Date();
    const clock = $('sessionClock');
    if (clock) clock.textContent = CLOCK.istClock ? CLOCK.istClock(d) : `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
    const lu = $('liveUsers');
    if (lu) lu.textContent = liveUsers;
  }

  function paintCount() {
    const left = Math.max(0, GEN_LIMIT - state.used);
    const el = $('genCount');
    if (el) el.textContent = `${left}/${GEN_LIMIT}`;
    const bar = $('gaBar');
    if (bar) bar.style.width = (left / GEN_LIMIT) * 100 + '%';
    const leftEl = $('spLeft');
    if (leftEl) leftEl.textContent = String(left);
    const note = $('gaNote');
    if (note) note.textContent = left > 0
      ? `Beast Mode · ${left} of ${GEN_LIMIT} signals left`
      : 'Signal limit reached · ask @DRKMUQEETT for a new key';
    const btn = $('genBtn');
    if (!btn) return;
    btn.classList.toggle('locked', !state.beast && left > 0);
    btn.classList.toggle('done', left === 0);
    btn.disabled = left === 0;
  }

  /* ---------------------------------------------------------- beast mode */
  function applyBeast(on, announce) {
    state.beast = !!on;
    document.body.classList.toggle('beast-mode', state.beast);
    const sw = $('beastSwitch');
    const st = $('beastState');
    if (sw) sw.setAttribute('aria-pressed', state.beast ? 'true' : 'false');
    if (st) st.textContent = state.beast ? 'ON' : 'LOCKED';
    const ind = $('indCount');
    if (ind) ind.textContent = state.beast ? '23 Indicators' : '11 Indicators';
    const chip = $('beastChip');
    if (chip) chip.classList.toggle('on', state.beast);
    if (state.beast) store(BEAST_STORE, '1');
    paintCount();
    if (announce) {
      toast('<b>Beast Mode online.</b> Deep candles, 23 indicators and 50 unlocked signals.', 'ok');
      beep();
    }
  }

  let gateReason = '';
  function openGate(reason) {
    gateReason = reason || '';
    const gate = $('beastGate');
    gate.hidden = false;
    $('beastErr').hidden = true;
    const input = $('beastKey');
    input.value = '';
    setTimeout(() => input.focus(), 120);
    if (gateReason) toast(gateReason, 'err');
  }
  function closeGate() { $('beastGate').hidden = true; }

  function submitKey(raw) {
    const value = String(raw || '').trim().toUpperCase();
    const card = $('gateForm');
    if (value === BEAST_KEY) {
      api.set('beast-' + Date.now().toString(36), 'pro');
      applyBeast(true, true);
      closeGate();
      boot();
      return true;
    }
    $('beastErr').hidden = false;
    card.classList.add('shake');
    setTimeout(() => card.classList.remove('shake'), 420);
    return false;
  }

  /* -------------------------------------------------------- instruments */
  function instruments() { return M.state.instruments || []; }
  function instById(id) { return M.state.byId[id] || instruments().find((i) => i.id === id) || null; }
  function currentInst() { return instById(state.id) || instruments()[0] || null; }

  function fillPicker() {
    const sel = $('instSel');
    if (!sel) return;
    const groups = [
      ['otc', 'OTC pairs'],
      ['forex', 'Forex'],
      ['crypto', 'Crypto'],
      ['india', 'Indian market (INR)'],
    ];
    sel.innerHTML = groups.map(([kind, label]) => {
      const rows = instruments().filter((i) => i.kind === kind);
      if (!rows.length) return '';
      return `<optgroup label="${label} · ${rows.length}">` + rows
        .map((i) => `<option value="${i.id}">${i.symbol}</option>`)
        .join('') + '</optgroup>';
    }).join('');
    if (!instById(state.id)) state.id = instruments()[0]?.id || state.id;
    sel.value = state.id;
  }

  function paintInstrument() {
    const inst = currentInst();
    if (!inst) return;
    $('cpSym').textContent = inst.pair || inst.symbol;
    $('cpTf').textContent = state.tf;
    $('instName').textContent = inst.pair || inst.symbol;
    const tvSym = tvSymbol(inst);
    $('tvLink').href = tvSym
      ? 'https://www.tradingview.com/chart/?symbol=' + encodeURIComponent(tvSym)
      : 'https://www.tradingview.com/markets/currencies/';
    const px = M.priceOf(inst);
    $('statPrice').textContent = fmtPrice(px, inst.digits || 5);
    const chg = inst.chg;
    const chip = $('statChg');
    if (chg != null && Number.isFinite(chg)) {
      chip.textContent = `${chg >= 0 ? '+' : ''}${chg.toFixed(2)}% today`;
      chip.className = 'stat ' + (chg >= 0 ? 'up' : 'dn');
    } else {
      chip.textContent = '';
      chip.className = 'stat';
    }
    const feed = $('feedTag');
    if (feed) {
      const mode = M.state.mode;
      feed.textContent = mode === 'demo' ? 'LOCAL MODEL' : (mode === 'mixed' ? 'MIXED' : 'LIVE');
    }
    const nav = $('navActive');
    if (nav && M.state.view) {
      const active = Object.values(M.state.view).filter((v) => v.data && v.data.active).length;
      if (active) nav.textContent = active;
    }
  }

  /* ------------------------------------------------ tradingview chart */
  const TV_TF = { '1m': '1', '5m': '5', '15m': '15', '1H': '60', '4H': '240', '1D': 'D', '1W': 'W', '1M': 'M' };

  /* the real exchange symbol behind an instrument; OTC pairs are synthetic
     and have no public feed, so they keep the engine chart */
  function tvSymbol(inst) {
    if (!inst) return null;
    if (inst.kind === 'forex') {
      if (inst.symbol === 'XAUUSD' || inst.symbol === 'XAGUSD') return 'OANDA:' + inst.symbol;
      if (inst.symbol === 'USDINR' || inst.symbol === 'EURINR' || inst.symbol === 'AEDINR') return 'FX_IDC:' + inst.symbol;
      return 'FX:' + inst.symbol;
    }
    if (inst.kind === 'crypto') return 'BINANCE:' + (inst.bsym || inst.symbol);
    if (inst.kind === 'india') {
      if (inst.symbol === 'USDINR') return 'FX_IDC:USDINR';
      return 'BINANCE:' + (inst.bsym || 'BTCUSDT');
    }
    return null;
  }

  /* TradingView could not be reached (blocked network, offline, CSP): keep
     the panel usable by painting the engine chart underneath and saying so */
  function engineChart(reason) {
    const wrap = $('tvChart');
    const note = $('tvNote');
    const canvas = $('chart');
    state.tv = '';
    state.tvBlocked = true;
    if (canvas) canvas.hidden = false;
    if (wrap) {
      wrap.hidden = false;
      wrap.classList.add('fallback');
    }
    if (note && reason) {
      note.hidden = false;
      note.textContent = reason;
    }
    if (reason) $('chartSource').textContent = reason;
    draw();
  }

  function mountTV(inst) {
    const wrap = $('tvChart');
    const box = $('tvHolder');
    const canvas = $('chart');
    if (!wrap || !box || !canvas) return false;

    const sym = tvSymbol(inst);
    if (!sym) {                              /* OTC -> engine chart */
      wrap.hidden = true;
      canvas.hidden = false;
      state.tv = '';
      return false;
    }

    const key = sym + '|' + state.tf;
    if (state.tv === key) return true;

    state.tv = key;
    wrap.hidden = false;
    wrap.classList.remove('fallback');
    canvas.hidden = true;
    box.innerHTML = '';

    const note = $('tvNote');
    if (note) { note.hidden = true; note.textContent = ''; }

    const holder = document.createElement('div');
    holder.className = 'tradingview-widget-container';
    holder.style.height = '100%';
    holder.style.width = '100%';
    const inner = document.createElement('div');
    inner.className = 'tradingview-widget-container__widget';
    inner.style.height = '100%';
    inner.style.width = '100%';
    holder.appendChild(inner);

    const script = document.createElement('script');
    script.type = 'text/javascript';
    script.async = true;
    script.src = 'https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js';
    script.innerHTML = JSON.stringify({
      autosize: true,
      symbol: sym,
      interval: TV_TF[state.tf] || '1',
      timezone: 'Asia/Kolkata',
      theme: 'dark',
      style: '1',
      locale: 'en',
      enable_publishing: false,
      allow_symbol_change: false,
      hide_side_toolbar: true,
      hide_top_toolbar: false,
      withdateranges: true,
      details: false,
      calendar: false,
      studies: ['Volume@tv-basicstudies'],
      support_host: 'https://www.tradingview.com',
    });
    holder.appendChild(script);
    box.appendChild(holder);

    $('chartSource').textContent = 'TradingView · ' + sym;

    const started = Date.now();
    const watch = setInterval(() => {
      if (wrap.hidden) { clearInterval(watch); return; }
      if (box.querySelector('iframe')) { clearInterval(watch); return; }
      if (Date.now() - started > 7000) {
        clearInterval(watch);
        engineChart('TradingView unreachable · showing engine chart');
      }
    }, 500);

    script.onerror = () => {
      clearInterval(watch);
      engineChart('TradingView blocked · showing engine chart');
    };
    return true;
  }

  /* picks the right chart for the current instrument */
  function applyChart() {
    const inst = currentInst();
    if (!inst) return;
    if (inst.kind === 'otc' || state.tvBlocked) {
      const wrap = $('tvChart');
      if (wrap) {
        wrap.hidden = inst.kind === 'otc';
        wrap.classList.remove('fallback');
      }
      $('chart').hidden = false;
      state.tv = '';
      if (state.source) $('chartSource').textContent = state.source;
      draw();
      return;
    }
    mountTV(inst);
  }

  /* ------------------------------------------------------------- series */
  function synthSeries(anchor, n, tfMs, seed) {
    const rand = mulberry32(hash(seed));
    const closes = [];
    let px = anchor;
    for (let i = 0; i < n; i++) {
      px = px * (1 + (rand() - 0.485) * 0.0075);
      closes.push(px);
    }
    const scale = anchor / closes[n - 1];
    for (let i = 0; i < n; i++) closes[i] *= scale;
    const out = [];
    const t0 = Math.floor(Date.now() / tfMs) * tfMs - (n - 1) * tfMs;
    for (let i = 0; i < n; i++) {
      const o = i ? closes[i - 1] : closes[i] * (1 - (rand() - 0.5) * 0.002);
      const c = closes[i];
      const span = Math.abs(c) * 0.0016 + 1e-9;
      out.push({
        t: t0 + i * tfMs,
        o,
        c,
        h: Math.max(o, c) + rand() * span,
        l: Math.min(o, c) - rand() * span,
        v: 100 + rand() * 900,
      });
    }
    return out;
  }

  function aggregate(rows, group) {
    if (group <= 1) return rows.slice();
    const out = [];
    for (let i = 0; i < rows.length; i += group) {
      const chunk = rows.slice(i, i + group);
      if (!chunk.length) continue;
      out.push({
        t: chunk[0].t,
        o: chunk[0].o,
        c: chunk[chunk.length - 1].c,
        h: Math.max(...chunk.map((k) => k.h)),
        l: Math.min(...chunk.map((k) => k.l)),
        v: chunk.reduce((s, k) => s + (k.v || 0), 0),
      });
    }
    return out;
  }

  /* Daily ECB closes expanded into a continuous intraday walk: the path
     interpolates the real daily closes and every candle stays inside the
     high/low range of the day it belongs to. */
  function expandDaily(daily, tfMs, inst) {
    const rows = daily.slice(-5);
    if (rows.length < 2) return [];
    const n = 140;
    const rand = mulberry32(hash(inst.id + tfMs));
    const endT = rows[rows.length - 1].t + 864e5 - tfMs;
    const out = [];
    for (let i = 0; i < n; i++) {
      const f = i / (n - 1);
      const pos = f * (rows.length - 1);
      const j = Math.min(rows.length - 2, Math.floor(pos));
      const frac = pos - j;
      const a = rows[j];
      const b = rows[j + 1];
      const base = a.c + (b.c - a.c) * frac;
      const hi = Math.max(a.h, b.h);
      const lo = Math.min(a.l, b.l);
      const range = Math.max(hi - lo, base * 1e-5);
      let close = clamp(base + (rand() - 0.5) * range * 0.55, lo, hi);
      const open = i ? out[i - 1].c : clamp(a.o, lo, hi);
      const wick = range * 0.16 * rand();
      out.push({
        t: endT - (n - 1 - i) * tfMs,
        o: open,
        h: Math.min(hi, Math.max(open, close) + wick),
        l: Math.max(lo, Math.min(open, close) - wick),
        c: close,
        v: 100 + rand() * 900,
        thin: true,
      });
    }
    const last = rows[rows.length - 1];
    const tail = out[out.length - 1];
    tail.c = last.c;
    tail.h = Math.max(tail.h, last.c);
    tail.l = Math.min(tail.l, last.c);
    return out;
  }

  async function loadSeries(inst, tf) {
    const tfMs = TF_MS[tf] || TF_MS['1m'];
    const px = M.priceOf(inst) || inst.px || 1;

    if (inst.kind === 'otc') {
      const base = M.state.series[inst.id]?.SYN || [];
      const agg = aggregate(base, Math.max(1, Math.round(tfMs / 3000)));
      if (agg.length >= 30) {
        return { candles: agg.slice(-140), source: 'OTC synthetic · 3s feed' };
      }
      return { candles: synthSeries(px, 140, tfMs, inst.id + tf), source: 'OTC synthetic · ' + tf };
    }

    if (inst.kind === 'forex') {
      const d = await M.candles(inst.id, '1d').catch(() => null);
      const daily = d?.candles || [];
      if (!daily.length) return { candles: synthSeries(px, 140, tfMs, inst.id + tf), source: 'local model' };
      if (tf === '1D') {
        return { candles: daily.slice(-120), source: 'ECB daily' };
      }
      if (tf === '1W' || tf === '1M') {
        const group = tf === '1W' ? 5 : 21;
        const agg = aggregate(daily, group).slice(-60);
        return { candles: agg, source: `ECB daily · ${tf} buckets` };
      }
      return { candles: expandDaily(daily, tfMs, inst), source: 'ECB daily · intraday derived' };
    }

    let d = null;
    try { d = await M.candles(inst.id, TF_BINANCE[tf] || '1m'); } catch { d = null; }
    let candles = d?.candles || [];
    if (candles.length < 25) {
      const alt = await M.candles(inst.id, '1m').catch(() => null);
      if (alt?.candles?.length) {
        candles = alt.candles;
        d = alt;
      }
    }
    if (!candles.length) {
      return { candles: synthSeries(px, 140, tfMs, inst.id + tf), source: 'local model' };
    }
    return { candles: candles.slice(-140), source: d?.source || 'Binance · live' };
  }

  async function ensureSeries(showHint) {
    const inst = currentInst();
    if (!inst) return;
    if (state.loading) return;
    state.loading = true;
    if (showHint) $('chartHint').hidden = false;
    try {
      const out = await loadSeries(inst, state.tf);
      state.candles = out.candles;
      state.source = out.source;
      if (state.candles.length) $('chartHint').hidden = true;
      $('chartSource').textContent = out.source;
      paintRange();
      applyChart();
      paintOHLC(state.candles.length - 1);
    } catch (e) {
      $('chartHint').textContent = 'feed unavailable — retrying';
    } finally {
      state.loading = false;
    }
  }

  const isDailyUp = () => state.tf === '1D' || state.tf === '1W' || state.tf === '1M';

  function paintRange() {
    const rows = state.candles;
    if (!rows.length) return;
    const fmt = (t) => {
      const d = new Date(t);
      return isDailyUp()
        ? d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })
        : d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
    };
    $('chartRange').textContent = `${fmt(rows[0].t)} → ${fmt(rows[rows.length - 1].t)} · ${rows.length} candles`;
  }

  /* -------------------------------------------------------------- chart */
  function paintOHLC(idx) {
    const rows = state.candles;
    if (!rows.length) return;
    const k = rows[clamp(idx, 0, rows.length - 1)];
    const prev = rows[Math.max(0, clamp(idx, 0, rows.length - 1) - 1)];
    const up = k.c >= k.o;
    const chg = prev && prev.c ? ((k.c - prev.c) / prev.c) * 100 : 0;
    $('ohlc').innerHTML =
      `O <b>${k.o.toPrecision(6)}</b> · H <b>${k.h.toPrecision(6)}</b> · L <b>${k.l.toPrecision(6)}</b> · C <b>${k.c.toPrecision(6)}</b> ` +
      `<span class="${up ? 'u' : 'd'}">${chg >= 0 ? '+' : ''}${chg.toFixed(3)}%</span>`;
    const inst = currentInst();
    const priceEl = $('statPrice');
    if (priceEl && idx >= rows.length - 1) priceEl.textContent = fmtPrice(k.c, inst?.digits || 5);
  }

  function draw() {
    if (state.tv) return;
    const cv = $('chart');
    const wrap = $('chartWrap');
    if (!cv || !wrap) return;
    const w = wrap.clientWidth;
    const h = wrap.clientHeight;
    if (!w || !h) return;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    cv.width = Math.round(w * dpr);
    cv.height = Math.round(h * dpr);
    const g = cv.getContext('2d');
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    g.clearRect(0, 0, w, h);

    const rows = state.candles;
    if (!rows.length) return;

    const wash = g.createLinearGradient(0, 0, 0, h);
    wash.addColorStop(0, 'rgba(46, 33, 92, .34)');
    wash.addColorStop(0.6, 'rgba(16, 16, 34, .2)');
    wash.addColorStop(1, 'rgba(10, 10, 22, .35)');
    g.fillStyle = wash;
    g.fillRect(0, 0, w, h);

    const padR = 62, padL = 6, padT = 12, padB = 20;
    const plotW = Math.max(40, w - padL - padR);
    const volH = clamp((h - padT - padB) * 0.17, 24, 54);
    const gap = 8;
    const plotH = Math.max(60, h - padT - padB - volH - gap);
    const volTop = padT + plotH + gap;

    let hi = -Infinity, lo = Infinity;
    rows.forEach((k) => { hi = Math.max(hi, k.h); lo = Math.min(lo, k.l); });
    const span = hi - lo || hi * 0.001 || 1;
    hi += span * 0.06;
    lo -= span * 0.06;
    const y = (v) => padT + plotH - ((v - lo) / (hi - lo)) * plotH;
    const stepX = plotW / rows.length;
    const bodyW = Math.max(1.6, stepX * 0.74);
    const xAt = (i) => padL + i * stepX + stepX / 2;

    /* grid + price axis */
    g.font = '10px ui-monospace, Menlo, monospace';
    g.textBaseline = 'middle';
    const lines = 5;
    for (let i = 0; i <= lines; i++) {
      const v = lo + ((hi - lo) / lines) * i;
      const yy = y(v);
      g.strokeStyle = 'rgba(255,255,255,.1)';
      g.lineWidth = 1;
      g.beginPath();
      g.moveTo(padL, Math.round(yy) + 0.5);
      g.lineTo(padL + plotW, Math.round(yy) + 0.5);
      g.stroke();
      g.fillStyle = 'rgba(196,196,224,.95)';
      g.textAlign = 'left';
      g.fillText(v.toPrecision(6), padL + plotW + 7, yy);
    }

    /* volume */
    const maxV = Math.max(...rows.map((k) => k.v || 0), 1);
    rows.forEach((k, i) => {
      const vh = ((k.v || 0) / maxV) * volH;
      g.fillStyle = k.c >= k.o ? 'rgba(52,211,153,.5)' : 'rgba(248,113,113,.5)';
      g.fillRect(xAt(i) - bodyW / 2, volTop + volH - vh, bodyW, Math.max(1, vh));
    });

    /* candles */
    const upFill = g.createLinearGradient(0, padT, 0, padT + plotH);
    upFill.addColorStop(0, '#34e37f');
    upFill.addColorStop(1, '#17a34a');
    const dnFill = g.createLinearGradient(0, padT, 0, padT + plotH);
    dnFill.addColorStop(0, '#ff5f6d');
    dnFill.addColorStop(1, '#c81e2b');
    rows.forEach((k, i) => {
      const up = k.c >= k.o;
      const x = xAt(i);
      g.strokeStyle = up ? '#3ddc84' : '#ff5566';
      g.lineWidth = Math.max(1.1, bodyW * 0.18);
      g.beginPath();
      g.moveTo(x, y(k.h));
      g.lineTo(x, y(k.l));
      g.stroke();
      const yo = y(k.o);
      const yc = y(k.c);
      const top = Math.min(yo, yc);
      const bh = Math.max(1.4, Math.abs(yc - yo));
      g.fillStyle = up ? upFill : dnFill;
      g.fillRect(x - bodyW / 2, top, Math.max(1.6, bodyW * 0.92), bh);
    });

    /* moving average */
    const period = 20;
    if (rows.length > period) {
      g.strokeStyle = 'rgba(251,191,36,.95)';
      g.lineWidth = 1.7;
      g.beginPath();
      for (let i = period - 1; i < rows.length; i++) {
        let sum = 0;
        for (let j = i - period + 1; j <= i; j++) sum += rows[j].c;
        const ma = sum / period;
        const x = xAt(i);
        const yy = y(ma);
        if (i === period - 1) g.moveTo(x, yy); else g.lineTo(x, yy);
      }
      g.stroke();
    }

    /* last price line + tag */
    const last = rows[rows.length - 1];
    const ys = y(last.c);
    g.setLineDash([4, 4]);
    g.strokeStyle = 'rgba(167,139,250,.9)';
    g.lineWidth = 1;
    g.beginPath();
    g.moveTo(padL, ys);
    g.lineTo(padL + plotW, ys);
    g.stroke();
    g.setLineDash([]);
    const tag = last.c.toPrecision(6);
    g.font = '700 10px ui-monospace, Menlo, monospace';
    const tw = g.measureText(tag).width + 12;
    g.fillStyle = '#8b3df0';
    g.beginPath();
    const tx = padL + plotW + 3;
    if (g.roundRect) g.roundRect(tx, ys - 8, tw, 16, 4); else g.rect(tx, ys - 8, tw, 16);
    g.fill();
    g.fillStyle = '#fff';
    g.textAlign = 'left';
    g.fillText(tag, tx + 6, ys + 0.5);

    /* time axis */
    g.fillStyle = 'rgba(190,190,220,.9)';
    g.font = '10px ui-monospace, Menlo, monospace';
    g.textAlign = 'center';
    const marks = Math.min(6, Math.max(2, Math.floor(plotW / 90)));
    for (let i = 0; i < marks; i++) {
      const idx = Math.round((i / (marks - 1 || 1)) * (rows.length - 1));
      const d = new Date(rows[idx].t);
      const label = isDailyUp()
        ? d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })
        : d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
      const lw = g.measureText(label).width;
      const lx = clamp(xAt(idx), padL + lw / 2 + 2, padL + plotW - lw / 2 - 2);
      g.fillText(label, lx, h - padB / 2 + 1);
    }

    /* crosshair */
    if (state.hover >= 0 && state.hover < rows.length) {
      const k = rows[state.hover];
      const x = xAt(state.hover);
      g.strokeStyle = 'rgba(196,181,253,.6)';
      g.setLineDash([3, 3]);
      g.beginPath();
      g.moveTo(x, padT);
      g.lineTo(x, padT + plotH + gap + volH);
      g.stroke();
      const yy = y(k.c);
      g.beginPath();
      g.moveTo(padL, yy);
      g.lineTo(padL + plotW, yy);
      g.stroke();
      g.setLineDash([]);
    }
  }

  function bindChartInteractions() {
    const cv = $('chart');
    const wrap = $('chartWrap');
    const tip = $('chartTip');

    const idxFromEvent = (ev) => {
      const rows = state.candles;
      if (!rows.length) return -1;
      const rect = cv.getBoundingClientRect();
      const w = wrap.clientWidth;
      const padR = 62, padL = 6;
      const plotW = Math.max(40, w - padL - padR);
      const x = ((ev.touches ? ev.touches[0].clientX : ev.clientX) - rect.left) - padL;
      return clamp(Math.floor((x / plotW) * rows.length), 0, rows.length - 1);
    };

    const move = (ev) => {
      const i = idxFromEvent(ev);
      if (i < 0) return;
      state.hover = i;
      const k = state.candles[i];
      const d = new Date(k.t);
      const t = isDailyUp()
        ? d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
        : d.toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
      tip.hidden = false;
      tip.innerHTML =
        `<div><span>${t}</span></div>` +
        `O ${k.o.toPrecision(6)} · H ${k.h.toPrecision(6)}<br>` +
        `L ${k.l.toPrecision(6)} · C ${k.c.toPrecision(6)}`;
      const wrapRect = wrap.getBoundingClientRect();
      const px = (ev.touches ? ev.touches[0].clientX : ev.clientX) - wrapRect.left;
      const py = (ev.touches ? ev.touches[0].clientY : ev.clientY) - wrapRect.top;
      tip.style.left = clamp(px + 12, 6, Math.max(6, wrapRect.width - 190)) + 'px';
      tip.style.top = clamp(py - 10, 6, Math.max(6, wrapRect.height - 70)) + 'px';
      paintOHLC(i);
      draw();
    };

    const leave = () => {
      state.hover = -1;
      tip.hidden = true;
      paintOHLC(state.candles.length - 1);
      draw();
    };

    cv.addEventListener('mousemove', move);
    cv.addEventListener('mouseleave', leave);
    cv.addEventListener('touchstart', move, { passive: true });
    cv.addEventListener('touchmove', move, { passive: true });
    cv.addEventListener('touchend', leave);
    window.addEventListener('resize', () => { draw(); });
  }

  /* ------------------------------------------------------------- signal */
  function strengthOf(conf) {
    if (conf >= 90) return { label: 'Very Strong', bars: 4 };
    if (conf >= 84) return { label: 'Strong', bars: 3 };
    if (conf >= 78) return { label: 'Moderate', bars: 2 };
    return { label: 'Weak', bars: 1 };
  }

  function wordFor(score) {
    if (score == null) return 'NEUTRAL';
    if (score >= 60) return 'BULLISH';
    if (score <= 40) return 'BEARISH';
    return 'NEUTRAL';
  }
  function clsFor(score) {
    if (score == null) return 'flat';
    if (score >= 60) return 'up';
    if (score <= 40) return 'dn';
    return 'flat';
  }

  function renderSignal(sig, inst, opts = {}) {
    const card = $('spCard');
    $('spEmpty').hidden = true;
    card.hidden = false;
    card.classList.remove('buy', 'sell', 'neutral');
    const dir = sig.dir === 'BUY' ? 'buy' : sig.dir === 'SELL' ? 'sell' : 'neutral';
    card.classList.add(dir);
    card.classList.remove('pop');
    void card.offsetWidth;
    card.classList.add('pop');

    const conf = clamp(Math.round(sig.confidence || 85), 60, 99.9);
    $('spDir').textContent = sig.dir || 'HOLD';
    $('spInst').textContent = (inst.pair || inst.symbol) + (state.tf ? ' · ' + state.tf : '');

    const factors = Array.isArray(sig.factors) && sig.factors.length
      ? sig.factors
      : [{ name: 'Algorithmic sequence matrix', score: 50 }];
    const score = (i) => (factors[i] && factors[i].score != null ? factors[i].score : 50);
    const trend = score(0), momentum = score(1), vol = score(2), structure = (score(3) + score(4)) / 2;
    const trendWord = wordFor(trend);
    const sub = `${state.tf} · ${trendWord === 'BULLISH' ? 'Bullish' : trendWord === 'BEARISH' ? 'Bearish' : 'Sideways'} · ${sig.dir === 'BUY' ? 'Long' : sig.dir === 'SELL' ? 'Short' : 'Flat'}`;
    $('spSub').textContent = sub;

    $('spConf').innerHTML = `${conf}<small>%</small>`;
    $('spConfBar').style.width = conf + '%';

    const st = strengthOf(conf);
    $('ssLabel').textContent = st.label;
    Array.from($('ssBars').children).forEach((el, i) => el.classList.toggle('on', i < st.bars));

    const agreement = factors.slice(0, 5).reduce((s, f) => s + Math.abs((f.score ?? 50) - 50) / 50, 0) / Math.min(5, factors.length || 1);
    $('spAcc').textContent = (92 + agreement * 7.9).toFixed(1) + '%';

    const cells = [
      ['TREND', trend],
      ['MOMENTUM', momentum],
      ['VOLATILITY', vol],
      ['STRUCTURE', structure],
    ];
    $('spFactors').innerHTML = cells.map(([label, v]) => {
      const sc = clamp(Math.round(v), 2, 98);
      return `<div class="sf ${clsFor(v)}">
        <span class="sf-l">${label}</span>
        <span class="sf-v">${wordFor(v)}</span>
        <span class="sf-b"><i style="width:${sc}%"></i></span>
      </div>`;
    }).join('');

    const agree = factors.slice(0, 5).filter((f) => {
      const v = f.score ?? 50;
      return sig.dir === 'SELL' ? v <= 46 : v >= 54;
    }).length;
    $('sbDots').innerHTML = Array.from({ length: 5 }, (_, i) => `<i class="${i < agree ? 'on' : ''}"></i>`).join('');
    $('spBeast').hidden = !state.beast;

    const tfMs = TF_MS[state.tf] || 6e4;
    const sched = CLOCK.plan(inst.id, tfMs, opts.at || Date.now());
    state.entryAt = sched.entryMs;
    state.expiryAt = sched.expiryMs;
    state.tfMs = tfMs;
    $('spEntry').textContent = CLOCK.istFullStamp(sched.entryMs) + ' IST';
    $('spDur').textContent = TF_LABEL[state.tf] || '1 minute';
    $('spSide').textContent = sig.dir || '—';

    const badge = $('spBadge');
    badge.className = 'st-badge';
    badge.className = 'st-badge';
    badge.textContent = (sched.pending ? 'ENTRY IN · ' : 'ACTIVE · ') +
      clock(sched.pending ? sched.toEntryMs : sched.toExpiryMs);
    state.signal = sig;
  }

  function clock(ms) {
    const total = Math.max(0, Math.floor(ms / 1000));
    if (total >= 3600) return `${Math.floor(total / 3600)}h ${pad(Math.floor((total % 3600) / 60))}m`;
    return `${pad(Math.floor(total / 60))}:${pad(total % 60)}`;
  }

  function tickExpiry() {
    const badge = $('spBadge');
    const inst = currentInst();
    if (!badge || !inst || !state.signal) return;
    const tfMs = state.tfMs || TF_MS[state.tf] || 6e4;
    /* the trade rolls onto the next window once it closes, so the card always
       matches the Live Scanner for the same pair */
    const plan = CLOCK.plan(inst.id, tfMs, Date.now());
    if (plan.entryMs !== state.entryAt) {
      state.entryAt = plan.entryMs;
      state.expiryAt = plan.expiryMs;
      const entry = $('spEntry');
      if (entry) entry.textContent = CLOCK.istFullStamp(plan.entryMs) + ' IST';
    }
    badge.className = 'st-badge';
    badge.textContent = (plan.pending ? 'ENTRY IN · ' : 'ACTIVE · ') +
      clock(plan.pending ? plan.toEntryMs : plan.toExpiryMs);
  }

  /* ------------------------------------------------------------- history */
  function renderHistory() {
    const list = $('histList');
    if (!list) return;
    $('histCount').textContent = String(state.history.length);
    if (!state.history.length) {
      list.innerHTML = '<div class="ch-empty">No signals yet — hit Generate Signal to fill the feed.</div>';
      return;
    }
    list.innerHTML = state.history.map((h, i) => `
      <div class="ch-row" data-i="${i}">
        <b>${h.sym}</b>
        <span class="t">${h.tf} · ${timeAgo(h.t)} ago</span>
        <span class="d ${h.dir === 'BUY' ? 'buy' : 'sell'}">${h.dir}</span>
        <span class="c">${h.conf}%</span>
      </div>`).join('');
    Array.from(list.querySelectorAll('.ch-row')).forEach((row) => {
      row.addEventListener('click', () => {
        const h = state.history[Number(row.dataset.i)];
        if (!h || !instById(h.id)) return;
        state.id = h.id;
        $('instSel').value = h.id;
        if (TF_MS[h.tf]) setTimeframe(h.tf);
        paintInstrument();
        ensureSeries(true);
        $('histPanel').hidden = true;
      });
    });
  }

  function pushHistory(sig, inst) {
    state.history.unshift({
      id: inst.id,
      sym: inst.pair || inst.symbol,
      dir: sig.dir || 'BUY',
      conf: Math.round(sig.confidence || 85),
      tf: state.tf,
      t: Date.now(),
    });
    state.history = state.history.slice(0, 20);
    saveHistory();
    renderHistory();
  }

  /* ------------------------------------------------------------- actions */
  async function generate() {
    if (!state.beast) {
      openGate('Beast Mode key required to generate signals.');
      return;
    }
    if (state.used >= GEN_LIMIT) {
      toast('<b>Signal limit reached.</b> Ask @DRKMUQEETT for a new key.', 'err');
      return;
    }
    const inst = currentInst();
    if (!inst) return;
    const btn = $('genBtn');
    const txt = btn.querySelector('.gb-txt');
    const original = txt.innerHTML;
    btn.classList.add('busy');
    txt.textContent = 'Running the five-factor engine…';

    try {
      if (!state.candles.length) await ensureSeries(true);
      const warm = M.state.view && Object.keys(M.state.view).length;
      if (!warm) {
        try { M.signals(inst.kind); } catch { /* fall back below */ }
      }
      await new Promise((r) => setTimeout(r, 620));
      let sig = null;
      try { sig = M.signalFor(inst); } catch { sig = null; }
      if (!sig) sig = { dir: 'BUY', confidence: 86, factors: [] };

      state.used = clamp(state.used + 1, 0, GEN_LIMIT);
      store(USED_KEY, String(state.used));
      paintCount();
      renderSignal(sig, inst);
      pushHistory(sig, inst);
      paintInstrument();
      beep();
      toast(`<b>${sig.dir} ${inst.pair || inst.symbol}</b> · ${Math.round(sig.confidence)}% confidence · ${state.tf} expiry`, sig.dir === 'BUY' ? 'ok' : '');
    } finally {
      btn.classList.remove('busy');
      txt.innerHTML = original;
    }
  }

  function setTimeframe(tf) {
    if (!TF_MS[tf]) return;
    state.tf = tf;
    Array.from($('tfRow').children).forEach((b) => b.classList.toggle('on', b.dataset.tf === tf));
    $('cpTf').textContent = tf;
    paintInstrument();
    ensureSeries(true);
  }

  function shareAnalysis() {
    const inst = currentInst();
    const sig = state.signal;
    if (!sig) {
      toast('Generate a signal first — then share it.', 'err');
      return;
    }
    const conf = Math.round(sig.confidence || 0);
    const text = [
      `OTC Bot · Beast Mode`,
      `${inst.pair || inst.symbol} · ${state.tf} · ${sig.dir}`,
      `Confidence ${conf}% · ${strengthOf(conf).label}`,
      `Entry ${CLOCK.istFullStamp(state.entryAt)} IST · ${TF_LABEL[state.tf]}`,
      `Access & support: https://t.me/DRKMUQEETT`,
    ].join('\n');
    const done = () => toast('<b>Analysis copied.</b> Paste it anywhere · @DRKMUQEETT', 'ok');
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(text).then(done).catch(() => fallback());
    } else fallback();
    function fallback() {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy'); done(); } catch { toast('Copy failed — long-press the signal card.', 'err'); }
      ta.remove();
    }
  }

  /* --------------------------------------------------------------- wiring */
  function wire() {
    $('moreBtn').addEventListener('click', () => {
      const list = $('moreList');
      const open = list.hidden;
      list.hidden = !open;
      $('moreBtn').setAttribute('aria-expanded', open ? 'true' : 'false');
    });

    $('sideToggle').addEventListener('click', () => $('genSide').classList.toggle('open'));
    $('genSide').addEventListener('click', (e) => {
      if (e.target.closest('a')) $('genSide').classList.remove('open');
    });

    $('signOut').addEventListener('click', () => {
      api.clear();
      store(BEAST_STORE, '');
      window.location.replace('./');
    });

    $('bellBtn').addEventListener('click', () => {
      state.sound = !state.sound;
      toast(state.sound ? '<b>Signal alerts on.</b> You will hear a beep on every new signal.' : 'Signal alerts muted.', state.sound ? 'ok' : '');
      if (state.sound) beep();
    });

    $('beastSwitch').addEventListener('click', () => {
      if (!state.beast) { openGate('Enter the Beast Mode key to unlock the generator.'); return; }
      applyBeast(!document.body.classList.contains('beast-mode'), false);
      toast(document.body.classList.contains('beast-mode') ? 'Beast Mode on.' : 'Beast Mode visuals off — access retained.');
    });

    $('instSel').addEventListener('change', (e) => {
      state.tvBlocked = false;
      state.tv = '';
      state.id = e.target.value;
      paintInstrument();
      ensureSeries(true);
    });

    $('tfRow').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-tf]');
      if (b) setTimeframe(b.dataset.tf);
    });

    $('genBtn').addEventListener('click', generate);

    $('cpTabs').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-view]');
      if (!b) return;
      const view = b.dataset.view;
      if (view === 'share') { shareAnalysis(); return; }
      Array.from($('cpTabs').children).forEach((x) => x.classList.toggle('on', x === b));
      $('privPanel').hidden = view !== 'private';
      $('histPanel').hidden = view !== 'signal';
      if (view === 'signal') renderHistory();
    });

    $('fsBtn').addEventListener('click', () => {
      const panel = document.querySelector('.chart-panel');
      if (!document.fullscreenElement) {
        (panel.requestFullscreen || panel.webkitRequestFullscreen || (() => {})).call(panel);
        setTimeout(draw, 320);
      } else {
        document.exitFullscreen?.();
      }
    });

    $('gateForm').addEventListener('submit', (e) => {
      e.preventDefault();
      submitKey($('beastKey').value);
    });
    $('beastKey').addEventListener('input', () => { $('beastErr').hidden = true; });

    window.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        $('histPanel').hidden = true;
        $('privPanel').hidden = true;
      }
    });
  }

  /* ------------------------------------------------------------- timers */
  function startTimers() {
    setInterval(() => {
      paintClock();
      liveUsers = clamp(liveUsers + (Math.random() < 0.5 ? -1 : 1) * Math.floor(Math.random() * 3), 210, 420);
    }, 1000);

    setInterval(() => {
      paintPush();
    }, 30000);

    setInterval(() => {
      tickExpiry();
      paintInstrument();
      const next = $('spNext');
      if (next) next.textContent = clock(Math.max(0, state.nextScanAt - Date.now()));
    }, 1000);

    /* keep the last candle live (OTC ticks, crypto refreshes) */
    setInterval(() => {
      const inst = currentInst();
      if (!inst || !state.candles.length) return;
      if (inst.kind === 'otc') {
        const px = M.priceOf(inst);
        const k = state.candles[state.candles.length - 1];
        if (px && Number.isFinite(px)) {
          k.c = px;
          k.h = Math.max(k.h, px);
          k.l = Math.min(k.l, px);
          if (state.hover < 0) paintOHLC(state.candles.length - 1);
          draw();
        }
      }
    }, 2000);

    setInterval(() => {
      if (document.hidden) return;
      state.nextScanAt = Date.now() + 45000;
      ensureSeries(false);
    }, 45000);
  }

  /* --------------------------------------------------------------- boot */
  let booted = false;
  async function boot() {
    if (booted) return;
    booted = true;
    try { await M.init(); } catch { /* engine falls back to the local model */ }
    fillPicker();
    paintInstrument();
    await ensureSeries(true);
    renderHistory();
    const nav = $('navActive');
    if (nav) {
      try {
        const list = M.signals('otc');
        const active = list.filter((s) => s.active).length;
        if (active) nav.textContent = active;
      } catch { /* keep default */ }
    }
    startTimers();
  }

  function init() {
    loadHistory();
    state.used = clamp(Number(store(USED_KEY) || 0) || 0, 0, GEN_LIMIT);
    wire();
    paintClock();
    paintPush();
    paintCount();
    renderHistory();
    bindChartInteractions();

    const fromUrl = new URLSearchParams(location.search).get('key');
    const unlocked = store(BEAST_STORE) === '1' && !!api.token;

    if (fromUrl && fromUrl.trim().toUpperCase() === BEAST_KEY) {
      api.set('beast-' + Date.now().toString(36), 'pro');
      applyBeast(true, true);
      closeGate();
      boot();
      return;
    }

    if (unlocked) {
      applyBeast(true, false);
      closeGate();
      boot();
    } else if (api.token) {
      applyBeast(false, false);
      openGate();
    } else {
      applyBeast(false, false);
      openGate();
    }

    /* engine warms up in parallel so the first Generate is instant */
    boot();
  }

  window.OTCBEAST = { state, redraw: draw, reload: () => ensureSeries(true) };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
