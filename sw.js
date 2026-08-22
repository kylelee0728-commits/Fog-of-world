// 離線快取：程式碼走 cache-first，地圖圖磚走 stale-while-revalidate
const VERSION = 'fow-v3';
const SHELL = [
  './',
  './index.html',
  './styles/main.css',
  './manifest.webmanifest',
  './assets/icon.svg',
  './src/main.js',
  './src/state.js',
  './src/storage.js',
  './src/fogLayer.js',
  './src/tracker.js',
  './src/ui.js',
  './src/util.js',
  './src/landmarks.js',
  './src/achievements.js',
  './src/icons.js',
  './vendor/leaflet/leaflet.css',
  './vendor/leaflet/leaflet.js',
];

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(VERSION)
      .then((c) => Promise.allSettled(SHELL.map((u) => c.add(u))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION && k !== 'fow-tiles').map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  // 地圖圖磚：先給快取，同時在背景更新
  if (/tile\.openstreetmap\.org$/.test(url.hostname)) {
    e.respondWith(
      caches.open('fow-tiles').then(async (cache) => {
        const hit = await cache.match(req);
        const net = fetch(req).then((res) => {
          if (res.ok) cache.put(req, res.clone());
          return res;
        }).catch(() => hit);
        return hit || net;
      })
    );
    return;
  }

  e.respondWith(
    caches.match(req).then((hit) => hit || fetch(req).then((res) => {
      if (res.ok && url.origin === location.origin) {
        const copy = res.clone();
        caches.open(VERSION).then((c) => c.put(req, copy));
      }
      return res;
    }).catch(() => caches.match('./index.html')))
  );
});
