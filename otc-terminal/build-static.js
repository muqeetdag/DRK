'use strict';

/**
 * Builds the fully static (server-less) version of the terminal into ./static.
 *
 * The pages are identical to the Node build; the only differences are:
 *   - assets/engine-local.js is loaded first, which shims fetch()/EventSource
 *     so the simulated engine runs in the browser (no backend required),
 *   - internal routes are rewritten to .html so any static host works,
 *   - a robots.txt keeps the demo out of search indexes.
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
];

function processFile(file) {
  const ext = path.extname(file);
  if (!['.html', '.js', '.css', '.json', '.txt'].includes(ext)) return;
  let text = fs.readFileSync(file, 'utf8');
  let changed = false;

  // Inject the local engine before config.js — must happen before the
  // asset paths below are made relative.
  if (ext === '.html' && text.includes('/assets/config.js')) {
    text = text.replace(
      '<script src="/assets/config.js"></script>',
      '<script src="/assets/engine-local.js"></script>\n<script src="/assets/config.js"></script>'
    );
    changed = true;
  }

  if (ext === '.html' || ext === '.js') {
    for (const [pattern, replacement] of rewrites) {
      const next = text.replace(pattern, replacement);
      if (next !== text) { text = next; changed = true; }
    }
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
      .replace(/replace\('\/#access'\)/g, "replace('./#access')")
      .replace(/location\.href = '\/'/g, "location.href = './'");
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
