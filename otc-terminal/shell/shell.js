/* =====================================================================
   OTC Bot — CDN boot shell
   ---------------------------------------------------------------------
   Fetched by shell/index.html from the pinned docs mirror. Picks a working
   CDN base, then mounts the requested page (?p=index|terminal|generator|
   scanner|checkout|terms) with its CSS inlined and its scripts rewritten so
   deep links keep the pretty URL.
   ===================================================================== */
(function () {
  "use strict";

  /* the deploying page tells us which build to read; the literal is only a
     fallback for loading this file on its own */
  var SHA = window.OTC_SHELL_SHA || "7194074d1e4af8f19c12abca1f64a1ea0607bc64";
  var BASES = [
    "https://cdn.jsdelivr.net/gh/muqeetdag/DRK@" + SHA + "/docs/",
    "https://raw.githubusercontent.com/muqeetdag/DRK/" + SHA + "/docs/"
  ];
  var PAGES = ["index", "terminal", "generator", "scanner", "checkout", "terms"];
  var params = new URLSearchParams(location.search);
  var page = PAGES.indexOf(params.get("p")) >= 0 ? params.get("p") : "index";
  var base = null;

  function fail(message) {
    var msg = document.getElementById("bootMsg");
    if (!msg) return;
    msg.textContent = message;
    var btn = document.createElement("button");
    btn.textContent = "Retry";
    btn.onclick = function () { location.reload(); };
    msg.parentNode.appendChild(btn);
  }

  function pickBase() {
    if (base) return Promise.resolve(base);
    return BASES.reduce(function (chain, candidate) {
      return chain.catch(function () {
        return fetch(candidate + "index.html", { cache: "no-store" })
          .then(function (r) {
            if (!r.ok) throw new Error("bad status " + r.status);
            base = candidate;
            return candidate;
          });
      });
    }, Promise.reject(new Error("start")));
  }

  function get(path) {
    return pickBase()
      .then(function (b) { return fetch(b + path, { cache: "force-cache" }); })
      .then(function (r) {
        if (!r.ok) throw new Error("missing " + path);
        return r.text();
      });
  }

  function rewriteJS(code) {
    return code
      .replace(/(['"])\/(terminal|generator|scanner|checkout|terms|index)\.html\1/g, "$1?p=$2$1")
      .replace(/'terminal\.html'/g, "'?p=terminal'")
      .replace(/'scanner\.html'/g, "'?p=scanner'")
      .replace(/'generator\.html'/g, "'?p=generator'")
      .replace(/'checkout\.html'/g, "'?p=checkout'")
      .replace(/'terms\.html'/g, "'?p=terms'")
      .replace(/'index\.html'/g, "'?p=index'")
      .replace(/href="terminal\.html"/g, 'href="?p=terminal"')
      .replace(/href="scanner\.html"/g, 'href="?p=scanner"')
      .replace(/href="generator\.html"/g, 'href="?p=generator"')
      .replace(/href="checkout\.html"/g, 'href="?p=checkout"')
      .replace(/href="terms\.html"/g, 'href="?p=terms"')
      .replace(/href="index\.html"/g, 'href="?p=index"')
      .replace(/location\.href = '\.\/'/g, "location.href = '?p=index'")
      .replace(/location\.replace\('\.\/'\)/g, "location.replace('?p=index')")
      .replace(/replace\('\.\/#access'\)/g, "replace('?p=index#access')")
      .replace(/replace\('\/#access'\)/g, "replace('?p=index#access')");
  }

  function rewriteLinks(root) {
    var links = root.querySelectorAll("a[href]");
    for (var i = 0; i < links.length; i++) {
      var a = links[i];
      var href = a.getAttribute("href");
      if (!href) continue;
      if (href === "terminal.html") a.setAttribute("href", "?p=terminal");
      else if (href === "scanner.html") a.setAttribute("href", "?p=scanner");
      else if (href === "generator.html") a.setAttribute("href", "?p=generator");
      else if (href === "checkout.html") a.setAttribute("href", "?p=checkout");
      else if (href === "terms.html") a.setAttribute("href", "?p=terms");
      else if (href === "index.html" || href === "./" || href === "/") a.setAttribute("href", "?p=index");
      else if (href === "./#access" || href === "/#access") a.setAttribute("href", "?p=index#access");
      else if (href.indexOf("assets/") === 0) a.setAttribute("href", base + href);
    }
    var imgs = root.querySelectorAll("img[src^='assets/'], link[href^='assets/']");
    for (var j = 0; j < imgs.length; j++) {
      var el = imgs[j];
      var attr = el.tagName === "IMG" ? "src" : "href";
      el.setAttribute(attr, base + el.getAttribute(attr));
    }
  }

  function inlineStyles(doc) {
    var links = doc.querySelectorAll("link[rel='stylesheet']");
    var chain = Promise.resolve();
    Array.prototype.forEach.call(links, function (link) {
      var href = link.getAttribute("href") || "";
      if (href.indexOf("assets/") !== 0) return;
      chain = chain.then(function () {
        return get(href).then(function (css) {
          var style = document.createElement("style");
          style.textContent = css;
          document.head.appendChild(style);
        });
      });
    });
    return chain;
  }

  // external and inline scripts run in document order (config.js must come
  // before the page's own inline script)
  function runScripts(doc) {
    var scripts = Array.prototype.slice.call(doc.querySelectorAll("script"));
    var chain = Promise.resolve();
    scripts.forEach(function (tag) {
      var src = tag.getAttribute("src") || "";
      var inline = !src;
      if (!inline && src.indexOf("assets/") !== 0) return;
      chain = chain.then(function () {
        var apply = function (code) {
          var el = document.createElement("script");
          el.textContent = rewriteJS(code);
          document.body.appendChild(el);
        };
        if (inline) { apply(tag.textContent || ""); return null; }
        return get(src).then(apply);
      });
    });
    return chain;
  }

  function mount(html) {
    var doc = new DOMParser().parseFromString(html, "text/html");
    document.title = doc.title || "OTC Bot";

    return inlineStyles(doc).then(function () {
      document.body.className = doc.body.className || "";
      var boot = document.getElementById("boot");
      document.body.innerHTML = doc.body.innerHTML;
      document.body.classList.add("mounted");
      if (boot && boot.parentNode) boot.parentNode.removeChild(boot);
      rewriteLinks(document.body);
      if (location.hash) {
        var target = document.getElementById(location.hash.slice(1));
        if (target) target.scrollIntoView();
      }
      return runScripts(doc);
    });
  }

  pickBase()
    .then(function () { return get(page + ".html"); })
    .then(mount)
    .catch(function (err) {
      console.error(err);
      fail("Could not reach the app bundle. Check your connection and retry.");
    });
})();
