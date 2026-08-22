// 成就定義。每個成就以「目前數值 / 目標數值」判定，達標即解鎖。

// icon 是 src/icons.js 裡的手繪圖示名稱，不是表情符號
const A = (id, icon, name, desc, target, value) => ({ id, icon, name, desc, target, value });

export const ACHIEVEMENTS = [
  // 起步
  A('first_step', 'ach_first', '第一步', '在長夜裡留下第一個腳印', 1, (s) => s.cells),

  // 距離
  A('dist_1', 'ach_distance', '千里之行', '累積步行 1 公里', 1000, (s) => s.distanceM),
  A('dist_5', 'walk', '城市漫遊', '累積步行 5 公里', 5000, (s) => s.distanceM),
  A('dist_10', 'ach_distance', '十公里俱樂部', '累積步行 10 公里', 10000, (s) => s.distanceM),
  A('dist_42', 'award', '馬拉松之魂', '累積步行 42.195 公里', 42195, (s) => s.distanceM),
  A('dist_100', 'ach_distance', '百里長征', '累積步行 100 公里', 100000, (s) => s.distanceM),
  A('dist_500', 'rank_compass', '大陸縱走', '累積步行 500 公里', 500000, (s) => s.distanceM),
  A('dist_1000', 'globe', '環球起點', '累積步行 1000 公里', 1000000, (s) => s.distanceM),

  // 點亮面積
  A('area_01', 'ach_area', '初亮', '點亮 0.1 平方公里', 0.1, (s) => s.areaKm2),
  A('area_1', 'rank_map', '一方天地', '點亮 1 平方公里', 1, (s) => s.areaKm2),
  A('area_10', 'lm_city', '街區重光', '點亮 10 平方公里', 10, (s) => s.areaKm2),
  A('area_50', 'ach_area', '半城燈火', '點亮 50 平方公里', 50, (s) => s.areaKm2),
  A('area_100', 'globe', '破夜者', '點亮 100 平方公里', 100, (s) => s.areaKm2),

  // 習慣
  A('streak_3', 'ach_streak', '三日不輟', '連續 3 天出門探索', 3, (s) => s.streak),
  A('streak_7', 'flame', '一週堅持', '連續 7 天出門探索', 7, (s) => s.streak),
  A('streak_30', 'ach_streak', '月之行者', '連續 30 天出門探索', 30, (s) => s.streak),
  A('days_50', 'ach_time', '五十日談', '累積 50 個探索日', 50, (s) => s.activeDays),
  A('day_5k', 'rank_bolt', '單日五公里', '在同一天內走滿 5 公里', 5000, (s) => s.bestDayM),
  A('session_5k', 'target', '一氣呵成', '單次探索走滿 5 公里', 5000, (s) => s.bestSessionM),

  // 時段
  A('night', 'rank_lamp', '夜行者', '在凌晨 0 點到 4 點之間探索', 1, (s) => s.nightWalk),
  A('dawn', 'rank_sunrise', '拂曉偵察', '在清晨 5 點到 7 點之間探索', 1, (s) => s.dawnWalk),

  // 地標與世界
  A('landmark_1', 'ach_landmark', '世界的第一站', '造訪第 1 個世界地標', 1, (s) => s.landmarks),
  A('landmark_5', 'passport', '背包客', '造訪 5 個世界地標', 5, (s) => s.landmarks),
  A('landmark_15', 'ach_landmark', '旅人', '造訪 15 個世界地標', 15, (s) => s.landmarks),
  A('landmark_30', 'lm_temple', '世界收藏家', '造訪 30 個世界地標', 30, (s) => s.landmarks),
  A('continent_2', 'globe', '跨洲旅人', '在 2 個大洲留下足跡', 2, (s) => s.continents),
  A('continent_4', 'globe', '四海為家', '在 4 個大洲留下足跡', 4, (s) => s.continents),
  A('continent_6', 'globe', '六大洲征服者', '在 6 個大洲留下足跡', 6, (s) => s.continents),

  // 地形
  A('alt_1000', 'ach_altitude', '高地偵察', '在海拔 1000 公尺以上探索', 1000, (s) => s.maxAltitude),
  A('alt_2500', 'lm_mountain', '雲上行者', '在海拔 2500 公尺以上探索', 2500, (s) => s.maxAltitude),

  // 光格
  A('cells_1000', 'ach_cells', '拾光獵人', '點亮 1000 個光格', 1000, (s) => s.cells),
  A('cells_10000', 'rank_crown', '長夜終結者', '點亮 10000 個光格', 10000, (s) => s.cells),
];

export const ACHIEVEMENT_COUNT = ACHIEVEMENTS.length;

/** 回傳這次新達成、但尚未記錄的成就 */
export function evaluate(stats, unlocked) {
  const fresh = [];
  for (const a of ACHIEVEMENTS) {
    if (unlocked[a.id]) continue;
    if (a.value(stats) >= a.target) fresh.push(a);
  }
  return fresh;
}

/** 稱號：依點亮面積晉升。icon 同樣是手繪圖示名稱 */
export const RANKS = [
  { icon: 'rank_candle', name: '提燈人', at: 0 },
  { icon: 'rank_beam', name: '尋光者', at: 0.25 },
  { icon: 'rank_lamp', name: '拾光者', at: 1 },
  { icon: 'rank_compass', name: '巡路人', at: 5 },
  { icon: 'rank_map', name: '繪光師', at: 15 },
  { icon: 'rank_bolt', name: '破夜者', at: 40 },
  { icon: 'rank_sunrise', name: '曦光使者', at: 100 },
  { icon: 'rank_crown', name: '白晝之主', at: 250 },
];

export function rankFor(areaKm2) {
  let idx = 0;
  for (let i = 0; i < RANKS.length; i++) if (areaKm2 >= RANKS[i].at) idx = i;
  const cur = RANKS[idx];
  const next = RANKS[idx + 1] || null;
  const progress = next
    ? (areaKm2 - cur.at) / (next.at - cur.at)
    : 1;
  return { ...cur, level: idx + 1, next, progress: Math.max(0, Math.min(1, progress)) };
}
