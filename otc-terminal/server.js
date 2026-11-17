'use strict';

/**
 * Zero-dependency static server for local development.
 *
 * The app has no backend: market data is fetched straight from public APIs in
 * the browser (see public/assets/market.js). This server only serves
 * public/ with clean routes so `npm start` behaves like the deployed site.
 *
 *     node server.js          -> http://localhost:3000
 *     PORT=8080 node server.js
 */

const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, 'public');
const PORT = Number(process.env.PORT) || 3000;

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.txt': 'text/plain; charset=utf-8',
};

const ROUTES = {
  '/': 'index.html',
  '/terminal': 'terminal.html',
  '/checkout': 'checkout.html',
  '/terms': 'terms.html',
};

function send(res, status, body, type = 'text/plain; charset=utf-8') {
  res.writeHead(status, {
    'content-type': type,
    'cache-control': 'no-cache',
    'x-content-type-options': 'nosniff',
  });
  res.end(body);
}

http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const pathname = decodeURIComponent(url.pathname);

  if (req.method !== 'GET' && req.method !== 'HEAD') {
    return send(res, 405, 'Method not allowed');
  }

  const route = ROUTES[pathname.replace(/\/$/, '') || '/'] || pathname;
  const file = path.join(ROOT, route);

  // keep requests inside public/
  if (!file.startsWith(ROOT)) return send(res, 403, 'Forbidden');

  fs.readFile(file, (err, data) => {
    if (err) {
      if (path.extname(file)) return send(res, 404, 'Not found');
      return fs.readFile(path.join(ROOT, '404.html'), (e2, page) =>
        send(res, 404, e2 ? 'Not found' : page, MIME['.html']));
    }
    send(res, 200, data, MIME[path.extname(file)] || 'application/octet-stream');
  });
}).listen(PORT, '0.0.0.0', () => {
  console.log(`OTC Bot -> http://localhost:${PORT}`);
});
