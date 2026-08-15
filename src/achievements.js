// 成就定義。每個成就以「目前數值 / 目標數值」判定，達標即解鎖。

const A = (id, icon, name, desc, target, value) => ({ id, icon, name, desc, target, value });

export const ACHIEVEMENTS = [
  // 起步
  A('first_step', '🥾', '初次踏出', '記錄下你的第一個座標', 1, (s) => s.cells),

  // 距離
  A('dist_1', '👣', '千里之行', '累積步行 1 公里', 1000, (s) => s.distanceM),
  A('dist_5', '🚶', '城市漫遊', '累積步行 5 公里', 5000, (s) => s.distanceM),
  A('dist_10', '🏃', '十公里俱樂部', '累積步行 10 公里', 10000, (s) => s.distanceM),
  A('dist_42', '🏅', '馬拉松之魂', '累積步行 42.195 公里', 42195, (s) => s.distanceM),
  A('dist_100', '🛤️', '百里長征', '累積步行 100 公里', 100000, (s) => s.distanceM),
  A('dist_500', '🧭', '大陸縱走', '累積步行 500 公里', 500000, (s) => s.distanceM),
  A('dist_1000', '🌍', '環球起點', '累積步行 1000 公里', 1000000, (s) => s.distanceM),

  // 撥霧面積
  A('area_01', '🌫️', '撥雲見日', '撥開 0.1 平方公里的迷霧', 0.1, (s) => s.areaKm2),
  A('area_1', '🗺️', '一方天地', '撥開 1 平方公里的迷霧', 1, (s) => s.areaKm2),
  A('area_10', '🏙️', '城區解放', '撥開 10 平方公里的迷霧', 10, (s) => s.areaKm2),
  A('area_50', '🌆', '區域霸主', '撥開 50 平方公里的迷霧', 50, (s) => s.areaKm2),
  A('area_100', '🌐', '破霧者', '撥開 100 平方公里的迷霧', 100, (s) => s.areaKm2),

  // 習慣
  A('streak_3', '📅', '三日不輟', '連續 3 天出門探索', 3, (s) => s.streak),
  A('streak_7', '🔥', '一週堅持', '連續 7 天出門探索', 7, (s) => s.streak),
  A('streak_30', '💎', '月之行者', '連續 30 天出門探索', 30, (s) => s.streak),
  A('days_50', '🗓️', '五十日談', '累積 50 個探索日', 50, (s) => s.activeDays),
  A('day_5k', '⚡', '單日五公里', '在同一天內走滿 5 公里', 5000, (s) => s.bestDayM),
  A('session_5k', '🎯', '一氣呵成', '單次探索走滿 5 公里', 5000, (s) => s.bestSessionM),

  // 時段
  A('night', '🌙', '夜行者', '在凌晨 0 點到 4 點之間探索', 1, (s) => s.nightWalk),
  A('dawn', '🌅', '拂曉偵察', '在清晨 5 點到 7 點之間探索', 1, (s) => s.dawnWalk),

  // 地標與世界
  A('landmark_1', '📍', '世界的第一站', '造訪第 1 個世界地標', 1, (s) => s.landmarks),
  A('landmark_5', '🎒', '背包客', '造訪 5 個世界地標', 5, (s) => s.landmarks),
  A('landmark_15', '✈️', '旅人', '造訪 15 個世界地標', 15, (s) => s.landmarks),
  A('landmark_30', '🏛️', '世界收藏家', '造訪 30 個世界地標', 30, (s) => s.landmarks),
  A('continent_2', '🌏', '跨洲旅人', '在 2 個大洲留下足跡', 2, (s) => s.continents),
  A('continent_4', '🌎', '四海為家', '在 4 個大洲留下足跡', 4, (s) => s.continents),
  A('continent_6', '🌍', '六大洲征服者', '在 6 個大洲留下足跡', 6, (s) => s.continents),

  // 地形
  A('alt_1000', '⛰️', '高地偵察', '在海拔 1000 公尺以上探索', 1000, (s) => s.maxAltitude),
  A('alt_2500', '🏔️', '雲上行者', '在海拔 2500 公尺以上探索', 2500, (s) => s.maxAltitude),

  // 迷霧
  A('cells_1000', '🌁', '迷霧獵人', '清除 1000 個霧格', 1000, (s) => s.cells),
  A('cells_10000', '👑', '霧之領主', '清除 10000 個霧格', 10000, (s) => s.cells),
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

/** 軍階：依撥霧面積晉升 */
export const RANKS = [
  { icon: '🌫️', name: '迷霧新兵', at: 0 },
  { icon: '🔦', name: '偵察兵', at: 0.25 },
  { icon: '🥾', name: '拓荒者', at: 1 },
  { icon: '🧭', name: '遊俠', at: 5 },
  { icon: '🗺️', name: '製圖師', at: 15 },
  { icon: '⚔️', name: '破霧者', at: 40 },
  { icon: '🏆', name: '世界行者', at: 100 },
  { icon: '👑', name: '霧之領主', at: 250 },
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
