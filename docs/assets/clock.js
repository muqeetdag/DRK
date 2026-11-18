/* =====================================================================
   OTC Bot — shared market clock
   ---------------------------------------------------------------------
   One clock for the whole app. Every signal trades a fixed candle: the
   entry happens a few seconds into the candle and the trade expires when
   that candle closes. Because the schedule is derived from the clock and
   the instrument id, the Live Scanner and the Signals page always show
   the same entry time and the same remaining seconds for the same pair.
   All stamps are displayed in IST (Asia/Kolkata).
   ===================================================================== */
(function () {
  'use strict';

  var TZ = 'Asia/Kolkata';

  var timeFmt = new Intl.DateTimeFormat('en-US', {
    timeZone: TZ, hour: '2-digit', minute: '2-digit', hour12: true,
  });
  var fullFmt = new Intl.DateTimeFormat('en-US', {
    timeZone: TZ, month: 'short', day: 'numeric', year: 'numeric',
  });
  var secFmt = new Intl.DateTimeFormat('en-GB', {
    timeZone: TZ, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  });
  /* entry stamps carry seconds so they can never be mistaken for the
     ticking wall clock: "04:32:11 PM" */
  var stampFmt = new Intl.DateTimeFormat('en-US', {
    timeZone: TZ, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true,
  });
  var fullStampFmt = new Intl.DateTimeFormat('en-US', {
    timeZone: TZ, month: 'short', day: 'numeric', year: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true,
  });

  function hash(str) {
    var h = 2166136261;
    for (var i = 0; i < str.length; i++) {
      h ^= str.charCodeAt(i);
      h = Math.imul(h, 16777619);
    }
    return h >>> 0;
  }

  var pad = function (n) { return String(n).padStart(2, '0'); };

  /** "04:32 PM" in IST */
  function istTime(date) {
    return timeFmt.format(date instanceof Date ? date : new Date(date));
  }

  /** "Nov 17, 2026, 04:32 PM" in IST */
  function istFull(date) {
    var d = date instanceof Date ? date : new Date(date);
    return fullFmt.format(d) + ', ' + timeFmt.format(d);
  }

  /** "04:32:11 PM" in IST (top bars) */
  function istClock(date) {
    var d = date instanceof Date ? date : new Date(date);
    return secFmt.format(d);
  }

  /** "04:32:11 PM" in IST (entry stamps — includes seconds) */
  function istStamp(date) {
    return stampFmt.format(date instanceof Date ? date : new Date(date));
  }

  /** "Nov 17, 2026, 04:32:11 PM" in IST (full entry stamp) */
  function istFullStamp(date) {
    return fullStampFmt.format(date instanceof Date ? date : new Date(date));
  }

  /** 56000 -> "00:56", 3725000 -> "1h 02m" */
  function mmss(ms) {
    var total = Math.max(0, Math.floor(ms / 1000));
    if (total >= 3600) return Math.floor(total / 3600) + 'h ' + pad(Math.floor((total % 3600) / 60)) + 'm';
    return pad(Math.floor(total / 60)) + ':' + pad(total % 60);
  }

  /**
   * Trade schedule for one instrument on one timeframe.
   *
   * Every trade is a fixed window: the entry lands a few seconds after the
   * candle opens (staggered per instrument, so 118 pairs do not all count
   * down in lockstep) and the trade expires exactly `tf` after its entry.
   *
   * Returns the current window plus the countdowns the UI needs:
   *   pending   - the entry has not printed yet
   *   toEntryMs - time until the entry prints
   *   toExpiryMs- time until the trade closes
   *   nextScanMs- the next scheduled scan (used by stand-by cards)
   */
  function plan(id, tfMs, nowMs, opts) {
    var tf = tfMs && tfMs > 0 ? tfMs : 60000;
    var now = nowMs == null ? Date.now() : nowMs;
    var fresh = !!(opts && opts.fresh);
    var bucket = Math.floor(now / tf) * tf;
    var maxOff = Math.max(3, Math.min(45, Math.floor((tf / 1000) * 0.55)));
    var offsetSec = 2 + (hash(id + '@' + bucket) % Math.max(1, maxOff - 2));

    var entryMs = bucket + offsetSec * 1000;
    var expiryMs = entryMs + tf;

    if (expiryMs <= now || (fresh && expiryMs - now < tf * 0.18)) {
      bucket += tf;
      entryMs = bucket + offsetSec * 1000;
      expiryMs = entryMs + tf;
    }

    return {
      entryMs: entryMs,
      expiryMs: expiryMs,
      pending: now < entryMs,
      toEntryMs: Math.max(0, entryMs - now),
      toExpiryMs: Math.max(0, expiryMs - now),
      nextScanMs: Math.ceil(now / tf) * tf,
      windowMs: tf,
    };
  }

  /** runs fn immediately and then once a second */
  function everySecond(fn) {
    fn();
    return setInterval(fn, 1000);
  }

  window.CLOCK = {
    TZ: TZ,
    hash: hash,
    istTime: istTime,
    istFull: istFull,
    istClock: istClock,
    istStamp: istStamp,
    istFullStamp: istFullStamp,
    mmss: mmss,
    plan: plan,
    everySecond: everySecond,
  };
})();
