// 拾光者 —— 主程式

import { GameState } from './state.js';
import { fogLayer } from './fogLayer.js';
import { Tracker } from './tracker.js';
import { LANDMARKS } from './landmarks.js';
import { loadSettings, saveSettings, clearSave } from './storage.js';
import { renderStats, renderStatsSheet, renderAchievements, renderPassport, renderLandmark,
  setPassportQuery, setPassportFilter, toast, setGps, openSheet, closeSheets } from './ui.js';
import { formatDistance } from './util.js';
import { icon, landmarkIcon, landmarkIconName } from './icons.js';

const $ = (id) => document.getElementById(id);

const settings = loadSettings();
const profile = () => (settings.sim ? 'demo' : 'real');

const state = new GameState(profile());
state.revealRadius = settings.revealRadius;

const tracker = new Tracker();
tracker.simSpeed = settings.simSpeed;

// ── 地圖 ─────────────────────────────────────────────
const start = state.data.lastPos || { lat: 25.0339, lng: 121.5645 };
const map = L.map('map', {
  center: [start.lat, start.lng],
  zoom: state.data.lastPos ? 16 : 3,
  zoomControl: false,
  attributionControl: true,
  worldCopyJump: true,
  tap: false,
});

L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '© OpenStreetMap',
}).addTo(map);

const fog = fogLayer(state, {
  radius: settings.revealRadius,
  opacity: settings.fogOpacity / 100,
});
fog.addTo(map);

// 玩家位置
const meIcon = L.divIcon({ className: '', html: '<div class="me-marker"></div>', iconSize: [18, 18] });
let meMarker = null;
let accuracyCircle = null;
let trail = L.polyline([], { color: '#f5c86b', weight: 3, opacity: 0.65 }).addTo(map);

// 地標
const lmMarkers = new Map();
for (const lm of LANDMARKS) {
  const m = L.marker([lm.lat, lm.lng], {
    icon: L.divIcon({ className: '', html: `<div class="lm-marker dim">${landmarkIcon(lm.icon)}</div>`, iconSize: [20, 20] }),
    keyboard: false,
  }).bindTooltip(`${lm.zh}<br><small>${lm.country}</small>`, { direction: 'top' });
  m.on('click', () => openLandmark(lm));
  m.addTo(map);
  lmMarkers.set(lm.id, m);
}
function refreshLandmarkMarkers() {
  const wish = new Set(state.data.wishlist || []);
  for (const lm of LANDMARKS) {
    const visited = !!state.data.landmarks[lm.id];
    const el = lmMarkers.get(lm.id).getElement();
    // 去過的亮、想去的用冷光標出來、其餘的壓暗
    if (el) {
      el.firstChild.className = 'lm-marker'
        + (visited ? '' : (wish.has(lm.id) ? ' wish' : ' dim'));
    }
  }
}
map.whenReady(refreshLandmarkMarkers);

// ── 位置更新 ─────────────────────────────────────────
let lastPos = state.data.lastPos;
let lastFollow = 0;

tracker.addEventListener('status', (e) => {
  const { state: st, text } = e.detail;
  setGps(st, text);
});

tracker.addEventListener('position', (e) => {
  const p = e.detail;
  if (!p.synthetic && p.accuracy > settings.accuracyLimit) {
    setGps('error', `訊號誤差 ±${Math.round(p.accuracy)} 公尺，暫不記錄`);
    return;
  }
  lastPos = p;
  state.addPosition(p);

  const ll = [p.lat, p.lng];
  if (!meMarker) {
    meMarker = L.marker(ll, { icon: meIcon, zIndexOffset: 1000, interactive: false }).addTo(map);
    accuracyCircle = L.circle(ll, {
      radius: state.revealRadius, color: '#f5c86b', weight: 1, opacity: 0.35, fillOpacity: 0.05,
    }).addTo(map);
  } else {
    meMarker.setLatLng(ll);
    accuracyCircle.setLatLng(ll).setRadius(state.revealRadius);
  }

  const pts = trail.getLatLngs();
  pts.push(L.latLng(ll));
  if (pts.length > 800) pts.shift();
  trail.setLatLngs(pts);

  if (settings.follow && Date.now() - lastFollow > 450) {
    lastFollow = Date.now();
    map.panTo(ll, { animate: true, duration: 0.4, noMoveStart: true });
  }
});

// ── 狀態變化 ─────────────────────────────────────────
state.addEventListener('change', (e) => renderStats(e.detail));
state.addEventListener('reload', () => {
  renderStats(state.stats());
  refreshLandmarkMarkers();
  trail.setLatLngs([]);
});

state.addEventListener('achievements', (e) => {
  // 一次解鎖太多時只報前幾個，其餘收成一則，免得洗版蓋住地圖
  const list = e.detail.achievements;
  for (const a of list.slice(0, 2)) {
    toast({ icon: a.icon, title: `成就解鎖：${a.name}`, sub: a.desc, gold: true });
  }
  if (list.length > 2) {
    toast({ icon: 'award', title: `另外還解鎖了 ${list.length - 2} 個成就`, sub: '到「成就」看看拿了哪些', gold: true });
  }
  if (!$('sheetAchievements').hidden) renderAchievements(state);
});

state.addEventListener('landmarks', (e) => {
  const list = e.detail.landmarks;
  for (const lm of list.slice(0, 2)) {
    toast({ icon: landmarkIconName(lm.icon), title: `護照蓋章：${lm.zh}`, sub: `${lm.country} · ${lm.continent}`, gold: true });
  }
  if (list.length > 2) {
    toast({ icon: 'passport', title: `另外蓋了 ${list.length - 2} 個地標的章`, sub: '到「護照」看看', gold: true });
  }
  refreshLandmarkMarkers();
  if (!$('sheetPassport').hidden) renderPassport(state, lastPos, openLandmark);
});

// ── 探索開關 ─────────────────────────────────────────
let walking = false;
let wakeLock = null;

async function startWalking() {
  state.startSession();
  walking = true;
  $('btnWalk').classList.add('walking');
  $('btnWalkIcon').innerHTML = icon('pause');
  $('btnWalkText').textContent = '暫停探索';
  tracker.start(settings.sim ? 'sim' : 'gps', {
    start: lastPos ? { lat: lastPos.lat, lng: lastPos.lng } : { ...map.getCenter() },
  });
  try {
    if ('wakeLock' in navigator) wakeLock = await navigator.wakeLock.request('screen');
  } catch { /* 螢幕恆亮不是必要功能 */ }
}

function stopWalking() {
  state.endSession();
  walking = false;
  $('btnWalk').classList.remove('walking');
  $('btnWalkIcon').innerHTML = icon('walk');
  $('btnWalkText').textContent = '開始探索';
  tracker.stop();
  if (wakeLock) { wakeLock.release().catch(() => {}); wakeLock = null; }
}

$('btnWalk').onclick = () => (walking ? stopWalking() : startWalking());

$('btnLocate').onclick = () => {
  if (lastPos) { map.flyTo([lastPos.lat, lastPos.lng], Math.max(map.getZoom(), 16)); return; }
  if (!navigator.geolocation) { toast({ icon: 'target', title: '此裝置不支援定位' }); return; }
  setGps('wait', '定位中…');
  navigator.geolocation.getCurrentPosition(
    (p) => {
      lastPos = { lat: p.coords.latitude, lng: p.coords.longitude };
      map.flyTo([lastPos.lat, lastPos.lng], 16);
      setGps('idle', '已定位，按「開始探索」點亮');
    },
    () => setGps('error', '拿不到定位，請確認已允許權限'),
    { enableHighAccuracy: true, timeout: 15000 }
  );
};

$('btnAchievements').onclick = () => { renderAchievements(state); openSheet('sheetAchievements'); };
$('btnPassport').onclick = () => { renderPassport(state, lastPos, openLandmark); openSheet('sheetPassport'); };
$('btnSettings').onclick = () => openSheet('sheetSettings');
$('chips').onclick = () => { renderStatsSheet(state, settings.dailyGoal || 0); openSheet('sheetStats'); };
for (const b of document.querySelectorAll('[data-close]')) b.onclick = closeSheets;

/** 點護照上的地標：開詳情，而不是直接跳地圖 */
function openLandmark(lm) {
  if (!lm) return;
  const handlers = {
    onShowOnMap: (l) => {
      closeSheets();
      map.flyTo([l.lat, l.lng], 13, { duration: 1.6 });
    },
    onToggleWish: (l) => {
      const added = state.toggleWish(l.id);
      toast({
        icon: 'flame',
        title: added ? '已加入想去' : '已從想去移除',
        sub: l.zh,
      });
      // 重畫詳情讓按鈕文字跟著換，地圖與護照上的標記也要更新
      renderLandmark(state, l, lastPos, handlers);
      refreshLandmarkMarkers();
    },
  };
  renderLandmark(state, lm, lastPos, handlers);
  openSheet('sheetLandmark');
}

// ── 護照的搜尋與篩選 ─────────────────────────────────
$('passSearch').oninput = (e) => {
  setPassportQuery(e.target.value);
  renderPassport(state, lastPos, openLandmark);
};
$('passFilters').onclick = (e) => {
  const b = e.target.closest('[data-filter]');
  if (!b) return;
  for (const c of $('passFilters').children) c.classList.toggle('on', c === b);
  setPassportFilter(b.dataset.filter);
  renderPassport(state, lastPos, openLandmark);
};

// ── 設定 ─────────────────────────────────────────────
function bindRange(id, outId, key, fmt, apply) {
  const input = $(id), out = $(outId);
  input.value = settings[key];
  out.textContent = fmt(settings[key]);
  input.oninput = () => {
    settings[key] = Number(input.value);
    out.textContent = fmt(settings[key]);
    apply(settings[key]);
    saveSettings(settings);
  };
}

bindRange('setRadius', 'outRadius', 'revealRadius', (v) => `${v} m`, (v) => {
  state.revealRadius = v;
  fog.setRadius(v);
  if (accuracyCircle) accuracyCircle.setRadius(v);
});
bindRange('setOpacity', 'outOpacity', 'fogOpacity', (v) => `${v}%`, (v) => fog.setOpacity(v / 100));
bindRange('setAccuracy', 'outAccuracy', 'accuracyLimit', (v) => `${v} m`, () => {});
bindRange('setSimSpeed', 'outSimSpeed', 'simSpeed', (v) => `${v} m/s`, (v) => { tracker.simSpeed = v; });

$('setFollow').checked = settings.follow;
$('setFollow').onchange = (e) => { settings.follow = e.target.checked; saveSettings(settings); };

$('setSim').checked = settings.sim;
$('setSim').onchange = (e) => {
  settings.sim = e.target.checked;
  saveSettings(settings);
  applySimMode();
};

function applySimMode() {
  const wasWalking = walking;
  if (walking) stopWalking();
  state.flush();
  state.load(profile());
  lastPos = state.data.lastPos;
  if (meMarker) { map.removeLayer(meMarker); meMarker = null; }
  if (accuracyCircle) { map.removeLayer(accuracyCircle); accuracyCircle = null; }
  $('simPad').hidden = !settings.sim;
  $('rowSimSpeed').style.display = settings.sim ? '' : 'none';
  $('profileFlag').hidden = !settings.sim;
  setGps('idle', settings.sim ? '模擬模式：資料與真實紀錄分開存放' : '真實模式');
  if (wasWalking) startWalking();
}

$('btnExport').onclick = () => {
  const blob = new Blob([state.export()], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `fog-of-world-${profile()}-${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  toast({ icon: 'export', title: '存檔已匯出' });
};

$('btnImport').onclick = () => $('fileImport').click();
$('fileImport').onchange = async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  try {
    state.import(await file.text());
    fog.rebuildIndex();
    fog.schedule();
    toast({ icon: 'import', title: '存檔已匯入' });
  } catch (err) {
    toast({ icon: 'flame', title: '匯入失敗', sub: String(err.message || err) });
  }
  e.target.value = '';
};

$('btnReset').onclick = () => {
  const label = settings.sim ? '模擬' : '真實';
  if (!confirm(`確定要清除「${label}」存檔嗎？走過的霧會全部長回來，此動作無法復原。`)) return;
  clearSave(profile());
  state.reset();
  fog.rebuildIndex();
  fog.schedule();
  toast({ icon: 'rank_candle', title: '長夜再度降臨', sub: '一切從頭開始' });
};

// ── 模擬操作 ─────────────────────────────────────────
const keys = new Set();
const KEYMAP = {
  ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right',
  w: 'up', s: 'down', a: 'left', d: 'right', W: 'up', S: 'down', A: 'left', D: 'right',
};

function applyKeys() {
  let x = 0, y = 0;
  if (keys.has('up')) y += 1;
  if (keys.has('down')) y -= 1;
  if (keys.has('left')) x -= 1;
  if (keys.has('right')) x += 1;
  tracker.setSimDirection(x, y);
}

addEventListener('keydown', (e) => {
  if (!settings.sim) return;
  const dir = KEYMAP[e.key];
  if (!dir) return;
  e.preventDefault();
  keys.add(dir);
  applyKeys();
  if (!walking) startWalking();
});
addEventListener('keyup', (e) => {
  const dir = KEYMAP[e.key];
  if (!dir) return;
  keys.delete(dir);
  applyKeys();
});

for (const b of document.querySelectorAll('.sim-keys button')) {
  const dir = b.dataset.dir;
  const press = (on) => (ev) => {
    ev.preventDefault();
    if (on) { keys.add(dir); if (!walking) startWalking(); } else keys.delete(dir);
    applyKeys();
  };
  b.addEventListener('pointerdown', press(true));
  b.addEventListener('pointerup', press(false));
  b.addEventListener('pointerleave', press(false));
  b.addEventListener('pointercancel', press(false));
}

map.on('click', (e) => {
  if (!settings.sim) return;
  tracker.setSimTarget(e.latlng.lat, e.latlng.lng);
  if (!walking) startWalking();
});

// ── 開場 ─────────────────────────────────────────────
function beginGame(sim) {
  settings.sim = sim;
  settings.seenIntro = true;
  saveSettings(settings);
  $('setSim').checked = sim;
  $('intro').hidden = true;
  applySimMode();
  if (!sim && !state.data.lastPos) {
    setGps('wait', '正在尋找你的位置…');
    $('btnLocate').click();
  }
  startWalking();
  const s = state.stats();
  if (s.cells === 0) {
    toast({
      icon: 'rank_candle',
      title: sim ? '模擬模式已啟動' : '出發吧',
      sub: sim ? '點地圖任一處，或用方向鍵開始移動' : '走動時光會沿著你的路線亮起來',
      ms: 6000,
    });
  }
}

$('btnStart').onclick = () => beginGame(false);
$('btnStartSim').onclick = () => beginGame(true);

if (settings.seenIntro) {
  $('intro').hidden = true;
  applySimMode();
} else {
  $('rowSimSpeed').style.display = settings.sim ? '' : 'none';
  $('simPad').hidden = !settings.sim;
}

renderStats(state.stats());
if (state.data.distanceM > 0) {
  setGps('idle', `已走過 ${formatDistance(state.data.distanceM)}，按「開始探索」繼續`);
}

// ── PWA ──────────────────────────────────────────────
if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
  addEventListener('load', () => navigator.serviceWorker.register('sw.js').catch(() => {}));
}

// 方便在主控台檢查
window.fow = { map, state, fog, tracker, settings };
