/* Terminal dashboard: SSE feed, canvas chart, scanner, diagnostics. */
(function () {
  const { api, get, clock, countdown, price: fmtPrice, pad } = window.OTC;

  if (!api.token) {
    window.location.replace('./#access');
    return;
  }

  const $ = (id) => document.getElementById(id);
  const state = {
    signals: [],
    cards: new Map(),
    chartPair: 'EUR/USD',
    chartDecimals: 5,
    candles: [],
    plan: api.plan || 'trial',
    sound: true,
    trialLimit: 5,
    trialSeen: 0,
    paywalled: false,
    connectedAt: Date.now(),
    lastPrice: null,
    activeSignal: null,
  };

  /* ------------------------------------------------------------------ */
  /* Toast                                                               */
  /* ------------------------------------------------------------------ */
  function toast(html, ms = 4200) {
    const wrap = $('toasts');
    if (!wrap) return;
    const el = document.createElement('div');
    el.className = 'toast';
    el.innerHTML = html;
    wrap.appendChild(el);
    setTimeout(() => {
      el.style.transition = 'opacity .35s ease, transform .35s ease';
      el.style.opacity = '0';
      el.style.transform = 'translateY(8px)';
      setTimeout(() => el.remove(), 360);
    }, ms);
    while (wrap.children.length > 4) wrap.firstChild.remove();
  }

  /* ------------------------------------------------------------------ */
  /* Sound                                                               */
  /* ------------------------------------------------------------------ */
  let audioCtx = null;
  function beep(direction) {
    if (!state.sound) return;
    try {
      audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
      if (audioCtx.state === 'suspended') audioCtx.resume();
      const now = audioCtx.currentTime;
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(direction === 'CALL' ? 880 : 520, now);
      osc.frequency.exponentialRampToValueAtTime(direction === 'CALL' ? 1320 : 380, now + 0.12);
      gain.gain.setValueAtTime(0.0001, now);
      gain.gain.exponentialRampToValueAtTime(0.16, now + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.28);
      osc.connect(gain).connect(audioCtx.destination);
      osc.start(now);
      osc.stop(now + 0.3);
    } catch (_) { /* audio unavailable */ }
  }

  const soundBtn = $('soundBtn');
  if (soundBtn) {
    soundBtn.classList.add('on');
    soundBtn.addEventListener('click', () => {
      state.sound = !state.sound;
      soundBtn.classList.toggle('on', state.sound);
      toast(state.sound ? 'Alert sound <span class="ok">ON</span>' : 'Alert sound muted', 1800);
      if (state.sound) beep('CALL');
    });
  }

  /* ------------------------------------------------------------------ */
  /* Log                                                                 */
  /* ------------------------------------------------------------------ */
  function log(text, cls) {
    const el = $('log');
    if (!el) return;
    const row = document.createElement('div');
    const t = new Date();
    row.innerHTML = `<span class="t">${pad(t.getHours())}:${pad(t.getMinutes())}:${pad(t.getSeconds())}</span> ${cls ? `<span class="${cls}">${text}</span>` : text}`;
    el.prepend(row);
    while (el.children.length > 60) el.lastChild.remove();
  }

  /* ------------------------------------------------------------------ */
  /* Feed rendering                                                      */
  /* ------------------------------------------------------------------ */
  function signalCard(sig) {
    const card = document.createElement('article');
    card.className = `sig-card ${sig.direction.toLowerCase()}`;
    card.dataset.id = sig.id;
    card.innerHTML = `
      <div class="sig-top">
        <div style="min-width:0">
          <div class="sig-pair">${sig.label}</div>
          <div class="sig-tag">[${sig.tag}] · ${sig.timeframe}</div>
        </div>
        <div class="sig-dir ${sig.direction.toLowerCase()}">
          ${sig.direction}
          <small>${sig.confidence.toFixed(1)}%</small>
        </div>
      </div>
      <div class="sig-meta">
        <span data-role="timer-text">--:--</span>
        <span class="sep"></span>
        <span>${fmtPrice(sig.price, sig.label.includes('JPY') ? 3 : 5)}</span>
        <span class="sep"></span>
        <span data-role="status" class="sig-result pending">PENDING</span>
      </div>
      <div class="timer ${sig.direction.toLowerCase()}"><i data-role="timer-bar" style="width:100%"></i></div>
    `;
    if (sig.status && sig.status !== 'PENDING') {
      applyStatus(card, sig.status);
    }
    return card;
  }

  function applyStatus(card, status) {
    const chip = card.querySelector('[data-role="status"]');
    if (!chip) return;
    chip.className = `sig-result ${status.toLowerCase()}`;
    chip.textContent = status;
    const bar = card.querySelector('[data-role="timer-bar"]');
    if (bar && status !== 'PENDING') bar.style.width = '0%';
    card.classList.add('old');
  }

  function addSignal(sig) {
    if (state.signals.some((s) => s.id === sig.id)) return;
    state.signals.unshift(sig);
    const feed = $('feed');
    if (!feed) return;

    const empty = feed.querySelector('.feed-empty');
    if (empty) empty.remove();

    const card = signalCard(sig);
    feed.prepend(card);
    state.cards.set(sig.id, { card, sig, expiresAt: sig.expiresAt, entryAt: sig.entryAt });

    // keep the DOM light
    while (feed.children.length > 26) {
      const last = feed.lastElementChild;
      if (!last) break;
      state.cards.delete(last.dataset.id);
      last.remove();
    }

    // confidence / stats bookkeeping
    updateStats();
    if (sig.trialIndex) {
      state.trialSeen = sig.trialIndex;
      updateTrial(sig.trialIndex, sig.trialLimit || state.trialLimit);
    }
    log(`SIGNAL ${sig.id} <span class="${sig.direction === 'CALL' ? 'ok' : 'put'}">${sig.direction}</span> ${sig.label} @ ${sig.confidence.toFixed(1)}%`, 'ok');
    beep(sig.direction);
    if (window.innerWidth > 760) {
      toast(`<b>${sig.label}</b> · <span class="ok">${sig.direction} ${sig.confidence.toFixed(1)}%</span> · ${sig.tag}`);
    }

    // follow the newest signal on the chart
    if (!chartLocked) {
      setChartPair(sig.pair);
      state.activeSignal = sig;
    }
  }

  function updateStats() {
    const list = state.signals;
    if ($('feedCount')) $('feedCount').textContent = list.length;
    if ($('statSignals')) $('statSignals').textContent = list.length;
    const resolved = list.filter((s) => s.status === 'WIN' || s.status === 'LOSS');
    if ($('statWin')) {
      if (!resolved.length) {
        $('statWin').textContent = '—';
      } else {
        const wins = resolved.filter((s) => s.status === 'WIN').length;
        $('statWin').textContent = `${((wins / resolved.length) * 100).toFixed(1)}%`;
        $('statWin').className = 'green';
      }
    }
    if ($('statConf') && list.length) {
      const avg = list.reduce((a, s) => a + s.confidence, 0) / list.length;
      $('statConf').textContent = `${avg.toFixed(1)}%`;
    }
    if ($('sesSeen')) $('sesSeen').textContent = state.trialSeen || (state.plan === 'pro' ? list.length : 0);
  }

  /* ------------------------------------------------------------------ */
  /* Per-second countdowns                                               */
  /* ------------------------------------------------------------------ */
  setInterval(() => {
    const now = Date.now();
    for (const { card, sig, expiresAt, entryAt } of state.cards.values()) {
      const text = card.querySelector('[data-role="timer-text"]');
      const bar = card.querySelector('[data-role="timer-bar"]');
      if (!text || !bar) continue;

      if (sig.status !== 'PENDING') { text.textContent = 'CLOSED'; continue; }

      if (now < entryAt) {
        text.textContent = `ENTRY ${Math.ceil((entryAt - now) / 1000)}s`;
        text.style.color = 'var(--amber)';
        continue;
      }
      text.style.color = '';
      const remaining = expiresAt - now;
      text.textContent = countdown(remaining);
      bar.style.width = `${Math.max(0, (remaining / 60000) * 100)}%`;
    }

    const up = Math.floor((Date.now() - state.connectedAt) / 1000);
    if ($('uptime')) $('uptime').textContent = `${pad(Math.floor(up / 60))}:${pad(up % 60)}`;
    if ($('clock')) $('clock').textContent = clock();
    const ct = $('chartCandle');
    if (ct) {
      const d = new Date();
      ct.textContent = `candle ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(Math.floor(d.getSeconds() / 3) * 3)}`;
    }
  }, 1000);

  /* ------------------------------------------------------------------ */
  /* Trial handling                                                      */
  /* ------------------------------------------------------------------ */
  function updateTrial(seen, limit) {
    state.trialSeen = seen;
    state.trialLimit = limit || state.trialLimit;
    const banner = $('trialBanner');
    if (!banner || state.plan === 'pro') return;
    const left = Math.max(0, state.trialLimit - seen);
    banner.classList.add('show');
    $('trialText').textContent = `TRIAL — ${left} of ${state.trialLimit} signals remaining`;
    $('trialBar').style.width = `${(left / state.trialLimit) * 100}%`;
    if ($('sesSeen')) $('sesSeen').textContent = `${seen}/${state.trialLimit}`;
    if (left === 0) {
      $('trialText').textContent = 'TRIAL — signal budget exhausted';
      $('trialBanner').querySelector('a').textContent = 'Unlock Pro →';
    }
  }

  function showPaywall() {
    if (state.paywalled) return;
    state.paywalled = true;
    const m = $('paywall');
    if (m) m.classList.add('show');
  }

  const closePaywall = $('closePaywall');
  if (closePaywall) {
    closePaywall.addEventListener('click', () => $('paywall').classList.remove('show'));
  }

  /* ------------------------------------------------------------------ */
  /* SSE feed                                                            */
  /* ------------------------------------------------------------------ */
  function handleSnapshot(data) {
    state.plan = data.plan;
    if ($('connState')) $('connState').textContent = 'ONLINE';
    if ($('planPill')) {
      $('planPill').textContent = data.plan === 'pro' ? 'PRO · LIFETIME' : 'TRIAL';
      $('planPill').classList.toggle('trial', data.plan !== 'pro');
    }
    if ($('sesPlan')) $('sesPlan').textContent = data.plan === 'pro' ? 'PRO / LIFETIME' : 'EVALUATION';
    if ($('sesKey')) $('sesKey').textContent = data.plan === 'pro' ? 'DRK-PRO-DEMO' : 'FREE-5';
    if ($('sesFp')) $('sesFp').textContent = fingerprint();

    if (Array.isArray(data.candles) && data.pair !== state.chartPair) {
      state.candles = data.candles;
      state.chartDecimals = data.decimals || 5;
      drawChart();
    }
    if (Array.isArray(data.signals)) {
      // newest first from server
      data.signals.slice().reverse().forEach((s) => addSignal(s));
      // a snapshot can also carry resolved results (polling mode)
      data.signals.forEach((s) => {
        if (s.status === 'PENDING') return;
        const existing = state.signals.find((x) => x.id === s.id);
        if (existing && existing.status !== s.status) {
          existing.status = s.status;
          const entry = state.cards.get(s.id);
          if (entry) applyStatus(entry.card, s.status);
        }
      });
      updateStats();
    }
    if (data.plan === 'trial') {
      const limit = 5;
      const seen = data.remaining === null ? 0 : Math.max(0, limit - data.remaining);
      state.trialSeen = Math.max(state.trialSeen, seen);
      updateTrial(state.trialSeen, limit);
      if (data.remaining === 0) setTimeout(showPaywall, 900);
    }
    pollChart();
    pollScanner();
  }

  let gotHello = false;
  let pollTimer = null;

  function connect() {
    const url = `/api/feed?token=${encodeURIComponent(api.token)}`;
    let es;
    try {
      es = new EventSource(url);
    } catch (_) {
      startPolling();
      return;
    }

    es.addEventListener('hello', (ev) => {
      gotHello = true;
      const data = JSON.parse(ev.data);
      handleSnapshot(data);
      log('ENGINE HANDSHAKE OK · 41 PAIRS LOADED', 'ok');
      log(`SESSION PLAN ${data.plan.toUpperCase()} · WS LATENCY ${latency()} ms`);
      toast('Terminal connected · <span class="ok">engine online</span>');
    });

    es.addEventListener('signal', (ev) => {
      const sig = JSON.parse(ev.data);
      // ignore duplicates (reconnect replays history)
      if (state.signals.some((s) => s.id === sig.id)) return;
      addSignal(sig);
    });

    es.addEventListener('result', (ev) => {
      const sig = JSON.parse(ev.data);
      const existing = state.signals.find((s) => s.id === sig.id);
      if (existing) existing.status = sig.status;
      const entry = state.cards.get(sig.id);
      if (entry) applyStatus(entry.card, sig.status);
      updateStats();
      log(`RESULT ${sig.id} ${sig.status}`, sig.status === 'WIN' ? 'ok' : 'warn');
    });

    es.addEventListener('trial_end', () => {
      log('TRIAL BUDGET EXHAUSTED', 'warn');
      updateTrial(state.trialLimit, state.trialLimit);
      setTimeout(showPaywall, 1600);
    });

    es.addEventListener('paywall', () => {
      if (state.plan !== 'pro') showPaywall();
    });

    es.onerror = () => {
      if (!gotHello) return; // the retry timer below handles a dead stream
      if ($('connState')) $('connState').textContent = 'RECONNECTING';
    };
    es.onopen = () => {
      if ($('connState')) $('connState').textContent = 'ONLINE';
    };

    // Some proxies buffer event streams. If no handshake arrives, fall back to
    // plain HTTP polling so the dashboard still works.
    setTimeout(() => {
      if (!gotHello) {
        try { es.close(); } catch (_) { /* ignore */ }
        startPolling();
      }
    }, 8000);
  }

  function startPolling() {
    if (pollTimer) return;
    if ($('connState')) $('connState').textContent = 'POLLING';
    log('STREAM UNAVAILABLE · SWITCHED TO HTTP POLLING', 'warn');
    const tickPoll = async () => {
      try {
        const data = await get('/api/live');
        handleSnapshot(data);
        gotHello = true;
      } catch (_) { /* retry next cycle */ }
    };
    tickPoll();
    pollTimer = setInterval(tickPoll, 4000);
  }

  function fingerprint() {
    let fp = localStorage.getItem('otc.fp');
    if (!fp) {
      fp = 'HW-' + Math.random().toString(36).slice(2, 8).toUpperCase();
      try { localStorage.setItem('otc.fp', fp); } catch (_) { /* ignore */ }
    }
    return fp;
  }

  let lastLatency = 0;
  function latency() {
    return lastLatency;
  }

  /* ------------------------------------------------------------------ */
  /* Chart                                                               */
  /* ------------------------------------------------------------------ */
  const canvas = $('chart');
  const ctx = canvas ? canvas.getContext('2d') : null;
  let chartLocked = false;

  function setChartPair(pair, decimals) {
    if (!pair) return;
    state.chartPair = pair;
    if (decimals) state.chartDecimals = decimals;
    const labelEl = $('chartPair');
    if (labelEl) {
      labelEl.textContent = /^(BTC|ETH|SOL|XAU|XAG)/.test(pair) ? pair : `${pair} (OTC)`;
    }
    const otc = $('chartOtc');
    if (otc) otc.textContent = /^(BTC|ETH|SOL|XAU|XAG)/.test(pair) ? 'LIVE SYNTHETIC' : 'OTC SYNTHETIC';
    document.querySelectorAll('.scan-row').forEach((r) => {
      r.classList.toggle('active', r.dataset.pair === pair);
    });
    pollChart();
  }

  function activeSignalFor(pair) {
    return state.signals.find((s) => s.pair === pair && s.status === 'PENDING') || null;
  }

  async function pollChart() {
    try {
      const t0 = performance.now();
      const data = await get(`/api/candles?pair=${encodeURIComponent(state.chartPair)}`);
      lastLatency = Math.max(0, Math.round(performance.now() - t0));
      if ($('latency')) $('latency').textContent = `${lastLatency} ms`;
      if (data.pair !== state.chartPair) return;
      state.candles = data.candles;
      state.chartDecimals = data.decimals;
      state.lastPrice = data.price;
      renderChartHead(data);
      drawChart();
      if ($('chartUpdated')) $('chartUpdated').textContent = 'updated just now';
    } catch (_) { /* silent */ }
  }

  function renderChartHead(data) {
    const last = data.candles[data.candles.length - 1];
    const first = data.candles[0];
    const change = ((last.c - first.o) / first.o) * 100;
    if ($('chartPrice')) $('chartPrice').textContent = fmtPrice(data.price, data.decimals);
    const chg = $('chartChange');
    if (chg) {
      chg.textContent = `${change >= 0 ? '+' : ''}${change.toFixed(2)}%`;
      chg.className = `chart-change ${change >= 0 ? 'up' : 'down'}`;
    }
    const sig = activeSignalFor(data.pair);
    const tag = $('chartSignalTag');
    if (tag) {
      if (sig) {
        tag.textContent = `${sig.direction} ${sig.confidence.toFixed(1)}% · ${sig.tag}`;
        tag.className = `tag ${sig.direction === 'PUT' ? 'put' : ''}`;
      } else {
        tag.textContent = 'NO ACTIVE SIGNAL';
        tag.className = 'tag';
      }
    }
  }

  function drawChart() {
    if (!canvas || !ctx) return;
    const rect = canvas.getBoundingClientRect();
    if (!rect.width) return;
    const dpr = window.devicePixelRatio || 1;
    const W = Math.floor(rect.width);
    const H = Math.floor(rect.height);
    if (canvas.width !== W * dpr || canvas.height !== H * dpr) {
      canvas.width = W * dpr;
      canvas.height = H * dpr;
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, W, H);

    const candles = state.candles;
    if (!candles || candles.length < 2) {
      ctx.fillStyle = '#37454f';
      ctx.font = '11px ui-monospace, monospace';
      ctx.textAlign = 'center';
      ctx.fillText('awaiting candles…', W / 2, H / 2);
      return;
    }

    const axisW = 68;
    const padL = 10;
    const padT = 12;
    const padB = 22;
    const plotW = W - axisW - padL;
    const plotH = H - padT - padB;

    // price range
    let min = Infinity;
    let max = -Infinity;
    for (const c of candles) { if (c.l < min) min = c.l; if (c.h > max) max = c.h; }
    const span = max - min || max * 0.001;
    min -= span * 0.12;
    max += span * 0.12;
    const y = (v) => padT + ((max - v) / (max - min)) * plotH;

    // grid + axis labels
    ctx.font = '10px ui-monospace, SFMono-Regular, Menlo, monospace';
    ctx.textBaseline = 'middle';
    const gridLines = 5;
    for (let i = 0; i <= gridLines; i++) {
      const gy = padT + (plotH / gridLines) * i;
      const value = max - ((max - min) / gridLines) * i;
      ctx.strokeStyle = 'rgba(255,255,255,0.045)';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(padL, Math.round(gy) + 0.5);
      ctx.lineTo(padL + plotW, Math.round(gy) + 0.5);
      ctx.stroke();
      ctx.fillStyle = '#63737f';
      ctx.textAlign = 'left';
      ctx.fillText(fmtPrice(value, state.chartDecimals), padL + plotW + 8, gy);
    }

    // vertical guide lines
    for (let i = 0; i <= 4; i++) {
      const gx = padL + (plotW / 4) * i;
      ctx.strokeStyle = 'rgba(255,255,255,0.03)';
      ctx.beginPath();
      ctx.moveTo(Math.round(gx) + 0.5, padT);
      ctx.lineTo(Math.round(gx) + 0.5, padT + plotH);
      ctx.stroke();
    }

    // candles
    const n = candles.length;
    const slot = plotW / n;
    const bodyW = Math.max(2, slot * 0.62);

    candles.forEach((c, i) => {
      const cx = padL + slot * i + slot / 2;
      const up = c.c >= c.o;
      const color = up ? '#00e08a' : '#ff4d63';
      const isLast = i === n - 1;

      // wick
      ctx.strokeStyle = color;
      ctx.lineWidth = 1;
      ctx.globalAlpha = 0.85;
      ctx.beginPath();
      ctx.moveTo(cx, y(c.h));
      ctx.lineTo(cx, y(c.l));
      ctx.stroke();

      // body
      const top = y(Math.max(c.o, c.c));
      const bottom = y(Math.min(c.o, c.c));
      ctx.globalAlpha = up ? 0.9 : 0.85;
      ctx.fillStyle = color;
      if (isLast) {
        ctx.shadowColor = color;
        ctx.shadowBlur = 14;
      }
      ctx.fillRect(cx - bodyW / 2, top, bodyW, Math.max(1.2, bottom - top));
      ctx.shadowBlur = 0;
      ctx.globalAlpha = 1;
    });

    // last price line
    const last = candles[n - 1];
    const ly = y(last.c);
    const up = last.c >= last.o;
    ctx.setLineDash([4, 4]);
    ctx.strokeStyle = up ? 'rgba(0,224,138,0.55)' : 'rgba(255,77,99,0.55)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(padL, Math.round(ly) + 0.5);
    ctx.lineTo(padL + plotW, Math.round(ly) + 0.5);
    ctx.stroke();
    ctx.setLineDash([]);

    // price flag
    const label = fmtPrice(last.c, state.chartDecimals);
    ctx.font = '10px ui-monospace, SFMono-Regular, Menlo, monospace';
    const tw = ctx.measureText(label).width;
    const fy = Math.min(Math.max(ly - 9, 2), H - 20);
    ctx.fillStyle = up ? '#00e08a' : '#ff4d63';
    ctx.beginPath();
    ctx.roundRect ? ctx.roundRect(padL + plotW + 3, fy, Math.min(tw + 12, axisW - 4), 18, 4) : ctx.rect(padL + plotW + 3, fy, Math.min(tw + 12, axisW - 4), 18);
    ctx.fill();
    ctx.fillStyle = '#04150e';
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, padL + plotW + 9, fy + 9);

    // entry marker for an active signal on this pair
    const sig = activeSignalFor(state.chartPair);
    if (sig) {
      const ex = padL + plotW - slot / 2;
      const ey = sig.direction === 'CALL' ? padT + plotH - 12 : padT + 12;
      ctx.fillStyle = sig.direction === 'CALL' ? 'rgba(0,224,138,0.16)' : 'rgba(255,77,99,0.16)';
      ctx.beginPath();
      ctx.arc(ex, ey, 16, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = sig.direction === 'CALL' ? '#00e08a' : '#ff4d63';
      ctx.beginPath();
      if (sig.direction === 'CALL') {
        ctx.moveTo(ex, ey - 7); ctx.lineTo(ex + 7, ey + 5); ctx.lineTo(ex - 7, ey + 5);
      } else {
        ctx.moveTo(ex, ey + 7); ctx.lineTo(ex + 7, ey - 5); ctx.lineTo(ex - 7, ey - 5);
      }
      ctx.closePath();
      ctx.fill();
    }

    // time labels
    ctx.fillStyle = '#4a5866';
    ctx.font = '9px ui-monospace, SFMono-Regular, Menlo, monospace';
    ctx.textBaseline = 'top';
    [0, Math.floor(n / 2), n - 1].forEach((idx, k) => {
      const c = candles[idx];
      if (!c) return;
      const d = new Date(c.t);
      const txt = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
      const cx = padL + slot * idx + slot / 2;
      ctx.textAlign = k === 0 ? 'left' : k === 2 ? 'right' : 'center';
      ctx.fillText(txt, Math.min(Math.max(cx, padL + 12), padL + plotW - 12), H - 16);
    });
  }

  if (canvas) {
    if ('ResizeObserver' in window) {
      new ResizeObserver(() => drawChart()).observe(canvas);
    } else {
      window.addEventListener('resize', drawChart);
    }
  }

  document.querySelectorAll('.tf').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.tf').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      toast(`Timeframe ${btn.dataset.tf} · simulated data window`, 2000);
    });
  });

  /* ------------------------------------------------------------------ */
  /* Scanner                                                             */
  /* ------------------------------------------------------------------ */
  let scannerRows = [];

  async function pollScanner() {
    try {
      const data = await get('/api/pairs');
      scannerRows = data.rows;
      renderScanner();
      if ($('scanUpdated')) {
        const d = new Date(data.updatedAt);
        $('scanUpdated').textContent = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
      }
    } catch (_) { /* silent */ }
  }

  function renderScanner() {
    const el = $('scanner');
    if (!el) return;
    const empty = el.querySelector('.feed-empty');
    if (empty) empty.remove();

    // First paint builds the rows; later polls only patch text nodes, which
    // keeps the list scroll position (and hover state) stable.
    if (!el.querySelector('.scan-row')) {
      const frag = document.createDocumentFragment();
      scannerRows.forEach((row) => {
        const div = document.createElement('div');
        div.className = 'scan-row';
        div.dataset.pair = row.pair;
        div.innerHTML = `
          <span class="pair">${row.pair}</span>
          <span class="px">—</span>
          <span class="chg up">+0.00%</span>
        `;
        div.addEventListener('click', () => {
          chartLocked = true;
          setChartPair(row.pair, row.decimals);
          toast(`Chart pinned to <b>${row.pair}</b> · new signals will not steal it`, 2600);
        });
        frag.appendChild(div);
      });
      el.appendChild(frag);
    }

    scannerRows.forEach((row) => {
      const div = el.querySelector(`.scan-row[data-pair="${row.pair}"]`);
      if (!div) return;
      div.classList.toggle('active', row.pair === state.chartPair);
      div.querySelector('.px').textContent = fmtPrice(row.price, row.decimals);
      const chg = div.querySelector('.chg');
      chg.textContent = `${row.change >= 0 ? '+' : ''}${row.change.toFixed(2)}%`;
      chg.className = `chg ${row.change >= 0 ? 'up' : 'down'}`;
    });
  }

  /* ------------------------------------------------------------------ */
  /* Diagnostics meters                                                  */
  /* ------------------------------------------------------------------ */
  const meters = [0, 1, 2, 3, 4].map((i) => ({ i, value: 70 + Math.random() * 28 }));

  function paintMeters() {
    meters.forEach((m) => {
      m.value = Math.max(52, Math.min(99.9, m.value + (Math.random() - 0.5) * 14));
      const label = $(`m${m.i}`);
      const bar = $(`m${m.i}b`);
      if (label) label.textContent = `${m.value.toFixed(1)}%`;
      if (bar) bar.style.width = `${m.value}%`;
    });
  }
  paintMeters();
  setInterval(paintMeters, 2400);

  /* ------------------------------------------------------------------ */
  /* Session + poll loops                                                */
  /* ------------------------------------------------------------------ */
  if ($('sesKey')) $('sesKey').textContent = state.plan === 'pro' ? 'DRK-PRO-DEMO' : 'FREE-5';
  if ($('sesFp')) $('sesFp').textContent = fingerprint();

  const logout = $('logoutBtn');
  if (logout) {
    logout.addEventListener('click', () => {
      api.clear();
      window.location.href = './';
    });
  }

  const flash = sessionStorage.getItem('otc.flash');
  if (flash) {
    sessionStorage.removeItem('otc.flash');
    setTimeout(() => toast(flash), 700);
  }

  connect();
  setInterval(pollChart, 1600);
  setInterval(pollScanner, 6000);
  window.addEventListener('resize', () => drawChart());

  // Keyboard shortcut: mute
  window.addEventListener('keydown', (e) => {
    if (e.key.toLowerCase() === 'm' && !e.metaKey && !e.ctrlKey) soundBtn.click();
  });
})();
