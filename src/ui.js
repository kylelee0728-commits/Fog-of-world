// 介面繪製：狀態列、成就、世界護照、提示訊息

import { ACHIEVEMENTS, rankFor } from './achievements.js';
import { LANDMARKS, CONTINENTS, VISIT_RADIUS_M } from './landmarks.js';
import { distanceM, bearing, compass, formatDate, formatDistance } from './util.js';
import { icon, landmarkIcon } from './icons.js';

const $ = (id) => document.getElementById(id);

export function renderStats(stats) {
  $('statDistance').textContent = (stats.distanceM / 1000).toFixed(2);
  $('statArea').textContent = stats.areaKm2 < 10 ? stats.areaKm2.toFixed(2) : stats.areaKm2.toFixed(1);
  $('statLandmarks').textContent = String(stats.landmarks);

  const rank = rankFor(stats.areaKm2);
  $('rankIcon').innerHTML = icon(rank.icon);
  $('rankName').textContent = `Lv.${rank.level} ${rank.name}`;
  $('rankFill').style.width = `${(rank.progress * 100).toFixed(1)}%`;
  $('rankName').title = rank.next ? `距離「${rank.next.name}」還差 ${(rank.next.at - stats.areaKm2).toFixed(2)} km²` : '已達最高階級';
}

export function renderAchievements(state) {
  const stats = state.stats();
  const got = state.data.achievements;
  $('achSummary').textContent = `${Object.keys(got).length} / ${ACHIEVEMENTS.length} 已解鎖`;
  $('achList').innerHTML = ACHIEVEMENTS.map((a) => {
    const at = got[a.id];
    const cur = a.value(stats);
    const pct = Math.min(100, (cur / a.target) * 100);
    return `
      <div class="ach ${at ? 'unlocked' : 'locked'}">
        <div class="ach-icon">${icon(at ? a.icon : 'lock')}</div>
        <div style="min-width:0">
          <div class="ach-name">${a.name}</div>
          <div class="ach-desc">${a.desc}</div>
          ${at
            ? `<div class="ach-date">${formatDate(at)} 解鎖</div>`
            : `<div class="ach-progress"><span style="width:${pct}%"></span></div>`}
        </div>
      </div>`;
  }).join('');
}

let passQuery = '';
let passFilter = 'all';

/** 搜尋與篩選由面板自己記著，重繪時沿用 */
export function setPassportQuery(q) { passQuery = q; }
export function setPassportFilter(f) { passFilter = f; }

export function renderPassport(state, pos, onPick) {
  const visited = state.data.landmarks;
  const wish = new Set(state.data.wishlist || []);
  const count = Object.keys(visited).length;
  $('passSummary').textContent = `${count} / ${LANDMARKS.length} 個地標 · ${new Set(
    Object.keys(visited).map((id) => LANDMARKS.find((l) => l.id === id)?.continent)
  ).size} 個大洲`;

  // 最近的未造訪地標
  const box = $('nearestBox');
  if (pos && !passQuery && passFilter === 'all') {
    const pending = LANDMARKS.filter((l) => !visited[l.id])
      .map((l) => ({ l, d: distanceM(pos.lat, pos.lng, l.lat, l.lng) }))
      .sort((a, b) => a.d - b.d)[0];
    if (pending) {
      const dir = compass(bearing(pos.lat, pos.lng, pending.l.lat, pending.l.lng));
      box.hidden = false;
      box.innerHTML = `<b>${landmarkIcon(pending.l.icon)} 最近的目標：${pending.l.zh}</b>
        <div>往${dir}方 ${formatDistance(pending.d)} · 走進 ${VISIT_RADIUS_M / 1000} 公里內即可蓋章</div>`;
    } else { box.hidden = true; }
  } else { box.hidden = true; }

  // 搜尋比對中文名、英文名與國家，用哪一種語言都找得到
  const q = passQuery.trim().toLowerCase();
  const shown = LANDMARKS.filter((l) => {
    const hitQuery = !q
      || l.zh.toLowerCase().includes(q)
      || l.en.toLowerCase().includes(q)
      || l.country.toLowerCase().includes(q)
      || l.countryEn.toLowerCase().includes(q);
    const hitFilter = passFilter === 'all'
      || (passFilter === 'visited' && visited[l.id])
      || (passFilter === 'unvisited' && !visited[l.id])
      || (passFilter === 'wish' && wish.has(l.id));
    return hitQuery && hitFilter;
  });

  const html = CONTINENTS.map((cont) => {
    const list = shown.filter((l) => l.continent === cont);
    if (!list.length) return '';
    const done = list.filter((l) => visited[l.id]).length;
    const rows = list.map((l) => {
      const at = visited[l.id];
      const d = pos ? distanceM(pos.lat, pos.lng, l.lat, l.lng) : null;
      return `<div class="lm ${at ? 'visited' : ''}" data-lm="${l.id}">
          <div class="lm-stamp">${at ? landmarkIcon(l.icon) : icon('lock')}</div>
          <div class="lm-main">
            <div class="lm-name">${l.zh}</div>
            <div class="lm-meta">${l.country} · ${l.en}</div>
          </div>
          <div class="lm-dist">${!at && wish.has(l.id) ? `<span class="wish">${icon('flame')}</span>` : ''}${at ? formatDate(at) : (d != null ? formatDistance(d) : '')}</div>
        </div>`;
    }).join('');
    return `<div class="continent">
        <div class="continent-head"><h3>${cont}</h3><small>${done} / ${list.length}</small></div>
        ${rows}
      </div>`;
  }).join('');

  const el = $('passList');
  el.innerHTML = shown.length ? html : '<div class="empty">沒有符合的地標</div>';
  el.onclick = (e) => {
    const row = e.target.closest('[data-lm]');
    if (row && onPick) onPick(LANDMARKS.find((l) => l.id === row.dataset.lm));
  };
}

/** 單一地標的詳情：介紹、距離方位、蓋章狀態、想去清單 */
export function renderLandmark(state, lm, pos, { onShowOnMap, onToggleWish }) {
  const at = state.data.landmarks[lm.id];
  const wished = state.isWished(lm.id);

  $('lmTitle').innerHTML = `${landmarkIcon(lm.icon)} ${lm.zh}`;
  $('lmSub').textContent = `${lm.country} · ${lm.continent} · ${lm.en}`;

  const status = at
    ? `<div class="lm-status done">${icon('check')} 已蓋章 · ${formatDate(at)}</div>`
    : `<div class="lm-status">${icon('lock')} 還沒去過 —— 走進 ${VISIT_RADIUS_M / 1000} 公里內就會自動蓋章</div>`;

  // 站在原地時方位是雜訊，不如不說
  const away = pos ? distanceM(pos.lat, pos.lng, lm.lat, lm.lng) : null;
  const where = away == null ? '還沒定位，無法計算距離'
    : away < 50 ? '你就在這裡'
    : `距離你 ${formatDistance(away)} · 往${compass(bearing(pos.lat, pos.lng, lm.lat, lm.lng))}方`;

  $('lmBody').innerHTML = `
    ${status}
    <div class="lm-where">${where}</div>
    <h3 class="lm-h">關於這裡</h3>
    <p class="lm-desc">${lm.descZh}</p>
    <div class="row-actions">
      <button class="btn" id="lmMap">${icon('target')} 在地圖上看</button>
      <button class="btn" id="lmWish">${icon('flame')} ${wished ? '從想去移除' : '加入想去'}</button>
    </div>`;

  $('lmMap').onclick = () => onShowOnMap(lm);
  $('lmWish').onclick = () => onToggleWish(lm);
}

const MAX_TOASTS = 4;
let toastSeq = 0;
export function toast({ icon: iconName = 'ach_first', title, sub = '', gold = false, ms = 4200 }) {
  const el = document.createElement('div');
  el.className = 'toast' + (gold ? ' gold' : '');
  el.innerHTML = `<div class="toast-row"><div class="ic">${icon(iconName)}</div>
    <div><b>${title}</b>${sub ? `<small>${sub}</small>` : ''}</div></div>`;
  const box = $('toasts');
  box.appendChild(el);
  while (box.children.length > MAX_TOASTS) box.firstChild.remove();
  const id = ++toastSeq;
  setTimeout(() => {
    el.classList.add('fade');
    setTimeout(() => el.remove(), 450);
  }, ms + (id % 3) * 120);
}

export function setGps(state, text) {
  const el = $('gpsStatus');
  el.hidden = false;
  el.className = 'gps-status' + (state === 'live' ? ' live' : state === 'error' ? ' error' : '');
  $('gpsText').textContent = text;
}

export function openSheet(id) {
  closeSheets();
  const el = $(id);
  if (el) el.hidden = false;
}

export function closeSheets() {
  for (const s of document.querySelectorAll('.sheet')) s.hidden = true;
}
