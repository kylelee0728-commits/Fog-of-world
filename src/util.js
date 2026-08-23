// 地理與網格運算工具

export const M_PER_DEG_LAT = 111320;

/** 兩點間的地表距離（公尺） */
export function distanceM(lat1, lng1, lat2, lng2) {
  const R = 6371008.8;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLng = (lng2 - lng1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
}

/** 經度在該緯度的縮放係數（避免靠近極區時除以 0） */
export function lngScale(lat) {
  return Math.max(Math.cos(lat * Math.PI / 180), 0.05);
}

/** 從 A 指向 B 的方位角（度，正北為 0） */
export function bearing(lat1, lng1, lat2, lng2) {
  const φ1 = lat1 * Math.PI / 180, φ2 = lat2 * Math.PI / 180;
  const Δλ = (lng2 - lng1) * Math.PI / 180;
  const y = Math.sin(Δλ) * Math.cos(φ2);
  const x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ);
  return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
}

const COMPASS = ['北', '東北', '東', '東南', '南', '西南', '西', '西北'];
export function compass(deg) {
  return COMPASS[Math.round(deg / 45) % 8];
}

/*
 * 網格：把座標量化成大致等邊（以公尺計）的格子。
 * 緯度方向固定步長；經度方向依該格緯度做補償，所以格子在任何緯度都接近正方形。
 * key 以 "i,j" 字串表示，i/j 為整數格號。
 */
export function cellKey(lat, lng, sizeM) {
  const stepLat = sizeM / M_PER_DEG_LAT;
  const i = Math.round(lat / stepLat);
  const cellLat = i * stepLat;
  const stepLng = sizeM / (M_PER_DEG_LAT * lngScale(cellLat));
  const j = Math.round(lng / stepLng);
  return i + ',' + j;
}

export function cellCenter(key, sizeM) {
  const [i, j] = key.split(',').map(Number);
  const stepLat = sizeM / M_PER_DEG_LAT;
  const lat = i * stepLat;
  const stepLng = sizeM / (M_PER_DEG_LAT * lngScale(lat));
  return { lat, lng: j * stepLng };
}

/** 回傳以某點為圓心、半徑內所有格子的 key（用於計算已撥霧面積） */
export function cellsInRadius(lat, lng, radiusM, sizeM) {
  const keys = [];
  const stepLat = sizeM / M_PER_DEG_LAT;
  const span = Math.ceil(radiusM / sizeM);
  const i0 = Math.round(lat / stepLat);
  for (let di = -span; di <= span; di++) {
    const i = i0 + di;
    const cellLat = i * stepLat;
    const stepLng = sizeM / (M_PER_DEG_LAT * lngScale(cellLat));
    const j0 = Math.round(lng / stepLng);
    for (let dj = -span; dj <= span; dj++) {
      const j = j0 + dj;
      const cLat = cellLat;
      const cLng = j * stepLng;
      if (distanceM(lat, lng, cLat, cLng) <= radiusM) keys.push(i + ',' + j);
    }
  }
  return keys;
}

/** 以本地時區回傳 YYYY-MM-DD */
export function dayKey(ts = Date.now()) {
  const d = new Date(ts);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export function formatDistance(m) {
  if (m < 1000) return `${Math.round(m)} 公尺`;
  return `${(m / 1000).toFixed(m < 10000 ? 2 : 1)} 公里`;
}

export function formatDate(ts) {
  const d = new Date(ts);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}/${p(d.getMonth() + 1)}/${p(d.getDate())}`;
}

export function formatDateTime(ts) {
  const d = new Date(ts);
  const p = (n) => String(n).padStart(2, '0');
  return `${p(d.getMonth() + 1)}/${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function todayKey() {
  return dayKey(Date.now());
}

/** 連續探索天數（含今天或昨天為止的最長尾端連線） */
export function currentStreak(days) {
  const set = new Set(days);
  if (!set.size) return 0;
  const oneDay = 86400000;
  let cursor = new Date();
  if (!set.has(dayKey(cursor.getTime()))) cursor = new Date(Date.now() - oneDay);
  if (!set.has(dayKey(cursor.getTime()))) return 0;
  let streak = 0;
  let t = cursor.getTime();
  while (set.has(dayKey(t))) { streak++; t -= oneDay; }
  return streak;
}

export function clamp(v, lo, hi) { return Math.min(hi, Math.max(lo, v)); }
