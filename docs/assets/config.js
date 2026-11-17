/* Shared client helpers: session storage, API calls, formatting. */
window.OTC = (function () {
  const TOKEN_KEY = 'otc.token';
  const PLAN_KEY = 'otc.plan';

  const api = {
    get token() {
      try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
    },
    get plan() {
      try { return localStorage.getItem(PLAN_KEY); } catch { return null; }
    },
    set(token, plan) {
      try {
        localStorage.setItem(TOKEN_KEY, token);
        localStorage.setItem(PLAN_KEY, plan || 'trial');
      } catch { /* storage blocked */ }
    },
    clear() {
      try {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(PLAN_KEY);
      } catch { /* ignore */ }
    },
  };

  async function post(path, body) {
    const res = await fetch(path, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body || {}),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || 'Request failed');
    return data;
  }

  async function get(path) {
    const res = await fetch(path, {
      headers: api.token ? { authorization: `Bearer ${api.token}` } : {},
    });
    if (!res.ok) throw new Error('Request failed');
    return res.json();
  }

  const pad = (n) => String(n).padStart(2, '0');

  function clock(date = new Date()) {
    return `${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())} UTC`;
  }

  function countdown(ms) {
    const total = Math.max(0, Math.floor(ms / 1000));
    return `${pad(Math.floor(total / 60))}:${pad(total % 60)}`;
  }

  function price(value, decimals = 5) {
    if (value === null || value === undefined || Number.isNaN(value)) return '—';
    return Number(value).toLocaleString('en-US', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    });
  }

  return { api, post, get, clock, countdown, price, pad };
})();
