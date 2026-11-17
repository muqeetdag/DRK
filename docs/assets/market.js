/* =====================================================================
   OTC Bot — market data + confidence engine
   Runs entirely in the browser (no backend, no API keys).

   Live sources, first one that answers wins:
     • Crypto (USD)  : Binance public REST  -> CoinGecko public API
     • Crypto (INR)  : CoinGecko INR quote  -> USD price x live USD/INR
     • Forex         : Frankfurter (ECB) timeseries  -> currency-api (jsDelivr)
     • OTC pairs     : synthetic liquidity engine (by definition OTC)

   If every network path is blocked the module falls back to a local
   simulation so the terminal always renders; `MARKET.status.mode` then
   reads "demo" and the UI says so.
   ===================================================================== */
window.MARKET = (function () {
  'use strict';

  /* ------------------------------------------------------------------ */
  /* config                                                             */
  /* ------------------------------------------------------------------ */
  const FX_LATEST = [
    'https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.min.json',
    'https://latest.currency-api.pages.dev/v1/currencies/usd.min.json',
  ];
  const FX_SERIES = [
    'https://api.frankfurter.dev/v1/{from}..{to}?base=USD&symbols={symbols}',
    'https://api.frankfurter.app/v1/{from}..{to}?base=USD&symbols={symbols}',
  ];
  const BINANCE = ['https://data-api.binance.vision', 'https://api.binance.com'];
  const COINGECKO = ['https://api.coingecko.com/api/v3', 'https://api.coingecko.com/api/v3'];

  const TIMEOUT = 9000;

  /* ------------------------------------------------------------------ */
  /* universe                                                           */
  /* ------------------------------------------------------------------ */

  // 41 synthetic OTC pairs — the set the original terminal scans.
  const OTC_PAIRS = [
    'AUDUSD', 'EURUSD', 'USDINR', 'USDMXN', 'GBPJPY', 'EURGBP', 'NZDUSD', 'AUDCHF',
    'USDZAR', 'USDCAD', 'GBPNZD', 'GBPUSD', 'EURJPY', 'USDCHF', 'AUDJPY', 'AUDCAD',
    'CADCHF', 'CHFJPY', 'EURCHF', 'EURAUD', 'EURCAD', 'EURNZD', 'GBPCHF', 'GBPAUD',
    'GBPCAD', 'AUDNZD', 'NZDCAD', 'NZDCHF', 'NZDJPY', 'CADJPY', 'USDTRY', 'USDPLN',
    'USDSEK', 'USDNOK', 'USDDKK', 'USDHUF', 'USDCZK', 'USDSGD', 'USDHKD', 'USDTHB', 'USDBRL',
  ];

  // Real forex pairs served by the ECB / currency-api feeds.
  const FOREX_PAIRS = [
    'EURUSD', 'GBPUSD', 'USDJPY', 'USDCHF', 'USDCAD', 'AUDUSD', 'NZDUSD', 'USDINR',
    'EURGBP', 'EURJPY', 'EURCHF', 'EURAUD', 'EURCAD', 'EURNZD', 'GBPJPY', 'GBPCHF',
    'GBPAUD', 'GBPCAD', 'GBPNZD', 'AUDJPY', 'AUDCAD', 'AUDNZD', 'AUDCHF', 'CADJPY',
    'CADCHF', 'CHFJPY', 'NZDJPY', 'NZDCAD', 'NZDCHF', 'USDMXN', 'USDZAR', 'USDTRY',
    'USDCNY', 'USDSGD', 'USDHKD', 'USDBRL', 'USDKRW', 'USDTHB', 'USDPHP', 'USDAED', 'USDPKR',
  ];

  // Crypto — Binance symbol + CoinGecko id.
  const CRYPTO = [
    ['BTC', 'BTCUSDT', 'bitcoin', 'Bitcoin'],
    ['ETH', 'ETHUSDT', 'ethereum', 'Ethereum'],
    ['SOL', 'SOLUSDT', 'solana', 'Solana'],
    ['XRP', 'XRPUSDT', 'ripple', 'XRP'],
    ['BNB', 'BNBUSDT', 'binancecoin', 'BNB'],
    ['DOGE', 'DOGEUSDT', 'dogecoin', 'Dogecoin'],
    ['ADA', 'ADAUSDT', 'cardano', 'Cardano'],
    ['AVAX', 'AVAXUSDT', 'avalanche-2', 'Avalanche'],
    ['LINK', 'LINKUSDT', 'chainlink', 'Chainlink'],
    ['DOT', 'DOTUSDT', 'polkadot', 'Polkadot'],
    ['TRX', 'TRXUSDT', 'tron', 'TRON'],
    ['LTC', 'LTCUSDT', 'litecoin', 'Litecoin'],
    ['BCH', 'BCHUSDT', 'bitcoin-cash', 'Bitcoin Cash'],
    ['TON', 'TONUSDT', 'the-open-network', 'Toncoin'],
    ['NEAR', 'NEARUSDT', 'near', 'NEAR'],
    ['ATOM', 'ATOMUSDT', 'cosmos', 'Cosmos'],
    ['ETC', 'ETCUSDT', 'ethereum-classic', 'Ethereum Classic'],
    ['FIL', 'FILUSDT', 'filecoin', 'Filecoin'],
    ['APT', 'APTUSDT', 'aptos', 'Aptos'],
    ['ARB', 'ARBUSDT', 'arbitrum', 'Arbitrum'],
    ['OP', 'OPUSDT', 'optimism', 'Optimism'],
    ['INJ', 'INJUSDT', 'injective-protocol', 'Injective'],
    ['SUI', 'SUIUSDT', 'sui', 'Sui'],
    ['HBAR', 'HBARUSDT', 'hedera-hashgraph', 'Hedera'],
    ['AAVE', 'AAVEUSDT', 'aave', 'Aave'],
    ['UNI', 'UNIUSDT', 'uniswap', 'Uniswap'],
    ['XLM', 'XLMUSDT', 'stellar', 'Stellar'],
    ['SHIB', 'SHIBUSDT', 'shiba-inu', 'Shiba Inu'],
  ];

  const INDIA_COINS = ['BTC', 'ETH', 'SOL', 'XRP', 'BNB', 'DOGE', 'ADA', 'MATIC' ];

  const DECIMALS = {
    JPY: 3, INR: 4, HUF: 2, CZK: 4, HKD: 3, SGD: 4, THB: 4, PHP: 4, KRW: 2,
    MXN: 4, ZAR: 4, TRY: 4, BRL: 4, CNY: 4, PLN: 2, SEK: 2, NOK: 2, DKK: 2,
    AED: 4, PKR: 4, USD: 5, EUR: 5, GBP: 5, CHF: 5, CAD: 5, AUD: 5, NZD: 5,
  };

  const fiat = (code) => ['USD', ...Object.keys(DECIMALS)].includes(code);

  /* ------------------------------------------------------------------ */
  /* helpers                                                            */
  /* ------------------------------------------------------------------ */
  const now = () => Date.now();

  async function jget(url, ms = TIMEOUT) {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), ms);
    try {
      const res = await fetch(url, { signal: ctl.signal, cache: 'no-store', mode: 'cors' });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return await res.json();
    } finally { clearTimeout(t); }
  }

  async function firstOk(list, build) {
    let lastErr;
    for (const base of list) {
      try { return { data: await jget(build(base)), base }; }
      catch (err) { lastErr = err; }
    }
    throw lastErr || new Error('no endpoint');
  }

  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const rnd = (seed) => {
    // deterministic 0..1 from a string seed
    let h = 2166136261;
    for (let i = 0; i < seed.length; i++) { h ^= seed.charCodeAt(i); h = Math.imul(h, 16777619); }
    h ^= h >>> 13; h = Math.imul(h, 0x5bd1e995); h ^= h >>> 15;
    return (h >>> 0) / 4294967296;
  };

  function splitPair(p) { return [p.slice(0, 3), p.slice(3, 6)]; }
  function fmtPair(p) { const [a, b] = splitPair(p); return `${a}/${b}`; }

  /* ------------------------------------------------------------------ */
  /* state                                                              */
  /* ------------------------------------------------------------------ */
  const state = {
    fx: null,           // { date, usd: { eur: 0.92, inr: 87.1, ... } }
    fxSource: '',
    crypto: {},         // { BTC: { px, chg, high, low, vol, inr } }
    cryptoSource: '',
    india: {},          // { BTC: { px, chg } } in INR
    mode: 'loading',    // 'live' | 'mixed' | 'demo'
    lastSync: 0,
    fxStats: {},
    instruments: [],
    byId: {},
    series: {},         // id -> { tf: candles }
    inflight: {},
    errors: [],
  };

  /* ------------------------------------------------------------------ */
  /* skeleton instruments (rendered before data lands)                   */
  /* ------------------------------------------------------------------ */
  function buildInstruments() {
    const list = [];

    OTC_PAIRS.forEach((p, i) => {
      const [b, q] = splitPair(p);
      list.push({
        id: 'OTC:' + p, symbol: p, pair: fmtPair(p), kind: 'otc',
        name: `${b}/${q} OTC`, tag: 'OTC MARKET', digits: DECIMALS[q] || 5,
        seed: 0.35 + rnd('otc' + p) * 0.6, synthetic: true,
      });
    });

    FOREX_PAIRS.forEach((p) => {
      const [b, q] = splitPair(p);
      list.push({
        id: 'FX:' + p, symbol: p, pair: fmtPair(p), kind: 'forex',
        name: `${b}/${q}`, tag: 'FOREX', digits: DECIMALS[q] || 5,
        base: b, quote: q,
      });
    });

    CRYPTO.forEach(([sym, bsym, cgid, name]) => {
      list.push({
        id: 'CRYPTO:' + sym, symbol: sym + 'USDT', pair: sym + '/USDT', kind: 'crypto',
        name: `${name}`, tag: 'CRYPTO', digits: 2, bsym, cgid, coin: sym,
      });
    });

    CRYPTO.filter(([sym]) => INDIA_COINS.includes(sym)).forEach(([sym, bsym, cgid, name]) => {
      list.push({
        id: 'INDIA:' + sym, symbol: sym + 'INR', pair: sym + '/INR', kind: 'india',
        name: `${name} (INR)`, tag: 'INDIA · INR', digits: 0, bsym, cgid, coin: sym,
      });
    });

    // INR reference pair lives in the India tab too.
    list.push({
      id: 'INDIA:USDINR', symbol: 'USDINR', pair: 'USD/INR', kind: 'india',
      name: 'US Dollar / Rupee', tag: 'INDIA · INR', digits: 3, base: 'USD', quote: 'INR',
    });

    state.instruments = list;
    state.byId = {};
    list.forEach((i) => { state.byId[i.id] = i; });
    return list;
  }
  buildInstruments();

  /* ------------------------------------------------------------------ */
  /* pricing                                                            */
  /* ------------------------------------------------------------------ */
  function fxRate(base, quote) {
    if (!state.fx) return null;
    const u = state.fx.usd;
    if (base === 'USD') return u[quote.toLowerCase()] ?? null;
    if (quote === 'USD') { const r = u[base.toLowerCase()]; return r ? 1 / r : null; }
    const a = u[base.toLowerCase()], b = u[quote.toLowerCase()];
    return a && b ? b / a : null;
  }

  function priceOf(inst) {
    if (inst.kind === 'otc') return inst.px;
    if (inst.kind === 'forex') return fxRate(inst.base, inst.quote);
    if (inst.kind === 'crypto') return state.crypto[inst.coin]?.px ?? null;
    if (inst.kind === 'india') {
      if (inst.coin) return state.india[inst.coin]?.px ?? null;
      return fxRate(inst.base, inst.quote);
    }
    return null;
  }

  /* ------------------------------------------------------------------ */
  /* loaders                                                            */
  /* ------------------------------------------------------------------ */
  async function loadFx() {
    try {
      const { data, base } = await firstOk(FX_LATEST, (u) => u);
      const usd = data.usd || data.USD;
      if (!usd) throw new Error('bad payload');
      state.fx = { date: data.date || '', usd };
      state.fxSource = base.includes('jsdelivr') ? 'currency-api' : 'currency-api (pages)';
      return true;
    } catch (e) {
      state.errors.push('fx: ' + e.message);
      try {
        const { data, base } = await firstOk(FX_SERIES, (u) =>
          u.replace('{from}', isoDate(10)).replace('{to}', isoDate(0)).replace('{symbols}', 'EUR,GBP,JPY,INR,AUD,CAD,CHF,NZD,MXN,ZAR,TRY,CNY,SGD,HKD,BRL,KRW,THB,PHP,AED,PKR'));
        const rates = data.rates || {};
        const days = Object.keys(rates).sort();
        const last = rates[days[days.length - 1]] || {};
        state.fx = { date: days[days.length - 1] || '', usd: {} };
        Object.entries(last).forEach(([k, v]) => { state.fx.usd[k.toLowerCase()] = v; });
        state.fx.usd.usd = 1;
        state.fxSource = 'frankfurter';
        return true;
      } catch (e2) {
        state.errors.push('fx2: ' + e2.message);
        state.fx = { date: '', usd: FALLBACK_RATES };
        state.fxSource = 'offline';
        return false;
      }
    }
  }

  // last-resort rates so the page is never blank
  const FALLBACK_RATES = {
    usd: 1, eur: 0.92, gbp: 0.79, jpy: 156.4, inr: 88.3, aud: 1.52, cad: 1.39,
    chf: 0.88, nzd: 1.68, cny: 7.24, sgd: 1.34, hkd: 7.79, mxn: 18.2, zar: 18.1,
    try: 34.4, brl: 5.62, krw: 1372, thb: 34.6, php: 57.4, aed: 3.67, pkr: 278,
    sek: 10.4, nok: 10.8, dkk: 6.87, pln: 3.98, czk: 23.3, huf: 361, idr: 15800, ngn: 1600,
  };

  const SIM_BASE = {
    BTC: 96500, ETH: 3420, SOL: 198, XRP: 2.31, BNB: 664, DOGE: 0.386, ADA: 0.92,
    AVAX: 41.2, LINK: 24.6, DOT: 7.4, TRX: 0.26, LTC: 118, BCH: 512, TON: 5.6,
    NEAR: 6.1, ATOM: 7.2, ETC: 27.5, FIL: 4.8, APT: 12.4, ARB: 0.78, OP: 1.9,
    INJ: 24.1, SUI: 3.42, HBAR: 0.29, AAVE: 298, UNI: 12.6, XLM: 0.44, SHIB: 0.0000212,
  };

  async function loadCrypto() {
    const symbols = CRYPTO.map((c) => c[1]);
    // --- Binance
    for (const host of BINANCE) {
      try {
        const q = encodeURIComponent(JSON.stringify(symbols));
        const rows = await jget(`${host}/api/v3/ticker/24hr?symbols=${q}`, 7000);
        if (!Array.isArray(rows) || !rows.length) throw new Error('empty');
        const map = {};
        rows.forEach((r) => {
          const sym = r.symbol.replace('USDT', '');
          map[sym] = {
            px: +r.lastPrice,
            chg: +r.priceChangePercent,
            high: +r.highPrice,
            low: +r.lowPrice,
            open: +r.openPrice,
            vol: +r.quoteVolume,
            ts: now(),
          };
        });
        if (Object.keys(map).length < 5) throw new Error('thin');
        state.crypto = map;
        state.cryptoSource = 'binance';
        return true;
      } catch (e) { state.errors.push('binance: ' + e.message); }
    }
    // --- CoinGecko
    try {
      const ids = CRYPTO.map((c) => c[2]).join(',');
      const url = `${COINGECKO[0]}/simple/price?ids=${ids}&vs_currencies=usd,inr&include_24hr_change=true&include_24hr_vol=true`;
      const data = await jget(url, 9000);
      const map = {};
      const inr = {};
      CRYPTO.forEach(([sym, , cgid]) => {
        const row = data[cgid];
        if (!row || row.usd == null) return;
        map[sym] = { px: +row.usd, chg: +(row.usd_24h_change || 0), vol: +(row.usd_24h_vol || 0), ts: now() };
        if (row.inr != null) inr[sym] = { px: +row.inr, chg: +(row.inr_24h_change || 0) };
      });
      if (!Object.keys(map).length) throw new Error('no rows');
      state.crypto = map;
      state.india = inr;
      state.cryptoSource = 'coingecko';
      return true;
    } catch (e) { state.errors.push('coingecko: ' + e.message); }

    // --- offline simulation
    const map = {};
    Object.entries(SIM_BASE).forEach(([sym, base]) => {
      const jitter = 1 + (rnd('sim' + sym + Math.floor(now() / 6e4)) - 0.5) * 0.02;
      map[sym] = {
        px: base * jitter,
        chg: (rnd('c' + sym) - 0.45) * 8,
        high: base * 1.02, low: base * 0.98, vol: base * 1000, ts: now(),
      };
    });
    state.crypto = map;
    state.cryptoSource = 'offline';
    return false;
  }

  function recomputeIndia() {
    const inr = fxRate('USD', 'INR') || FALLBACK_RATES.inr;
    const out = {};
    Object.entries(state.crypto).forEach(([sym, c]) => {
      if (!INDIA_COINS.includes(sym)) return;
      out[sym] = { px: c.px * inr, chg: c.chg, inrRate: inr };
    });
    state.india = out;
  }

  /* ------------------------------------------------------------------ */
  /* OTC synthetic feed (3s candles, 1s ticks)                          */
  /* ------------------------------------------------------------------ */
  function initOtc() {
    OTC_PAIRS.forEach((p) => {
      const inst = state.byId['OTC:' + p];
      const digits = DECIMALS[p.slice(3)] || 5;
      const base = p.startsWith('USD') ? (inst.seed * 40 + 1) : (inst.seed * 1.6 + 0.5);
      const px = p === 'USDINR' ? 88 + inst.seed * 3 : base;
      const s = state.series[inst.id] || (state.series[inst.id] = {});
      const c = [];
      let o = px;
      const t0 = Math.floor(now() / 3000) * 3000 - 240 * 3000;
      for (let i = 0; i < 240; i++) {
        const vol = px * 0.0012;
        const cl = o + (rnd(inst.id + i) - 0.5) * vol * 2;
        const hi = Math.max(o, cl) + rnd(inst.id + 'h' + i) * vol;
        const lo = Math.min(o, cl) - rnd(inst.id + 'l' + i) * vol;
        c.push({ t: t0 + i * 3000, o, h: hi, l: lo, c: cl, v: 100 + rnd(inst.id + 'v' + i) * 900 });
        o = cl;
      }
      s.SYN = c;
      inst.px = c[c.length - 1].c;
      inst.digits = digits;
      inst.chg = ((inst.px - c[0].c) / c[0].c) * 100;
      inst.high = Math.max(...c.slice(-80).map((k) => k.h));
      inst.low = Math.min(...c.slice(-80).map((k) => k.l));
      inst.open = c[0].c;
    });
  }

  let otcTimer = null;
  function tickOtc() {
    OTC_PAIRS.forEach((p) => {
      const inst = state.byId['OTC:' + p];
      const s = state.series[inst.id];
      const arr = s.SYN;
      const last = arr[arr.length - 1];
      const bucket = Math.floor(now() / 3000) * 3000;
      const vol = inst.px * 0.0006 + 1e-9;
      const step = (Math.random() - 0.5) * vol * 2;
      if (bucket !== last.t) {
        arr.push({ t: bucket, o: last.c, h: last.c, l: last.c, c: last.c + step, v: 300 });
        if (arr.length > 300) arr.shift();
      } else {
        const n = arr[arr.length - 1];
        n.c += step;
        n.h = Math.max(n.h, n.c);
        n.l = Math.min(n.l, n.c);
        n.v += 25;
      }
      inst.px = arr[arr.length - 1].c;
      inst.chg = ((inst.px - arr[0].c) / arr[0].c) * 100;
      inst.high = Math.max(...arr.slice(-80).map((k) => k.h));
      inst.low = Math.min(...arr.slice(-80).map((k) => k.l));
    });
  }

  /* ------------------------------------------------------------------ */
  /* candles                                                            */
  /* ------------------------------------------------------------------ */
  const BINANCE_TF = { '1m': '1m', '3m': '3m', '5m': '5m', '15m': '15m' };

  async function cryptoCandles(inst, tf) {
    const key = inst.bsym;
    for (const host of BINANCE) {
      try {
        const rows = await jget(`${host}/api/v3/klines?symbol=${key}&interval=${BINANCE_TF[tf] || '1m'}&limit=140`, 8000);
        if (!Array.isArray(rows) || !rows.length) throw new Error('empty');
        return {
          mode: 'candles',
          candles: rows.map((r) => ({ t: +r[0], o: +r[1], h: +r[2], l: +r[3], c: +r[4], v: +r[5] })),
        };
      } catch (e) { state.errors.push('klines: ' + e.message); }
    }
    // CoinGecko OHLC (30-min granularity, 1 day)
    try {
      const rows = await jget(`${COINGECKO[0]}/coins/${inst.cgid}/ohlc?vs_currency=usd&days=1`, 9000);
      if (!Array.isArray(rows) || !rows.length) throw new Error('empty');
      return {
        mode: 'candles',
        candles: rows.map((r) => ({ t: r[0], o: r[1], h: r[2], l: r[3], c: r[4], v: 0 })),
        source: 'CoinGecko · 30m',
      };
    } catch (e) { state.errors.push('cg ohlc: ' + e.message); }
    return null;
  }

  function isoDate(offsetDays) {
    const d = new Date(now() - offsetDays * 864e5);
    return d.toISOString().slice(0, 10);
  }

  async function fxSeries() {
    if (state.fxHistory) return state.fxHistory;
    const symbols = 'EUR,GBP,JPY,INR,AUD,CAD,CHF,NZD,MXN,ZAR,TRY,CNY,SGD,HKD,BRL,KRW,THB,PHP,AED,PKR';
    try {
      const { data } = await firstOk(FX_SERIES, (u) =>
        u.replace('{from}', isoDate(120)).replace('{to}', isoDate(0)).replace('{symbols}', symbols));
      state.fxHistory = data.rates || null;
      return state.fxHistory;
    } catch (e) { state.errors.push('fx series: ' + e.message); return null; }
  }

  function fxCandlesFor(inst, series) {
    if (!series) return null;
    const days = Object.keys(series).sort();
    const vals = [];
    days.forEach((d) => {
      const row = series[d];
      if (!row) return;
      const cur = row[inst.quote];
      let v = null;
      if (inst.base === 'USD') v = cur;
      else if (inst.quote === 'USD') v = cur ? 1 / cur : null;
      else if (row[inst.base] && cur) v = cur / row[inst.base];
      if (v) vals.push({ t: Date.parse(d + 'T00:00:00Z'), c: v });
    });
    if (vals.length < 5) return null;
    // daily closes only -> build candles with a tiny derived range
    const candles = vals.map((v, i) => {
      const prev = i ? vals[i - 1].c : v.c;
      const spread = Math.abs(v.c - prev) * 0.35 || v.c * 1e-5;
      return {
        t: v.t,
        o: prev,
        c: v.c,
        h: Math.max(prev, v.c) + spread,
        l: Math.min(prev, v.c) - spread,
        v: 0,
        thin: true,
      };
    });
    return { mode: 'candles', candles, source: 'ECB daily' };
  }

  /** Derive a real daily series for every forex pair from one ECB request. */
  function buildFxStats(series) {
    if (!series) return;
    const days = Object.keys(series).sort();
    state.instruments.filter((i) => i.kind === 'forex').forEach((inst) => {
      const vals = [];
      for (const d of days) {
        const row = series[d];
        if (!row) continue;
        const cur = row[inst.quote];
        let v = null;
        if (inst.base === 'USD') v = cur;
        else if (inst.quote === 'USD') v = cur ? 1 / cur : null;
        else if (row[inst.base] && cur) v = cur / row[inst.base];
        if (v) vals.push({ t: Date.parse(d + 'T00:00:00Z'), c: v });
      }
      if (vals.length < 8) return;
      const candles = vals.map((v, i) => {
        const prev = i ? vals[i - 1].c : v.c;
        const spread = Math.abs(v.c - prev) * 0.35 || v.c * 1e-5;
        return { t: v.t, o: prev, c: v.c, h: Math.max(prev, v.c) + spread, l: Math.min(prev, v.c) - spread, v: 0, thin: true };
      });
      const last = vals[vals.length - 1].c;
      const prev = vals[vals.length - 2].c;
      const tail = candles.slice(-30);
      state.fxStats[inst.id] = {
        candles,
        chg: ((last - prev) / prev) * 100,
        high: Math.max(...tail.map((k) => k.h)),
        low: Math.min(...tail.map((k) => k.l)),
      };
    });
  }

  function synthCandles(inst) {
    const s = state.series[inst.id] || (state.series[inst.id] = {});
    if (!s.SYN) {
      let o = state.crypto[inst.coin]?.px || 100;
      const c = [];
      const t0 = Math.floor(now() / 6e4) * 6e4 - 140 * 6e4;
      for (let i = 0; i < 140; i++) {
        const vol = o * 0.004;
        const cl = o + (rnd(inst.id + i) - 0.5) * vol * 2;
        c.push({
          t: t0 + i * 6e4, o, c: cl,
          h: Math.max(o, cl) + rnd(inst.id + 'h' + i) * vol,
          l: Math.min(o, cl) - rnd(inst.id + 'l' + i) * vol,
          v: 100 + rnd(inst.id + 'v' + i) * 900,
        });
        o = cl;
      }
      s.SYN = c;
    }
    return { mode: 'candles', candles: s.SYN.slice(-140), source: 'local simulation' };
  }

  async function candles(id, tf = '1m') {
    const inst = state.byId[id];
    if (!inst) return null;
    if (inst.kind === 'otc') return { mode: 'candles', candles: state.series[id].SYN.slice(-140), source: 'OTC synthetic' };

    const cacheKey = `${id}|${tf}`;
    const s = state.series[id] || (state.series[id] = {});
    if (s[cacheKey] && now() - s[cacheKey].at < 25000) return s[cacheKey].data;

    let out = null;
    if (inst.kind === 'crypto' || inst.kind === 'india') {
      out = await cryptoCandles(inst, tf);
      if (out && inst.kind === 'india') {
        const rate = fxRate('USD', 'INR') || FALLBACK_RATES.inr;
        out = {
          mode: 'candles',
          source: (out.source || 'Binance') + ' · converted to INR',
          candles: out.candles.map((k) => ({ t: k.t, o: k.o * rate, h: k.h * rate, l: k.l * rate, c: k.c * rate, v: k.v })),
        };
      }
    } else if (inst.kind === 'forex') {
      out = fxCandlesFor(inst, await fxSeries());
    }
    if (!out) out = synthCandles(inst);
    s[cacheKey] = { at: now(), data: out };
    return out;
  }

  /**
   * Pull a short 1m candle series for every coin (3 at a time) so grid
   * signals are scored on real candles, not just the 24h snapshot.
   */
  let hydrating = false;
  async function hydrateCrypto() {
    if (hydrating) return;
    hydrating = true;
    const queue = state.instruments.filter((i) => i.kind === 'crypto');
    const worker = async () => {
      while (queue.length) {
        const inst = queue.shift();
        try {
          const data = await cryptoCandles(inst, '1m');
          if (data) {
            const s = state.series[inst.id] || (state.series[inst.id] = {});
            s['1m'] = { at: now(), data };
          }
        } catch { /* keep the snapshot score */ }
      }
    };
    await Promise.all([worker(), worker(), worker()]);
    hydrating = false;
  }

  function cachedCandles(inst) {
    const entry = state.series[inst.id]?.['1m'];
    if (!entry) return null;
    return entry.data?.candles || null;
  }

  /* ------------------------------------------------------------------ */
  /* 5-factor confidence engine                                         */
  /* ------------------------------------------------------------------ */
  function ema(vals, n) {
    const k = 2 / (n + 1);
    let e = vals[0];
    const out = [e];
    for (let i = 1; i < vals.length; i++) { e = vals[i] * k + e * (1 - k); out.push(e); }
    return out;
  }
  function rsi(vals, n = 14) {
    let g = 0, l = 0;
    for (let i = 1; i <= n && i < vals.length; i++) {
      const d = vals[i] - vals[i - 1];
      if (d >= 0) g += d; else l -= d;
    }
    g /= n; l /= n;
    if (!l) return 100;
    const rs = g / l;
    return 100 - 100 / (1 + rs);
  }
  function atr(cs, n = 14) {
    const slice = cs.slice(-n - 1);
    let sum = 0;
    for (let i = 1; i < slice.length; i++) {
      const k = slice[i], p = slice[i - 1];
      sum += Math.max(k.h - k.l, Math.abs(k.h - p.c), Math.abs(k.l - p.c));
    }
    return sum / Math.max(1, slice.length - 1);
  }

  /**
   * Score a market. Works from candles when available, otherwise from the
   * real 24h snapshot (price position inside the high/low range, momentum,
   * volume). Returns direction, confidence and the five factor scores.
   */
  /** Factors derived from a 24h snapshot (change, range position). */
  function snapshotFactors(px, chg, hi, lo) {
    const c = chg || 0;
    const h = hi || px * 1.01;
    const l = lo || px * 0.99;
    const pos = h === l ? 0.5 : (px - l) / (h - l);
    return [
      clamp(c / 3.2, -1.3, 1.3),
      clamp((pos - 0.5) * 2.2, -1.2, 1.2),
      clamp(0.6 - Math.abs(pos - 0.5) * 1.4, -0.7, 1),
      clamp((pos - 0.5) * 2.4, -1.3, 1.3),
      clamp(c / 2.4, -1.3, 1.3),
    ];
  }

  function analyse(inst, ctx) {
    let factors;
    const cs = ctx.candles;
    const px = ctx.price ?? 0;

    if (cs && cs.length > 20) {
      const closes = cs.map((k) => k.c);
      const last = closes[closes.length - 1];
      const e9 = ema(closes.slice(-40), 9), e21 = ema(closes.slice(-60), 21);
      const a = atr(cs) || last * 0.001 || 1e-9;
      // every factor is expressed in ATR units so the score is volatility-neutral
      const trend = clamp(((e9[e9.length - 1] - e21[e21.length - 1]) / a) * 1.35, -1.35, 1.35);
      const r = rsi(closes.slice(-40));
      const mom = clamp((r - 50) / 21, -1.35, 1.35);
      const volPct = (a / (last || 1)) * 100;
      const volScore = clamp(0.55 - Math.abs(volPct - 0.35) * 1.1, -0.75, 0.9);
      const hi = Math.max(...cs.slice(-30).map((k) => k.h));
      const lo = Math.min(...cs.slice(-30).map((k) => k.l));
      const pos = hi === lo ? 0.5 : (last - lo) / (hi - lo);
      const structure = clamp((pos - 0.5) * 2.7, -1.35, 1.35);
      const back = closes[Math.max(0, closes.length - 15)];
      const confluence = clamp(((last - back) / a) * 0.45, -1.35, 1.35);
      const names = [
        'Algorithmic sequence matrix',
        'Kinetic momentum exhaustion',
        'False-breakout shield',
        'Smart money concepts (SMC)',
        'Multi-timeframe confluence',
      ];
      let vals = [trend, mom, volScore, structure, confluence];
      if (ctx.chg != null) {
        // blend the candle read with the live 24h/last-session snapshot
        const snap = snapshotFactors(px, ctx.chg, ctx.high, ctx.low);
        vals = vals.map((v, i) => v * 0.62 + snap[i] * 0.38);
      }
      factors = names.map((n, i) => [n, vals[i]]);
    } else {
      const names = [
        'Algorithmic sequence matrix',
        'Kinetic momentum exhaustion',
        'False-breakout shield',
        'Smart money concepts (SMC)',
        'Multi-timeframe confluence',
      ];
      const snap = snapshotFactors(px, ctx.chg, ctx.high, ctx.low);
      factors = names.map((n, i) => [n, snap[i]]);
    }

    const weights = [0.34, 0.24, 0.16, 0.14, 0.12];
    const raw = factors.reduce((s, f, i) => s + clamp(f[1], -1.4, 1.4) * weights[i], 0);
    const bias = clamp(raw, -1, 1);
    const dir = bias >= 0 ? 'BUY' : 'SELL';
    const strength = Math.abs(bias);
    const jitter = (rnd(inst.id + Math.floor(now() / 3e5)) - 0.5) * 2.4;
    const confidence = clamp(73.5 + Math.pow(strength, 0.72) * 25.5 + jitter, 71.5, 99.4);
    const match = clamp(Math.round(confidence + (rnd(inst.id + 'm') - 0.5) * 1.4), 71, 99);
    const active = confidence >= (state.cuts?.[inst.kind] ?? 86);

    return {
      dir, confidence, match, active,
      factors: factors.map(([name, v]) => ({ name, score: clamp(50 + v * 40, 5, 99) })),
      price: px,
    };
  }

  function rawSignal(inst) {
    const px = priceOf(inst) ?? inst.px ?? null;
    let ctx = { price: px, candles: null, chg: inst.chg ?? null, high: inst.high, low: inst.low };

    if (inst.kind === 'otc') {
      ctx.candles = state.series[inst.id]?.SYN || null;
    } else if (inst.kind === 'forex') {
      const st = state.fxStats[inst.id];
      if (st) ctx = { price: px, candles: st.candles, chg: st.chg, high: st.high, low: st.low };
    } else if (inst.kind === 'crypto') {
      const c = state.crypto[inst.coin] || {};
      ctx = { price: px, candles: cachedCandles(inst), chg: c.chg ?? null, high: c.high, low: c.low };
    } else if (inst.kind === 'india') {
      if (inst.coin) {
        const c = state.crypto[inst.coin] || {};
        const rate = fxRate('USD', 'INR') || FALLBACK_RATES.inr;
        const src = cachedCandles(state.byId['CRYPTO:' + inst.coin]);
        ctx = {
          price: px,
          candles: src ? src.map((k) => ({ t: k.t, o: k.o * rate, h: k.h * rate, l: k.l * rate, c: k.c * rate, v: k.v })) : null,
          chg: c.chg ?? null,
          high: c.high ? c.high * rate : undefined,
          low: c.low ? c.low * rate : undefined,
        };
      } else {
        const st = state.fxStats['FX:USDINR'];
        ctx = st ? { price: px, candles: st.candles, chg: st.chg, high: st.high, low: st.low }
          : { price: px, candles: null, chg: null };
      }
    }

    const a = analyse(inst, ctx);
    return { ...inst, ...a, price: px, chg: ctx.chg, tf: state.tf || '1m' };
  }

  /**
   * Score a market, then rank it: the raw 5-factor score is mapped onto the
   * 78–99 confidence band for display and the top ~34% of instruments are
   * flagged Active — the rest stay on Standby.
   */
  const ACTIVE_FRACTION = 0.34;

  function signals(kind) {
    const list = state.instruments.filter((i) => !kind || kind === 'all' || i.kind === kind);
    const scored = list.map(rawSignal);
    if (scored.length) {
      const asc = scored.slice().sort((a, b) => a.confidence - b.confidence);
      const n = asc.length;
      asc.forEach((s, i) => {
        const p = n > 1 ? i / (n - 1) : 1;
        const c = 78 + p * 21;
        s.confidence = Math.round(c * 10) / 10;
        s.match = Math.max(78, Math.min(99, Math.round(c)));
      });
      const cutIdx = Math.max(0, Math.floor(n * (1 - ACTIVE_FRACTION)));
      const cut = asc[Math.min(n - 1, cutIdx)].confidence;
      scored.forEach((s) => { s.active = s.confidence >= cut; });
      state.view = state.view || {};
      scored.forEach((s) => { state.view[s.id] = { at: now(), data: s }; });
    }
    return scored;
  }

  /** Single instrument, using the ranked view when it is still warm. */
  function signalFor(inst) {
    if (!inst) return null;
    const cached = state.view?.[inst.id];
    if (cached && now() - cached.at < 4000) {
      // overlay a fresh price so values never look stale between scans
      const live = priceOf(inst) ?? inst.px ?? null;
      const chg = inst.kind === 'otc' ? inst.chg
        : inst.kind === 'crypto' ? state.crypto[inst.coin]?.chg
          : inst.kind === 'india' ? (inst.coin ? state.india[inst.coin]?.chg : state.fxStats['FX:USDINR']?.chg)
            : state.fxStats[inst.id]?.chg;
      return { ...cached.data, price: live, chg: chg ?? cached.data.chg };
    }
    const list = signals(inst.kind);
    return list.find((s) => s.id === inst.id) || rawSignal(inst);
  }

  /* ------------------------------------------------------------------ */
  /* public api                                                         */
  /* ------------------------------------------------------------------ */
  async function init() {
    initOtc();
    const [fxOk, crOk] = await Promise.all([loadFx(), loadCrypto()]);
    if (state.cryptoSource === 'coingecko' && Object.keys(state.india).length) {
      // CoinGecko already gave us INR quotes
    } else {
      recomputeIndia();
    }
    state.mode = fxOk && crOk ? 'live' : (fxOk || crOk ? 'mixed' : 'demo');
    state.lastSync = now();
    state.view = {};
    if (!otcTimer) otcTimer = setInterval(tickOtc, 1000);
    // one extra request gives every forex pair 120 days of real closes
    const series = await fxSeries();
    buildFxStats(series);
    state.lastSync = now();
    hydrateCrypto().catch(() => {});
    return state;
  }

  async function refresh() {
    const [fxOk, crOk] = await Promise.all([loadFx(), loadCrypto()]);
    if (state.cryptoSource !== 'coingecko') recomputeIndia();
    state.mode = fxOk && crOk ? 'live' : (fxOk || crOk ? 'mixed' : 'demo');
    state.lastSync = now();
    state.view = {};
    return state;
  }

  function counts() {
    const c = { otc: 0, forex: 0, crypto: 0, india: 0 };
    state.instruments.forEach((i) => { c[i.kind]++; });
    return c;
  }

  setInterval(() => { if (state.mode !== 'demo') hydrateCrypto().catch(() => {}); }, 5 * 60 * 1000);

  return {
    state, init, refresh, signals, signalFor, candles, counts, priceOf, hydrateCrypto,
    fmtPair, sourceLabel: () => state.cryptoSource,
  };
})();
