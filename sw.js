/* 只快取網站殼層；即時 ETA 與索引一律走網絡，避免看到過期班次。 */
const CACHE = 'hkbus-shell-v2';
const SHELL = ['./', './index.html', './css/style.css', './js/config.js',
  './js/api.js', './js/rail.js', './js/data.js', './js/planner.js', './js/app.js'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(
      keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))
    )).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  // 即時 API、索引、版本檔、地圖圖磚都不進快取
  if (url.origin !== self.location.origin) return;
  if (/\/(data\/|versions\.json|routes\.json|routeFareList)/.test(url.pathname)) {
    e.respondWith(fetch(req));
    return;
  }
  e.respondWith(
    caches.match(req).then((hit) => hit || fetch(req).then((res) => {
      if (res.ok) {
        const copy = res.clone();
        caches.open(CACHE).then((c) => c.put(req, copy));
      }
      return res;
    }))
  );
});
