'use strict';

/**
 * Builds the fully static version of the site into ./static.
 *
 * The app is already server-less (all market data is fetched in the browser),
 * so the build only has to make the pages portable:
 *   - internal routes are rewritten to .html so any static host works,
 *   - every asset/route reference becomes relative (sub-path safe),
 *   - a robots.txt and a minimal vercel.json are emitted.
 */

const fs = require('fs');
const path = require('path');

const SRC = path.join(__dirname, 'public');
const OUT = path.join(__dirname, 'static');

fs.rmSync(OUT, { recursive: true, force: true });
fs.mkdirSync(OUT, { recursive: true });

function copyDir(from, to) {
  fs.mkdirSync(to, { recursive: true });
  for (const entry of fs.readdirSync(from, { withFileTypes: true })) {
    const src = path.join(from, entry.name);
    const dest = path.join(to, entry.name);
    if (entry.isDirectory()) copyDir(src, dest);
    else fs.copyFileSync(src, dest);
  }
}
copyDir(SRC, OUT);

/* rewrite routes + inject the local engine */
// Only rewrite route references (end of string, quote, #fragment, query or
// whitespace) so asset paths such as /assets/terminal.js are left untouched.
const rewrites = [
  [/\/terminal(?=["'#?\s]|$)/g, '/terminal.html'],
  [/\/checkout(?=["'#?\s]|$)/g, '/checkout.html'],
  [/\/terms(?=["'#?\s]|$)/g, '/terms.html'],
];

function processFile(file) {
  const ext = path.extname(file);
  if (!['.html', '.js', '.css', '.json', '.txt'].includes(ext)) return;
  let text = fs.readFileSync(file, 'utf8');
  let changed = false;

  if (ext === '.html' || ext === '.js') {
    for (const [pattern, replacement] of rewrites) {
      const next = text.replace(pattern, replacement);
      if (next !== text) { text = next; changed = true; }
    }
  }

  // Inline scripts inside the HTML page (e.g. the checkout activate handler)
  // must use the same relative routes as the external scripts.
  if (ext === '.html') {
    const next = text
      .replace(/'\/(terminal|checkout|terms|index)\.html'/g, "'$1.html'")
      .replace(/location\.href = '\/'/g, "location.href = './'");
    if (next !== text) { text = next; changed = true; }
  }

  // Project sites (e.g. user.github.io/repo/) are served from a sub-path, so
  // every asset and route reference must be relative.
  if (ext === '.html') {
    const next = text
      .replace(/href="\/"/g, 'href="./"')
      .replace(/(href|src)="\/(?!\/)/g, '$1="');
    if (next !== text) { text = next; changed = true; }
  }
  if (ext === '.js') {
    const next = text
      .replace(/'\/terminal\.html'/g, "'terminal.html'")
      .replace(/'\/checkout\.html'/g, "'checkout.html'")
      .replace(/'\/terms\.html'/g, "'terms.html'")
      .replace(/replace\('\/#access'\)/g, "replace('./#access')")
      .replace(/location\.href = '\/'/g, "location.href = './'")
      .replace(/location\.replace\('\/'\)/g, "location.replace('./')")
      .replace(/href="\/(terminal|checkout|terms|index)"/g, 'href="$1.html"')
      .replace(/href="\/"/g, 'href="./"');
    if (next !== text) { text = next; changed = true; }
  }


  if (changed) fs.writeFileSync(file, text);
}

(function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full);
    else processFile(full);
  }
})(OUT);

/* keep the demo out of search results */
fs.writeFileSync(
  path.join(OUT, 'robots.txt'),
  'User-agent: *\nDisallow: /\n'
);

/* Vercel config: clean URLs + explicit html fallbacks + a sane 404 */
fs.writeFileSync(
  path.join(OUT, 'vercel.json'),
  JSON.stringify({
    cleanUrls: true,
    trailingSlash: false,
    headers: [
      {
        source: '/(.*)',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'no-referrer' },
        ],
      },
    ],
  }, null, 2)
);

const count = (function walk(dir, n = 0) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    n = entry.isDirectory() ? walk(path.join(dir, entry.name), n) : n + 1;
  }
  return n;
})(OUT);

console.log(`static build ready: ${count} files -> ${path.relative(process.cwd(), OUT)}`);
