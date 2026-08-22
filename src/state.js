// 遊戲狀態：吃進定位、記錄足跡、算面積、判定地標與成就

import { cellKey, cellsInRadius, distanceM, dayKey, currentStreak } from './util.js';
import { LANDMARKS, VISIT_RADIUS_M } from './landmarks.js';
import { evaluate } from './achievements.js';
import { loadSave, writeSave, emptySave } from './storage.js';

export const CELL_M = 20;    // 細網格：撥霧軌跡的解析度
export const BLOCK_M = 50;   // 粗網格：計算已撥霧面積的單位
const BLOCK_AREA_KM2 = (BLOCK_M * BLOCK_M) / 1e6;

const MIN_STEP_M = 3;        // 小於此距離視為 GPS 抖動，不計入里程
const MAX_STEP_M = 300;      // 大於此距離視為訊號跳點，不計入里程
const MAX_INTERP = 300;      // 兩點間最多補幾個中繼格

export class GameState extends EventTarget {
  constructor(profile) {
    super();
    this.profile = profile;
    this.cells = new Set();
    this.blocks = new Set();
    this.sessionM = 0;
    this.dirty = false;
    this.load(profile);
    setInterval(() => this.flush(), 5000);
    addEventListener('visibilitychange', () => { if (document.hidden) this.flush(); });
    addEventListener('pagehide', () => this.flush());
  }

  load(profile) {
    this.profile = profile;
    this.data = loadSave(profile);
    this.cells = unpack(this.data.cells);
    this.blocks = unpack(this.data.blocks);
    this.sessionM = 0;
    this.emit('reload');
  }

  reset() {
    this.data = emptySave();
    this.cells = new Set();
    this.blocks = new Set();
    this.sessionM = 0;
    this.dirty = true;
    this.flush();
    this.emit('reload');
  }

  emit(type, detail) { this.dispatchEvent(new CustomEvent(type, { detail })); }

  flush() {
    if (!this.dirty) return;
    this.data.cells = pack(this.cells);
    this.data.blocks = pack(this.blocks);
    this.data.updatedAt = Date.now();
    if (writeSave(this.profile, this.data)) this.dirty = false;
  }

  /**
   * 記錄一個定位點。
   * @param {{lat:number,lng:number,alt?:number,accuracy?:number,ts?:number,synthetic?:boolean}} pos
   */
  addPosition(pos) {
    const { lat, lng } = pos;
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
    const ts = pos.ts || Date.now();
    const d = this.data;
    const prev = d.lastPos;

    // 1. 撥霧：把這個點（以及與上一點之間的路徑）畫進細網格
    const before = this.cells.size;
    const newCells = [];
    const mark = (la, ln) => {
      const k = cellKey(la, ln, CELL_M);
      if (!this.cells.has(k)) { this.cells.add(k); newCells.push({ key: k, lat: la, lng: ln }); }
      for (const b of cellsInRadius(la, ln, this.revealRadius, BLOCK_M)) this.blocks.add(b);
    };

    let moved = 0;
    if (prev) {
      moved = distanceM(prev.lat, prev.lng, lat, lng);
      // 兩點距離大時補上中間的格子，避免高速移動留下破洞
      if (moved > CELL_M) {
        const steps = Math.min(MAX_INTERP, Math.ceil(moved / (CELL_M * 0.7)));
        for (let i = 1; i < steps; i++) {
          const t = i / steps;
          mark(prev.lat + (lat - prev.lat) * t, prev.lng + (lng - prev.lng) * t);
        }
      }
    }
    mark(lat, lng);

    // 2. 里程
    //    關鍵：位移小於雜訊門檻時「不更新錨點」，讓慢速步行的位移慢慢累積到門檻，
    //    否則以走路速度（約 1.4 m/s）每次定位都低於門檻，里程會永遠是 0。
    if (!prev) {
      d.days[dayKey(ts)] = d.days[dayKey(ts)] || 0;
      d.lastPos = { lat, lng, ts };
    } else if (moved > MAX_STEP_M) {
      d.lastPos = { lat, lng, ts };            // 訊號跳點：重設錨點，不計里程
    } else if (moved >= MIN_STEP_M) {
      d.distanceM += moved;
      this.sessionM += moved;
      const dk = dayKey(ts);
      d.days[dk] = (d.days[dk] || 0) + moved;
      if (this.sessionM > d.bestSessionM) d.bestSessionM = this.sessionM;
      d.lastPos = { lat, lng, ts };
    } else {
      d.lastPos.ts = ts;                       // 位移太小：保留錨點繼續累積
    }

    // 3. 海拔與時段
    if (Number.isFinite(pos.alt) && pos.alt > d.maxAltitude) d.maxAltitude = pos.alt;
    const hour = new Date(ts).getHours();
    if (hour < 4) d.nightWalk = 1;
    else if (hour >= 5 && hour < 7) d.dawnWalk = 1;

    this.dirty = true;

    // 4. 地標
    const stamped = [];
    for (const lm of LANDMARKS) {
      if (d.landmarks[lm.id]) continue;
      if (distanceM(lat, lng, lm.lat, lm.lng) <= VISIT_RADIUS_M) {
        d.landmarks[lm.id] = ts;
        stamped.push(lm);
      }
    }

    // 5. 成就
    const stats = this.stats();
    const unlocked = evaluate(stats, d.achievements);
    for (const a of unlocked) d.achievements[a.id] = ts;

    if (newCells.length || this.cells.size !== before) this.emit('fog', { cells: newCells });
    if (stamped.length) this.emit('landmarks', { landmarks: stamped });
    if (unlocked.length) this.emit('achievements', { achievements: unlocked });
    this.emit('change', stats);

    return { newCells, stamped, unlocked, stats };
  }

  /** 目前的撥霧半徑，由設定注入 */
  get revealRadius() { return this._radius || 60; }
  set revealRadius(v) { this._radius = v; }

  stats() {
    const d = this.data;
    const days = Object.keys(d.days);
    const visited = Object.keys(d.landmarks);
    const continents = new Set(
      visited.map((id) => LANDMARKS.find((l) => l.id === id)?.continent).filter(Boolean)
    );
    return {
      distanceM: d.distanceM,
      areaKm2: this.blocks.size * BLOCK_AREA_KM2,
      cells: this.cells.size,
      landmarks: visited.length,
      continents: continents.size,
      streak: currentStreak(days),
      activeDays: days.length,
      bestDayM: Math.max(0, ...Object.values(d.days)),
      bestSessionM: d.bestSessionM,
      maxAltitude: d.maxAltitude,
      nightWalk: d.nightWalk,
      dawnWalk: d.dawnWalk,
      achievementCount: Object.keys(d.achievements).length,
    };
  }

  /** 想去清單：只存 id，切換後回傳切換後的狀態 */
  isWished(id) {
    return (this.data.wishlist || []).includes(id);
  }

  toggleWish(id) {
    if (!Array.isArray(this.data.wishlist)) this.data.wishlist = [];
    const i = this.data.wishlist.indexOf(id);
    if (i >= 0) this.data.wishlist.splice(i, 1);
    else this.data.wishlist.push(id);
    this.dirty = true;
    this.flush();
    return i < 0;
  }

  export() {
    this.flush();
    return JSON.stringify({ ...this.data, cells: pack(this.cells), blocks: pack(this.blocks) });
  }

  import(json) {
    const parsed = JSON.parse(json);
    if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.cells)) {
      throw new Error('存檔格式不正確');
    }
    this.data = { ...emptySave(), ...parsed };
    this.cells = unpack(this.data.cells);
    this.blocks = unpack(this.data.blocks);
    this.dirty = true;
    this.flush();
    this.emit('reload');
  }
}

// 網格 key 以扁平整數陣列儲存，比字串陣列省空間
function pack(set) {
  const out = new Array(set.size * 2);
  let n = 0;
  for (const k of set) {
    const c = k.indexOf(',');
    out[n++] = +k.slice(0, c);
    out[n++] = +k.slice(c + 1);
  }
  return out;
}

function unpack(arr) {
  const set = new Set();
  if (!Array.isArray(arr)) return set;
  for (let i = 0; i + 1 < arr.length; i += 2) set.add(arr[i] + ',' + arr[i + 1]);
  return set;
}
