/* Landing page: live quotes, board, gate authentication, reveals. */
(function () {
  'use strict';
  const { api } = window.OTC;
  const M = window.MARKET;

  const $ = (id) => document.getElementById(id);
  const TRIAL_KEY = 'otc.trialLeft';
  const TRIAL_LIMIT = 5;

  /* ---------------------------------------------------------------- helpers */
  function fmt(px, digits) {
    if (px == null || Number.isNaN(px)) return '—';
    const d = px < 0.01 ? 8 : px < 1 ? 5 : px < 100 ? 4 : px < 10000 ? (digits || 2) : 0;
    return px.toLocaleString('en-US', { minimumFractionDigits: d, maximumFractionDigits: d });
  }
  function chgHTML(chg) {
    if (chg == null) return '<span class="chg">—</span>';
    const up = chg >= 0;
    return `<span class="chg ${up ? 'chg-up' : 'chg-dn'}">${up ? '▲' : '▼'} ${Math.abs(chg).toFixed(2)}%</span>`;
  }
  const toast = (msg, cls = '') => {
    const wrap = $('toasts');
    if (!wrap) return;
    const el = document.createElement('div');
    el.className = 'toast ' + cls;
    el.innerHTML = msg;
    wrap.appendChild(el);
    setTimeout(() => el.remove(), 3800);
  };

  /* ---------------------------------------------------------------- ticker */
  const TICKER_IDS = [
    'CRYPTO:BTC', 'CRYPTO:ETH', 'CRYPTO:SOL', 'CRYPTO:XRP', 'FX:EURUSD', 'FX:GBPUSD',
    'FX:USDJPY', 'FX:USDINR', 'INDIA:BTC', 'CRYPTO:DOGE', 'FX:AUDUSD', 'OTC:EURUSD',
  ];

  function tickHTML(s) {
    const px = fmt(s.price, 4);
    return `<div class="tick"><b>${s.pair}</b>` +
      `<span class="dir ${s.dir.toLowerCase() === 'buy' ? 'buy' : 'sell'}">${s.dir}</span>` +
      `<span class="pct">${px}</span>` +
      `<span class="tag">${s.match}% ${s.kind === 'otc' ? '[OTC]' : ''}</span></div>`;
  }

  function renderTicker() {
    const track = $('tickerTrack');
    if (!track) return;
    const rows = TICKER_IDS.map((id) => M.signalFor(M.state.byId[id])).filter(Boolean);
    if (!rows.length) return;
    const html = rows.map(tickHTML).join('');
    track.innerHTML = html + html;
  }

  /* ---------------------------------------------------------------- board */
  const BOARD_IDS = [
    'CRYPTO:BTC', 'CRYPTO:ETH', 'CRYPTO:SOL', 'FX:EURUSD', 'FX:USDINR',
    'INDIA:BTC', 'FX:USDJPY', 'OTC:EURUSD',
  ];

  function renderBoard() {
    const box = $('boardRows');
    if (!box) return;
    const rows = BOARD_IDS.map((id) => M.signalFor(M.state.byId[id])).filter(Boolean);
    box.innerHTML = rows.map((s) => {
      const cls = s.kind === 'otc' ? 'OTC' : s.kind === 'crypto' ? 'CRYPTO'
        : s.kind === 'india' ? 'INR' : 'FX';
      return `<div class="board-row">
        <span class="sym">${s.pair}<i class="cls">${cls}</i></span>
        <span class="px">${fmt(s.price, 4)}</span>
        ${chgHTML(s.chg)}
      </div>`;
    }).join('');

    const stamp = $('boardStamp');
    if (stamp) {
      stamp.textContent = M.state.mode === 'demo'
        ? 'feed offline · local model'
        : 'synced ' + new Date(M.state.lastSync).toLocaleTimeString();
    }
    const src = $('boardSource');
    if (src) {
      const live = M.state.mode !== 'demo';
      src.className = 'pill ' + (live ? 'pill-green' : 'pill-amber');
      src.style.cssText = 'padding:3px 8px;font-size:10px';
      src.innerHTML = live
        ? `<i class="live-dot"></i> ${(M.state.cryptoSource || 'live').toUpperCase()}`
        : 'LOCAL MODEL';
    }
    const badge = $('feedBadge');
    if (badge) {
      badge.textContent = M.state.mode === 'demo' ? 'Local model feed' : 'Live market data';
    }
    const gf = $('gateFeed');
    if (gf) gf.textContent = M.state.mode === 'demo' ? 'local model' : 'live';
  }

  /* ---------------------------------------------------------------- counts */
  function renderCounts() {
    const c = M.counts();
    const set = (id, v) => { const el = $(id); if (el) el.textContent = v; };
    set('mkOtc', c.otc);
    set('mkForex', c.forex);
    set('mkCrypto', c.crypto);
    set('mkIndia', c.india);
    set('statInstruments', c.otc + c.forex + c.crypto + c.india);
    set('gateCount', c.otc + c.forex + c.crypto + c.india);
  }

  /* ---------------------------------------------------------------- gate */
  function goTerminal() { window.location.href = 'terminal.html'; }

  function startTrial() {
    api.set('trial-' + Date.now().toString(36), 'trial');
    try { localStorage.setItem(TRIAL_KEY, String(TRIAL_LIMIT)); } catch { /* ignore */ }
    toast('<strong>Trial activated</strong> — 5 signals unlocked.', 'ok');
    setTimeout(goTerminal, 380);
  }

  function activate(key) {
    const clean = String(key || '').trim().toUpperCase();
    const err = $('gateError');
    if (clean.length < 8 || !/^[A-Z0-9][A-Z0-9-]{6,}[A-Z0-9]$/.test(clean)) {
      if (err) err.textContent = 'Enter a valid license key (8+ characters, letters, numbers, dashes).';
      return;
    }
    if (err) err.textContent = '';
    api.set('lic-' + clean, 'pro');
    try { localStorage.removeItem(TRIAL_KEY); } catch { /* ignore */ }
    if (window.OTC.recordActivation) window.OTC.recordActivation();
    toast('<strong>License accepted</strong> — Pro unlocked.', 'ok');
    setTimeout(goTerminal, 380);
  }

  const form = $('gateForm');
  if (form) {
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      activate($('licenseInput').value);
    });
  }
  ['trialBtn', 'trialCta'].forEach((id) => {
    const el = $(id);
    if (el) el.addEventListener('click', (e) => { e.preventDefault(); startTrial(); });
  });

  /* ---------------------------------------------------------------- reveals */
  const io = new IntersectionObserver((entries) => {
    entries.forEach((en) => { if (en.isIntersecting) { en.target.classList.add('in'); io.unobserve(en.target); } });
  }, { rootMargin: '0px 0px -8% 0px' });
  document.querySelectorAll('.reveal').forEach((el) => io.observe(el));

  const y = $('year');
  if (y) y.textContent = new Date().getFullYear();

  /* ---------------------------------------------------------------- boot */
  renderCounts();
  renderTicker();
  renderBoard();

  M.init().then(() => {
    renderCounts();
    renderTicker();
    renderBoard();
    if (M.state.mode === 'demo') toast('Live feeds unreachable — showing the local model.', 'err');
  }).catch(() => {});

  setInterval(() => { renderTicker(); renderBoard(); }, 6000);
})();
