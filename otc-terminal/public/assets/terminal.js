/* =====================================================================
   OTC Bot — terminal controller
   ===================================================================== */
(function () {
  'use strict';
  const { api } = window.OTC;
  const M = window.MARKET;
  const $ = (id) => document.getElementById(id);

  /* ---------------------------------------------------------------- guard */
  if (!api.token) {
    window.location.replace('/');
    return;
  }

  const plan = api.plan || 'trial';
  const TRIAL_KEY = 'otc.trialLeft';
  const TRIAL_LIMIT = 5;
  let trialLeft = Number(localStorage.getItem(TRIAL_KEY));
  if (!Number.isFinite(trialLeft)) trialLeft = TRIAL_LIMIT;
  if (plan !== 'trial') trialLeft = Infinity;

  /* ---------------------------------------------------------------- state */
  const state = {
    kind: 'otc',
    filter: 'all',
    tf: '1m',
    selected: null,
    sound: false,
    seenActive: new Set(),
    lastSig: '',
  };

  const MARKETS = [
    { kind: 'otc', label: 'OTC Pairs', sub: 'OTC' },
    { kind: 'forex', label: 'Forex', sub: 'FX' },
    { kind: 'crypto', label: 'Crypto', sub: 'CRYPTO' },
    { kind: 'india', label: 'Indian Market', sub: 'INR' },
  ];

  /* ---------------------------------------------------------------- utils */
  function fmtPrice(px, digits) {
    if (px == null || Number.isNaN(px)) return '—';
    const d = px < 0.01 ? 8
      : px < 1 ? 5
        : px < 100 ? digits
          : px < 1000 ? Math.min(digits, 4)
            : px < 10000 ? Math.min(digits, 3) : Math.min(digits, 2);
    return px.toLocaleString('en-US', { minimumFractionDigits: d, maximumFractionDigits: d });
  }
  const pad = (n) => String(n).padStart(2, '0');
  function timeLabel(d = new Date()) {
    let h = d.getHours();
    const m = pad(d.getMinutes());
    const ap = h >= 12 ? 'PM' : 'AM';
    h = h % 12 || 12;
    return `${pad(h)}:${m} ${ap}`;
  }
  function toast(msg, cls = '') {
    const wrap = $('toasts');
    if (!wrap) return;
    const el = document.createElement('div');
    el.className = 'toast ' + cls;
    el.innerHTML = msg;
    wrap.appendChild(el);
    setTimeout(() => el.remove(), 4000);
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
      o.frequency.value = 880;
      g.gain.setValueAtTime(0.0001, ctx.currentTime);
      g.gain.exponentialRampToValueAtTime(0.12, ctx.currentTime + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.28);
      o.connect(g).connect(ctx.destination);
      o.start();
      o.stop(ctx.currentTime + 0.3);
    } catch { /* ignore */ }
  }

  /* ---------------------------------------------------------------- header */
  function paintPlan() {
    const pill = $('planPill');
    const name = $('avatarName');
    const init = $('avatarInitial');
    const pro = plan !== 'trial';
    if (pill) {
      pill.textContent = pro ? 'PRO' : 'TRIAL';
      pill.className = 'pill ' + (pro ? 'pill-purple' : 'pill-amber');
      pill.style.cssText = 'padding:2px 7px;font-size:9px';
    }
    if (name) name.textContent = pro ? 'Super' : 'Trial';
    if (init) init.textContent = pro ? 'S' : 'T';
    const ribbon = $('trialRibbon');
    if (ribbon) {
      ribbon.hidden = pro;
      if (!pro) {
        const left = Math.max(0, trialLeft);
        $('trialText').textContent = `TRIAL · ${left} of ${TRIAL_LIMIT} signals unlocked`;
        $('trialBar').style.width = `${(left / TRIAL_LIMIT) * 100}%`;
      }
    }
  }

  let liveUsers = 340 + Math.floor(Math.random() * 90);
  function paintClocks() {
    const clock = $('sessionClock');
    if (clock) {
      const d = new Date();
      clock.textContent = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
        }
    const lu = $('liveUsers');
    if (lu) lu.textContent = liveUsers;
  }
  setInterval(() => {
    paintClocks();
    liveUsers = Math.max(280, Math.min(560, liveUsers + (Math.random() < 0.5 ? -1 : 1) * Math.floor(Math.random() * 4)));
  }, 1000);

  function paintFeed() {
    const live = M.state.mode !== 'demo';
    const label = live
      ? `${(M.state.cryptoSource || 'live').toUpperCase()} · LIVE`
      : 'LOCAL MODEL';
    ['feedState', 'footFeed'].forEach((id) => {
      const el = $(id);
      if (!el) return;
      el.textContent = label;
      el.className = 'feed-badge' + (live ? '' : ' warn');
    });
    const src = $('footSources');
    if (src) {
      src.textContent = live
        ? `sources: ${M.state.cryptoSource || '—'} · ${M.state.fxSource || '—'}`
        : 'sources: offline simulation';
    }
    const pill = $('enginePill');
    if (pill) pill.textContent = live ? '⚡ Live Liquidity Engine™' : '⚡ Local Liquidity Engine™';
  }

  /* ---------------------------------------------------------------- tabs */
  function paintTabs() {
    const box = $('mkTabs');
    if (!box) return;
    const counts = M.counts();
    box.innerHTML = MARKETS.map((m) => {
      const on = state.kind === m.kind ? ' on' : '';
      const n = counts[m.kind] || 0;
      return `<button class="mk-tab${on}" data-kind="${m.kind}">${m.label}<span class="n">${n}</span></button>`;
    }).join('');
  }

  /* ---------------------------------------------------------------- cards */
  const cardEls = new Map();

  const ICON_CLOCK = '<svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 7.5v4.8l3 1.8"/></svg>';
  const ICON_ENTRY = '<svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M4 12h11"/><path d="M11 8l4 4-4 4"/><path d="M20 5v14"/></svg>';

  function cardHTML(s, locked) {
    const cls = ['sig-card', s.dir === 'BUY' ? 'buy' : 'sell'];
    if (!s.active) cls.push('standby');
    if (locked) cls.push('locked');
    const arrow = s.dir === 'BUY'
      ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 18L18 6"/><path d="M9 6h9v9"/></svg>'
      : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 6l14 12"/><path d="M18 9v9H9"/></svg>';
    const kindLabel = `${s.tag || 'OTC'} · ${s.name || ''}`;
    const chg = s.chg != null ? `<span class="chg ${s.chg >= 0 ? 'chg-up' : 'chg-dn'}">${s.chg >= 0 ? '+' : ''}${s.chg.toFixed(2)}%</span>` : '';
    const live = s.kind === 'otc'
      ? '<i class="mk-dot otc"></i>'
      : '<i class="live-dot" style="width:5px;height:5px"></i>';
    return `
      <button class="${cls.join(' ')}" data-id="${s.id}" data-dir="${s.dir}" ${locked ? 'data-locked="1"' : ''}>
        <div class="sc-top">
          <span class="sc-sym">${s.symbol}</span>
          <span class="sc-copy" data-copy="${s.id}">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 012-2h8"/></svg>Copy
          </span>
          <span class="sc-match" data-ref="match">${s.match}%</span>
        </div>
        <div class="sc-meta">${live}<span class="dot"></span>${kindLabel}</div>
        <div class="sc-price"><span data-ref="price">${fmtPrice(s.price, s.digits)}</span>${chg}</div>
        <div class="sc-dir">${s.active ? s.dir : 'STANDBY'} ${s.active ? arrow : ''}</div>
        <div class="sc-conf"><span>Confidence</span><b data-ref="conf">${s.confidence.toFixed(1)}%</b></div>
        <div class="sc-bar"><i data-ref="bar" style="width:${Math.max(4, s.confidence - 55)}%"></i></div>
        <div class="sc-kv">
          <div class="sc-row"><span>${ICON_CLOCK}Trade Duration</span><b>${state.tf}</b></div>
          <div class="sc-row"><span>${ICON_ENTRY}Entry Time</span><b data-ref="entry">${timeLabel(entryDate())}</b></div>
        </div>
      </button>`;
  }

  function entryDate() {
    const now = Date.now();
    const step = state.tf === '1m' ? 60e3 : parseInt(state.tf, 10) * 60e3;
    return new Date(Math.ceil(now / step) * step);
  }

  function currentList() {
    let list = M.signals(state.kind);
    list.sort((a, b) => (b.active - a.active) || (b.confidence - a.confidence));
    if (state.filter === 'active') list = list.filter((s) => s.active);
    if (state.filter === 'standby') list = list.filter((s) => !s.active);
    return list;
  }

  function paintCounters(all) {
    const base = M.signals(state.kind);
    const active = base.filter((s) => s.active).length;
    const standby = base.length - active;
    $('cActive').textContent = active;
    $('cStandby').textContent = standby;
    $('nAll').textContent = base.length;
    $('nActive').textContent = active;
    $('nStandby').textContent = standby;
    $('scanCount').textContent = base.length;
    $('scanLabel').textContent = state.kind === 'otc' ? 'OTC pairs'
      : state.kind === 'crypto' ? 'crypto markets'
        : state.kind === 'india' ? 'Indian market instruments' : 'forex pairs';
    const word = $('scanWord');
    if (word) {
      word.textContent = state.kind === 'otc' ? 'OTC' : state.kind === 'crypto' ? 'Crypto'
        : state.kind === 'india' ? 'Indian' : 'Forex';
    }
  }

  function renderGrid() {
    const grid = $('grid');
    if (!grid) return;
    const list = currentList();
    const sig = state.kind + '|' + state.filter + '|' + Math.min(99, Number.isFinite(trialLeft) ? trialLeft : 99) + '|' + list.map((s) => s.id).sort().join(',');
    if (sig === state.lastSig && cardEls.size) {
      updateCards(list);
      return;
    }
    state.lastSig = sig;
    cardEls.clear();
    if (!list.length) {
      grid.innerHTML = '<div class="grid-empty"><b>No signals here</b>Nothing matches this filter right now — try another market.</div>';
      return;
    }
    grid.innerHTML = list.map((s, i) => {
      const locked = plan === 'trial' && i >= trialLeft;
      return cardHTML(s, locked);
    }).join('');
    Array.from(grid.children).forEach((el, i) => {
      if (el.dataset.id) cardEls.set(el.dataset.id, { el, s: list[i] });
    });
  }

  function updateCards(list) {
    list.forEach((s) => {
      const entry = cardEls.get(s.id);
      if (!entry) return;
      const el = entry.el;
      const price = el.querySelector('[data-ref="price"]');
      if (price) price.textContent = fmtPrice(s.price, s.digits);
      const conf = el.querySelector('[data-ref="conf"]');
      if (conf) conf.textContent = s.confidence.toFixed(1) + '%';
      const match = el.querySelector('[data-ref="match"]');
      if (match) match.textContent = s.match + '%';
      const bar = el.querySelector('[data-ref="bar"]');
      if (bar) bar.style.width = Math.max(4, s.confidence - 55) + '%';
      const ent = el.querySelector('[data-ref="entry"]');
      if (ent) ent.textContent = timeLabel(entryDate());
      const chg = el.querySelector('.chg');
      if (chg && s.chg != null) {
        chg.textContent = `${s.chg >= 0 ? '+' : ''}${s.chg.toFixed(2)}%`;
        chg.className = 'chg ' + (s.chg >= 0 ? 'chg-up' : 'chg-dn');
      }
      // keep the verdict label / tint in sync when a signal activates
      const dirEl = el.querySelector('.sc-dir');
      if (dirEl) {
        const want = s.active ? s.dir : 'STANDBY';
        if (!dirEl.textContent.trim().startsWith(want)) {
          dirEl.innerHTML = s.active
            ? `${s.dir} <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round">${s.dir === 'BUY' ? '<path d="M4 18L18 6"/><path d="M9 6h9v9"/>' : '<path d="M4 6l14 12"/><path d="M18 9v9H9"/>'}</svg>`
            : 'STANDBY';
        }
      }
      el.classList.toggle('buy', s.dir === 'BUY');
      el.classList.toggle('sell', s.dir === 'SELL');
      el.classList.toggle('standby', !s.active);
      entry.s = s;
    });
  }

  /* ---------------------------------------------------------------- detail */
  let chartTimer = null;

  function openSheet() { $('detail').classList.add('open'); }
  function closeSheet() {
    $('detail').classList.remove('open');
    state.selected = null;
    if (chartTimer) { clearInterval(chartTimer); chartTimer = null; }
    document.querySelectorAll('.sig-card.selected').forEach((el) => el.classList.remove('selected'));
  }

  async function openDetail(id) {
    const inst = M.state.byId[id];
    if (!inst) return;
    const sig = M.signalFor(inst);
    state.selected = id;
    document.querySelectorAll('.sig-card').forEach((el) => el.classList.toggle('selected', el.dataset.id === id));
    $('ddSym').textContent = inst.pair;
    $('ddSub').textContent = (inst.tagLong || inst.tag) + ' · ' + inst.name;
    $('ddPrice').textContent = fmtPrice(sig.price, inst.digits);
    $('ddPrice').dataset.digits = inst.digits;
    const chg = $('ddChg');
    if (sig.chg != null) {
      chg.textContent = `${sig.chg >= 0 ? '+' : ''}${sig.chg.toFixed(2)}% 24h`;
      chg.className = sig.chg >= 0 ? 'chg-up' : 'chg-dn';
    } else chg.textContent = '—';

    const dir = $('ddDir');
    dir.textContent = sig.active ? sig.dir : 'STANDBY';
    dir.className = 'dir ' + (sig.active ? (sig.dir === 'BUY' ? 'dir-buy' : 'dir-sell') : '');
    $('ddConf').textContent = sig.confidence.toFixed(1) + '%';
    $('ddFactors').innerHTML = sig.factors.map((f) =>
      `<div class="dd-factor"><div class="fl"><span>${f.name}</span><b>${f.score.toFixed(0)}</b></div>
       <div class="fb"><i style="width:${f.score.toFixed(0)}%"></i></div></div>`).join('');
    $('ddNote').textContent = inst.kind === 'otc'
      ? 'OTC instrument · synthetic liquidity model · entry valid for the next ' + state.tf + ' candle.'
      : 'Model output computed from live market data. Verify the price on your broker before entering.';
    openSheet();

    const draw = async () => {
      const data = await M.candles(id, state.tf);
      if (!data || state.selected !== id) return;
      drawChart($('ddChart'), data);
      $('ddSource').textContent = (data.source || (inst.kind === 'otc' ? 'OTC synthetic' : 'LIVE')).toUpperCase();
      const cs = data.candles;
      $('ddRange').textContent = `${cs.length} bars · ${fmtPrice(Math.min(...cs.map((c) => c.l)), inst.digits)} – ${fmtPrice(Math.max(...cs.map((c) => c.h)), inst.digits)}`;
    };
    await draw();
    if (chartTimer) clearInterval(chartTimer);
    chartTimer = setInterval(async () => {
      const s = M.signalFor(inst);
      $('ddPrice').textContent = fmtPrice(s.price, inst.digits);
      await draw();
    }, inst.kind === 'otc' ? 1500 : 20000);
  }

  function drawChart(canvas, data) {
    if (!canvas) return;
    const cs = data.candles || [];
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const w = canvas.clientWidth || 320;
    const h = canvas.clientHeight || 240;
    canvas.width = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    const ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);
    if (!cs.length) return;

    const padR = 54, padL = 6, padT = 10, padB = 18;
    const iw = w - padL - padR, ih = h - padT - padB;
    const min = Math.min(...cs.map((c) => c.l));
    const max = Math.max(...cs.map((c) => c.h));
    const span = (max - min) || max * 0.001 || 1;
    const y = (v) => padT + ih - ((v - min) / span) * ih;
    const bw = Math.max(1.6, iw / cs.length - 1.6);

    // grid
    ctx.strokeStyle = 'rgba(255,255,255,.055)';
    ctx.lineWidth = 1;
    ctx.font = '9px ui-monospace, Menlo, monospace';
    ctx.fillStyle = 'rgba(139,139,169,.75)';
    for (let i = 0; i <= 4; i++) {
      const v = min + (span * i) / 4;
      const py = Math.round(y(v)) + 0.5;
      ctx.beginPath(); ctx.moveTo(padL, py); ctx.lineTo(padL + iw, py); ctx.stroke();
      ctx.fillText(v.toFixed(v < 1 ? 5 : 2), padL + iw + 5, py + 3);
    }

    const thin = cs.every((c) => c.thin);
    if (thin) {
      // area + line for close-only series (ECB daily forex)
      const grad = ctx.createLinearGradient(0, padT, 0, padT + ih);
      grad.addColorStop(0, 'rgba(139,92,246,.35)');
      grad.addColorStop(1, 'rgba(139,92,246,0)');
      ctx.beginPath();
      cs.forEach((c, i) => {
        const px = padL + (iw * (i + 0.5)) / cs.length;
        i ? ctx.lineTo(px, y(c.c)) : ctx.moveTo(px, y(c.c));
      });
      ctx.strokeStyle = '#a78bfa';
      ctx.lineWidth = 1.6;
      ctx.stroke();
      ctx.lineTo(padL + iw, padT + ih);
      ctx.lineTo(padL, padT + ih);
      ctx.closePath();
      ctx.fillStyle = grad;
      ctx.fill();
    } else {
      cs.forEach((c, i) => {
        const x = padL + (iw * (i + 0.5)) / cs.length;
        const up = c.c >= c.o;
        ctx.strokeStyle = up ? '#22c55e' : '#ef4444';
        ctx.fillStyle = up ? 'rgba(34,197,94,.85)' : 'rgba(239,68,68,.85)';
        ctx.beginPath();
        ctx.moveTo(x, y(c.h));
        ctx.lineTo(x, y(c.l));
        ctx.stroke();
        const yo = y(c.o), yc = y(c.c);
        const top = Math.min(yo, yc);
        const bh = Math.max(1.2, Math.abs(yc - yo));
        ctx.fillRect(x - bw / 2, top, bw, bh);
      });
      // EMA(9)
      const closes = cs.map((c) => c.c);
      const k = 2 / 10;
      let e = closes[0];
      const pts = closes.map((v, i) => { e = v * k + e * (1 - k); return { x: padL + (iw * (i + 0.5)) / cs.length, yv: e }; });
      ctx.beginPath();
      pts.forEach((p, i) => (i ? ctx.lineTo(p.x, y(p.yv)) : ctx.moveTo(p.x, y(p.yv))));
      ctx.strokeStyle = 'rgba(167,139,250,.85)';
      ctx.lineWidth = 1.2;
      ctx.stroke();
    }

    // last price marker
    const last = cs[cs.length - 1].c;
    const py = Math.round(y(last)) + 0.5;
    ctx.setLineDash([3, 3]);
    ctx.strokeStyle = 'rgba(255,255,255,.22)';
    ctx.beginPath(); ctx.moveTo(padL, py); ctx.lineTo(padL + iw, py); ctx.stroke();
    ctx.setLineDash([]);
    ctx.fillStyle = '#a78bfa';
    ctx.fillRect(padL + iw + 1, py - 8, padR - 4, 16);
    ctx.fillStyle = '#0b0a12';
    ctx.font = 'bold 9px ui-monospace, Menlo, monospace';
    ctx.fillText(last.toFixed(last < 1 ? 5 : 2), padL + iw + 5, py + 3);

    // time axis
    ctx.fillStyle = 'rgba(139,139,169,.7)';
    ctx.font = '8.5px ui-monospace, Menlo, monospace';
    [0, Math.floor(cs.length / 2), cs.length - 1].forEach((idx) => {
      const c = cs[idx];
      const d = new Date(c.t);
      const label = data.thin
        ? `${pad(d.getDate())}/${pad(d.getMonth() + 1)}`
        : `${pad(d.getHours())}:${pad(d.getMinutes())}`;
      const x = padL + (iw * (idx + 0.5)) / cs.length;
      ctx.fillText(label, Math.min(Math.max(x - 12, padL), padL + iw - 26), padT + ih + 12);
    });
  }

  /* ---------------------------------------------------------------- trial */
  function consumeTrial() {
    if (plan !== 'trial' || !Number.isFinite(trialLeft)) return true;
    if (trialLeft <= 0) { $('paywall').classList.add('open'); return false; }
    trialLeft -= 1;
    try { localStorage.setItem(TRIAL_KEY, String(trialLeft)); } catch { /* ignore */ }
    paintPlan();
    if (trialLeft === 0) {
      setTimeout(() => $('paywall').classList.add('open'), 500);
    }
    renderGrid();
    return true;
  }

  /* ---------------------------------------------------------------- events */
  document.addEventListener('click', (e) => {
    const tab = e.target.closest('.mk-tab');
    if (tab) {
      state.kind = tab.dataset.kind;
      state.filter = 'all';
      state.lastSig = '';
      paintTabs();
      renderGrid();
      paintCounters();
      document.querySelectorAll('.filter').forEach((f) => f.classList.toggle('on', f.dataset.filter === 'all'));
      return;
    }

    const copyBtn = e.target.closest('[data-copy]');
    if (copyBtn) {
      e.stopPropagation();
      const id = copyBtn.dataset.copy;
      const s = M.signalFor(M.state.byId[id]);
      const text = `${s.pair} ${s.active ? s.dir : 'STANDBY'} · ${s.confidence.toFixed(1)}% confidence · ${state.tf} expiry · OTC Bot`;
      const done = () => toast('<strong>Copied</strong> ' + s.pair + ' signal.', 'ok');
      if (navigator.clipboard?.writeText) navigator.clipboard.writeText(text).then(done).catch(() => toast(text));
      else toast(text);
      return;
    }

    const card = e.target.closest('.sig-card');
    if (card) {
      if (card.dataset.locked) { $('paywall').classList.add('open'); return; }
      if (plan === 'trial' && !card.dataset.consumed) {
        card.dataset.consumed = '1';
        if (!consumeTrial()) return;
      }
      openDetail(card.dataset.id);
      return;
    }

    const f = e.target.closest('.filter');
    if (f) {
      state.filter = f.dataset.filter;
      state.lastSig = '';
      document.querySelectorAll('.filter').forEach((b) => b.classList.toggle('on', b === f));
      renderGrid();
      return;
    }

    const tf = e.target.closest('#tfGroup button');
    if (tf) {
      state.tf = tf.dataset.tf;
      document.querySelectorAll('#tfGroup button').forEach((b) => b.classList.toggle('on', b === tf));
      state.lastSig = '';
      renderGrid();
      if (state.selected) openDetail(state.selected);
      return;
    }

    if (e.target.closest('[data-close]')) closeSheet();
  });

  $('closePaywall')?.addEventListener('click', () => $('paywall').classList.remove('open'));
  $('paywall')?.addEventListener('click', (e) => { if (e.target.id === 'paywall') $('paywall').classList.remove('open'); });

  $('soundBtn')?.addEventListener('click', (e) => {
    state.sound = !state.sound;
    e.currentTarget.style.color = state.sound ? 'var(--green-2)' : '';
    e.currentTarget.style.borderColor = state.sound ? 'rgba(34,197,94,.5)' : '';
    toast(state.sound ? '<strong>Alerts on</strong> — sound on new active signals.' : '<strong>Alerts off</strong>');
    if (state.sound) beep();
  });

  $('logoutBtn')?.addEventListener('click', () => {
    api.clear();
    try { localStorage.removeItem(TRIAL_KEY); } catch { /* ignore */ }
    window.location.href = '/';
  });

  $('scanBtn')?.addEventListener('click', async (e) => {
    const btn = e.currentTarget;
    btn.classList.add('spin');
    const t0 = performance.now();
    try { await M.refresh(); } catch { /* ignore */ }
    paintFeed();
    state.lastSig = '';
    renderGrid();
    paintCounters();
    const ms = Math.round(performance.now() - t0);
    const active = M.signals(state.kind).filter((s) => s.active).length;
    $('lastScan').textContent = timeLabel() + ':' + pad(new Date().getSeconds());
    setTimeout(() => btn.classList.remove('spin'), 420);
    toast(`<strong>Scan complete</strong> — ${active} active setups found (${ms} ms).`, 'ok');
  });

  $('ddCopy')?.addEventListener('click', () => {
    if (!state.selected) return;
    const s = M.signalFor(M.state.byId[state.selected]);
    const text = `${s.pair} ${s.active ? s.dir : 'STANDBY'} · ${s.confidence.toFixed(1)}% confidence · ${state.tf} expiry · OTC Bot`;
    if (navigator.clipboard?.writeText) navigator.clipboard.writeText(text);
    toast('<strong>Copied</strong> ' + s.pair + ' signal.', 'ok');
  });

  window.addEventListener('resize', () => {
    if (state.selected) openDetail(state.selected);
  });

  /* ---------------------------------------------------------------- boot */
  paintPlan();
  paintClocks();
  paintTabs();
  paintCounters();
  renderGrid();
  paintFeed();

  M.init().then(() => {
    if (M.hydrateCrypto) {
      M.hydrateCrypto().then(() => { state.lastSig = ''; renderGrid(); paintCounters(); }).catch(() => {});
    }
    paintTabs();
    paintCounters();
    state.lastSig = '';
    renderGrid();
    paintFeed();
    $('lastScan').textContent = timeLabel() + ':' + pad(new Date().getSeconds());
  }).catch(() => {});

  // fast loop for OTC ticks / live prices
  setInterval(() => {
    if (state.opened === undefined) state.opened = false;
    renderGrid();
    if (state.selected && M.state.byId[state.selected]?.kind === 'otc') return; // sheet has its own loop
  }, 1500);

  // periodic price refresh for live markets
  setInterval(async () => {
    if (M.state.mode === 'demo') return;
    try { await M.refresh(); } catch { /* ignore */ }
    state.lastSig = '';
    renderGrid();
    paintCounters();
    $('lastScan').textContent = timeLabel() + ':' + pad(new Date().getSeconds());
  }, 30000);

  // alert on new active signal
  setInterval(() => {
    const active = M.signals(state.kind).filter((s) => s.active);
    active.forEach((s) => {
      const k = s.id + s.dir;
      if (!state.seenActive.has(k)) {
        state.seenActive.add(k);
        if (state.seenActive.size > 1) beep();
      }
    });
  }, 4000);
})();
