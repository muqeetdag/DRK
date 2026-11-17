/* Landing page behaviour: ticker, preview feed, counters, gate auth. */
(function () {
  const { api, post, clock, countdown } = window.OTC;

  /* ---------------- ticker ---------------- */
  const tickerData = [
    ['EUR/USD (OTC)', 'CALL', 98.2, '5/5 Matrix'],
    ['GBP/USD (OTC)', 'PUT', 96.5, 'SMC Sweep'],
    ['USD/INR (OTC)', 'CALL', 95.8, 'Kinetic Reversal'],
    ['USD/JPY (OTC)', 'CALL', 97.4, 'Order Block'],
    ['BTC/USD', 'CALL', 94.6, 'Trend Follow'],
    ['AUD/CAD (OTC)', 'PUT', 95.1, 'Liquidity Grab'],
    ['EUR/GBP (OTC)', 'CALL', 97.9, 'Breakout Shield'],
    ['XAU/USD', 'PUT', 96.2, 'Order Block'],
    ['USD/CHF (OTC)', 'CALL', 95.4, '5/5 Matrix'],
    ['NZD/JPY (OTC)', 'PUT', 96.9, 'Kinetic Reversal'],
  ];

  function tickHTML([pair, dir, pct, tag]) {
    return `<div class="tick"><b>${pair}</b><span class="dir ${dir.toLowerCase()}">${dir}</span>` +
      `<span class="pct">${pct.toFixed(1)}%</span><span class="tag">[${tag}]</span></div>`;
  }

  const track = document.getElementById('tickerTrack');
  if (track) {
    const html = tickerData.map(tickHTML).join('');
    track.innerHTML = html + html; // duplicated for a seamless -50% loop
  }

  /* ---------------- hero clock ---------------- */
  const previewClock = document.getElementById('previewClock');
  const tickClock = () => { if (previewClock) previewClock.textContent = clock(); };
  tickClock();
  setInterval(tickClock, 1000);

  /* ---------------- rotating preview ---------------- */
  const previewSignals = document.getElementById('previewSignals');
  let rotate = 0;
  if (previewSignals) {
    setInterval(() => {
      const rows = Array.from(previewSignals.children);
      const first = rows[0];
      if (!first) return;
      first.style.transition = 'opacity .3s ease, transform .3s ease';
      first.style.opacity = '0';
      first.style.transform = 'translateY(-6px)';
      setTimeout(() => {
        first.style.transition = '';
        first.style.opacity = '';
        first.style.transform = '';
        previewSignals.appendChild(first);
      }, 320);
      rotate++;
    }, 2600);
  }

  /* ---------------- stat counters ---------------- */
  function animateCount(el) {
    const target = parseFloat(el.dataset.count || '0');
    const suffix = el.dataset.suffix || '';
    const decimals = (el.dataset.count || '').includes('.') ? 1 : 0;
    const start = performance.now();
    const dur = 1400;
    function frame(now) {
      const t = Math.min(1, (now - start) / dur);
      const eased = 1 - Math.pow(1 - t, 3);
      el.textContent = (target * eased).toFixed(decimals) + suffix;
      if (t < 1) requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  }

  /* ---------------- reveal on scroll ---------------- */
  const revealEls = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window) {
    const io = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('in');
        entry.target.querySelectorAll('[data-count]').forEach(animateCount);
        io.unobserve(entry.target);
      });
    }, { threshold: 0.18 });
    revealEls.forEach((el) => io.observe(el));

    // hero stats are visible immediately
    document.querySelectorAll('.hero-stats [data-count]').forEach((el) => {
      const obs = new IntersectionObserver((e) => {
        if (e[0].isIntersecting) { animateCount(el); obs.disconnect(); }
      }, { threshold: 0.4 });
      obs.observe(el);
    });
  } else {
    revealEls.forEach((el) => el.classList.add('in'));
  }

  /* ---------------- auth gate ---------------- */
  const form = document.getElementById('licenseForm');
  const msg = document.getElementById('gateMsg');
  const btn = document.getElementById('authBtn');
  const keyInput = document.getElementById('licenseKey');

  function showMessage(text, ok) {
    if (!msg) return;
    msg.textContent = text;
    msg.classList.add('show');
    msg.classList.toggle('ok', !!ok);
  }

  function launch(token, plan, extra) {
    api.set(token, plan);
    if (extra) sessionStorage.setItem('otc.flash', extra);
    window.location.href = 'terminal.html';
  }

  async function startTrial() {
    showMessage('Provisioning evaluation seat…', true);
    try {
      const data = await post('/api/auth', { mode: 'trial' });
      launch(data.token, 'trial', 'Free evaluation active — 5 signals remaining.');
    } catch (err) {
      showMessage(err.message || 'Could not start evaluation.');
    }
  }

  document.querySelectorAll('[data-start-trial]').forEach((el) => {
    el.addEventListener('click', (e) => {
      e.preventDefault();
      startTrial();
    });
  });

  if (form) {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const key = (keyInput.value || '').trim().toUpperCase();
      if (key.length < 8) {
        showMessage('License keys are at least 8 characters. Try DEMO-KEY-2025.');
        keyInput.focus();
        return;
      }
      btn.disabled = true;
      showMessage('Verifying license against seat server…', true);
      try {
        const data = await post('/api/auth', { mode: 'license', key });
        showMessage(`License accepted (${data.key}) — launching terminal…`, true);
        setTimeout(() => launch(data.token, 'pro', 'Pro seat authenticated. Full engine unlocked.'), 550);
      } catch (err) {
        btn.disabled = false;
        showMessage(err.message || 'Authentication failed.');
      }
    });
  }

  const trialBtn = document.getElementById('trialBtn');
  if (trialBtn) trialBtn.addEventListener('click', startTrial);

  // If a valid session already exists, offer a shortcut back into the terminal.
  if (api.token && form) {
    const existing = document.createElement('p');
    existing.className = 'gate-foot';
    existing.style.marginTop = '10px';
    existing.innerHTML = 'Session detected. <a href="/terminal.html">Re-open terminal →</a>';
    form.after(existing);
  }

  const year = document.getElementById('year');
  if (year) year.textContent = new Date().getFullYear();
})();
