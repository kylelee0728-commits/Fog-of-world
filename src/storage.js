// 本機儲存：存檔（可分「真實」與「模擬」兩份）與設定

const NS = 'fow:v1';

export const DEFAULT_SETTINGS = {
  revealRadius: 60,   // 撥霧半徑（公尺）
  fogOpacity: 88,     // 迷霧濃度（%）
  accuracyLimit: 60,  // GPS 精度門檻（公尺）
  follow: true,       // 畫面跟隨
  sim: false,         // 模擬模式
  simSpeed: 12,       // 模擬速度（公尺/秒）
  seenIntro: false,
};

export function loadSettings() {
  try {
    return { ...DEFAULT_SETTINGS, ...JSON.parse(localStorage.getItem(`${NS}:settings`) || '{}') };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

export function saveSettings(s) {
  try { localStorage.setItem(`${NS}:settings`, JSON.stringify(s)); } catch (e) { console.warn('設定儲存失敗', e); }
}

const saveKey = (profile) => `${NS}:save:${profile}`;

export function emptySave() {
  return {
    v: 1,
    createdAt: Date.now(),
    updatedAt: Date.now(),
    distanceM: 0,
    cells: [],        // 細網格（畫霧用），扁平化的 [i, j, i, j, ...]
    blocks: [],       // 粗網格（算面積用）
    days: {},         // { 'YYYY-MM-DD': 當日公尺數 }
    landmarks: {},    // { landmarkId: 造訪時間 }
    achievements: {}, // { achievementId: 解鎖時間 }
    maxAltitude: 0,
    nightWalk: 0,
    dawnWalk: 0,
    bestSessionM: 0,
    lastPos: null,
  };
}

export function loadSave(profile) {
  try {
    const raw = localStorage.getItem(saveKey(profile));
    if (!raw) return emptySave();
    return { ...emptySave(), ...JSON.parse(raw) };
  } catch (e) {
    console.warn('讀檔失敗，改用新存檔', e);
    return emptySave();
  }
}

export function writeSave(profile, data) {
  try {
    localStorage.setItem(saveKey(profile), JSON.stringify(data));
    return true;
  } catch (e) {
    console.warn('存檔失敗（可能超出瀏覽器容量）', e);
    return false;
  }
}

export function clearSave(profile) {
  localStorage.removeItem(saveKey(profile));
}
