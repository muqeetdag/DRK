/* =====================================================================
   OTC Bot — Live Scanner
   ---------------------------------------------------------------------
   A dense grid of every scanned pair: status (WAIT / BUY / SELL), trend
   from the algorithmic sequence factor, live confidence and the next
   execution minute. Cards update in place every few seconds.
   ===================================================================== */
(function () {
  'use strict';

  const { api } = window.OTC;
  const M = window.MARKET;
  const $ = (id) => document.getElementById(id);

  const SOURCE_KINDS = {
    live: ['forex', 'crypto', 'india'],
    otc: ['otc'],
  };

  const state = {
    kind: 'all',
    status: 'all',
    sources: new Set(['live', 'otc']),
    entries: new Map(),
    lastSignature: '',
    sound: false,
    cycle: 0,
  };

  /* --------------------------------------------------------------- utils */
  const pad = (n) => String(n).padStart(2, '0');
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

  function toast(html, cls = '') {
    const wrap = $('toasts');
    if (!wrap) return;
    const el = document.createElement('div');
    el.className = 'toast ' + cls;
    el.innerHTML = html;
    wrap.appendChild(el);
    setTimeout(() => el.remove(), 3800);
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
      o.frequency.value = 760;
      g.gain.setValueAtTime(0.0001, ctx.currentTime);
      g.gain.exponentialRampToValueAtTime(0.09, ctx.currentTime + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.24);
      o.connect(g).connect(ctx.destination);
      o.start();
      o.stop(ctx.currentTime + 0.28);
    } catch { /* ignore */ }
  }

  function hms(d = new Date()) {
    return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  }

  function timeLabel(d = new Date()) {
    let h = d.getHours();
    const m = pad(d.getMinutes());
    const ap = h >= 12 ? 'PM' : 'AM';
    h = h % 12 || 12;
    return `${pad(h)}:${m} ${ap}`;
  }

  function executionTime() {
    const d = new Date(Date.now() + 60000);
    d.setSeconds(0, 0);
    return timeLabel(d);
  }

  function trendOf(sig) {
    const score = sig.factors?.[0]?.score ?? 50;
    if (score >= 60) return { word: 'Uptrend', cls: 'up', icon: 'up' };
    if (score <= 40) return { word: 'Downtrend', cls: 'dn', icon: 'dn' };
    return { word: 'Sideways', cls: 'flat', icon: 'flat' };
  }

  const ICON = {
    up: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 17L19 6"/><path d="M11 6h8v8"/></svg>',
    dn: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7l15 11"/><path d="M19 10v8h-8"/></svg>',
    flat: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 12h10"/><path d="M10 8l4 4-4 4"/></svg>',
    clock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 7.5v4.8l3 1.8"/></svg>',
    copy: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 012-2h8"/></svg>',
  };

  /* --------------------------------------------------------------- header */
  let liveUsers = 412 + Math.floor(Math.random() * 40);
  function paintClock() {
    const clock = $('sessionClock');
    if (clock) clock.textContent = hms();
    const lu = $('liveUsers');
    if (lu) lu.textContent = liveUsers;
    const sync = $('scanSync');
    if (sync) sync.textContent = hms();
  }

  function paintPush(date = new Date()) {
    const el = $('lastPush');
    if (!el) return;
    el.textContent = date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) + ', ' + timeLabel(date);
  }

  /* ------------------------------------------------------------- signals */
  function allowedKinds() {
    const set = new Set();
    state.sources.forEach((src) => {
      if (src === 'beast') return;
      (SOURCE_KINDS[src] || []).forEach((k) => set.add(k));
    });
    return set;
  }

  function beastOnly() {
    return state.sources.has('beast');
  }

  /* every market is ranked on its own band (top 34% active), exactly like
     the terminal tabs — so OTC shows its own 14/27 split, crypto its own */
  function scoreRows(kind) {
    if (kind !== 'all') {
      try { return M.signals(kind); } catch { return []; }
    }
    const out = [];
    ['otc', 'forex', 'crypto', 'india'].forEach((k) => {
      try { out.push(...M.signals(k)); } catch { /* skip */ }
    });
    return out;
  }

  function listSignals() {
    const kind = state.kind === 'all' ? 'all' : state.kind;
    let rows = scoreRows(kind);

    const allowed = allowedKinds();
    if (allowed.size) rows = rows.filter((s) => allowed.has(s.kind));

    if (state.status === 'active') rows = rows.filter((s) => s.active);
    else if (state.status === 'wait') rows = rows.filter((s) => !s.active);

    // Beast mode = strongest setups float to the top of every view
    if (beastOnly()) {
      rows = rows.slice().sort((a, b) => (b.active - a.active) || (b.confidence - a.confidence));
    } else {
      const order = { otc: 0, forex: 1, crypto: 2, india: 3 };
      rows = rows.slice().sort((a, b) => order[a.kind] - order[b.kind]);
    }
    return rows;
  }

  /* --------------------------------------------------------------- cards */
  const KIND_TAG = { otc: ['OTC', 'o'], forex: ['FX', 'f'], crypto: ['CRYPTO', 'c'], india: ['INR', 'i'] };

  function cardHTML(sig) {
    const trend = trendOf(sig);
    const tag = KIND_TAG[sig.kind] || ['', ''];
    return `<article class="scan-card" data-id="${sig.id}">
      <div class="sc-head">
        <b class="sc-sym">${sig.symbol}</b><i class="sc-tag ${tag[1]}">${tag[0]}</i>
        <button class="sc-copy" type="button" data-copy="${sig.id}" aria-label="Copy signal">${ICON.copy}Copy</button>
        <span class="sc-feed"><i></i>Real-time feed</span>
      </div>
      <div class="sc-status"><b data-ref="status">WAIT</b><em data-ref="pct"></em></div>
      <div class="sc-rows">
        <div class="sc-kv"><span>Trend</span><b data-ref="trend" class="${trend.cls}">${ICON[trend.icon]}${trend.word}</b></div>
        <div class="sc-kv"><span>Execution time</span><b class="time" data-ref="time">${ICON.clock}${executionTime()}</b></div>
      </div>
      <i class="sc-accent"></i>
    </article>`;
  }

  function render() {
    const grid = $('scanGrid');
    if (!grid) return;
    const rows = listSignals();
    grid.innerHTML = rows.length
      ? rows.map(cardHTML).join('')
      : '<div class="scan-empty">No pairs match these filters — turn a master-key feed back on.</div>';

    state.entries = new Map();
    Array.from(grid.querySelectorAll('.scan-card')).forEach((card) => {
      state.entries.set(card.dataset.id, {
        card,
        status: card.querySelector('[data-ref="status"]'),
        pct: card.querySelector('[data-ref="pct"]'),
        trend: card.querySelector('[data-ref="trend"]'),
        time: card.querySelector('[data-ref="time"]'),
      });
    });
    paintCards(rows);
    const count = $('scanCount');
    if (count) count.textContent = String(rows.length);
  }

  function paintCards(rows) {
    const et = executionTime();
    rows.forEach((s) => {
      const el = state.entries.get(s.id);
      if (!el) return;
      const trend = trendOf(s);
      const cls = s.active ? (s.dir === 'BUY' ? 'buy' : 'sell') : 'wait';
      el.card.className = `scan-card ${cls}${s.active ? ' active' : ''}`;
      el.status.textContent = s.active ? s.dir : 'WAIT';
      el.pct.textContent = s.active ? Math.round(s.confidence) + '%' : '';
      el.trend.className = trend.cls;
      el.trend.innerHTML = ICON[trend.icon] + trend.word;
      el.time.innerHTML = ICON.clock + et;
    });
    const active = rows.filter((s) => s.active).length;
    const nav = $('navActive');
    if (nav) nav.textContent = String(active);
  }

  function update() {
    const kind = state.kind === 'all' ? 'all' : state.kind;
    let rows = scoreRows(kind);
    const allowed = allowedKinds();
    if (allowed.size) rows = rows.filter((s) => allowed.has(s.kind));
    if (state.status === 'active') rows = rows.filter((s) => s.active);
    else if (state.status === 'wait') rows = rows.filter((s) => !s.active);
    let list = rows;
    if (allowed.size) list = list.filter((s) => allowed.has(s.kind));
    if (beastOnly()) list = list.slice().sort((a, b) => (b.active - a.active) || (b.confidence - a.confidence));
    if (list.length !== state.entries.size) { render(); return; }
    paintCards(list);
    paintPush();
    state.cycle += 1;
    if (state.cycle % 12 === 0) beep();
  }

  /* --------------------------------------------------------------- copy */
  function copySignal(id) {
    const inst = M.state.byId[id];
    if (!inst) return;
    let sig = null;
    try { sig = M.signalFor(inst); } catch { sig = null; }
    const trend = sig ? trendOf(sig).word : 'Sideways';
    const conf = sig ? Math.round(sig.confidence) : 0;
    const line = sig && sig.active
      ? `${inst.symbol} ${sig.dir} ${conf}% · ${trend} · entry ${executionTime()}`
      : `${inst.symbol} WAIT · ${trend} · next scan ${executionTime()}`;
    const done = () => toast(`<b>Copied.</b> ${line}`, 'ok');
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(line).then(done).catch(fallback);
    } else fallback();
    function fallback() {
      const ta = document.createElement('textarea');
      ta.value = line;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy'); done(); } catch { toast('Copy blocked by the browser.', 'err'); }
      ta.remove();
    }
  }

  /* -------------------------------------------------------------- wiring */
  function wire() {
    $('kindChips').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-kind]');
      if (!b) return;
      state.kind = b.dataset.kind;
      Array.from($('kindChips').children).forEach((x) => x.classList.toggle('on', x === b));
      render();
    });

    $('statusChips').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-status]');
      if (!b) return;
      state.status = b.dataset.status;
      Array.from($('statusChips').children).forEach((x) => x.classList.toggle('on', x === b));
      render();
    });

    $('masterChips').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-src]');
      if (!b) return;
      const src = b.dataset.src;
      if (state.sources.has(src)) state.sources.delete(src);
      else state.sources.add(src);
      b.classList.toggle('on', state.sources.has(src));
      render();
    });

    $('scanGrid').addEventListener('click', (e) => {
      const btn = e.target.closest('[data-copy]');
      if (btn) {
        e.stopPropagation();
        copySignal(btn.dataset.copy);
      }
    });

    $('moreBtn').addEventListener('click', () => {
      const list = $('moreList');
      const open = list.hidden;
      list.hidden = !open;
      $('moreBtn').setAttribute('aria-expanded', open ? 'true' : 'false');
    });

    $('sideToggle').addEventListener('click', () => $('genSide').classList.toggle('open'));
    $('genSide').addEventListener('click', (e) => { if (e.target.closest('a')) $('genSide').classList.remove('open'); });

    $('signOut').addEventListener('click', () => {
      api.clear();
      try { localStorage.removeItem('otc.beast'); } catch { /* ignore */ }
      window.location.replace('./');
    });

    $('bellBtn').addEventListener('click', () => {
      state.sound = !state.sound;
      toast(state.sound ? '<b>Alerts on.</b> You will hear a beep as the grid rescans.' : 'Alerts muted.', state.sound ? 'ok' : '');
      if (state.sound) beep();
    });
  }

  /* --------------------------------------------------------------- boot */
  async function boot() {
    paintClock();
    paintPush();
    try { await M.init(); } catch { /* local model fallback */ }
    render();
    setInterval(() => {
      paintClock();
      liveUsers = clamp(liveUsers + (Math.random() < 0.5 ? -1 : 1) * Math.floor(Math.random() * 3), 360, 560);
    }, 1000);
    setInterval(update, 5000);
    setInterval(paintPush, 30000);
  }

  function init() {
    /* the scanner is readable without a key (it is public market data);
       signing in only unlocks the licence label and the Pro-only modules */
    const plan = api.plan || 'guest';
    const planEl = $('guPlan');
    if (planEl) planEl.textContent = plan === 'pro' ? 'Pro licence' : plan === 'trial' ? 'Trial licence' : 'Guest access';
    wire();
    boot();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
