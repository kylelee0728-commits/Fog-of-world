package com.fogofworld.data

/** 成就：以「目前數值 / 目標數值」判定，達標即解鎖 */
data class Achievement(
    val id: String,
    val icon: String,
    val name: String,
    val desc: String,
    val target: Double,
    val value: (Stats) -> Double,
)

object Achievements {

    val ALL: List<Achievement> = listOf(
        Achievement("first_step", "🥾", "第一步", "在長夜裡留下第一個腳印", 1.0) { it.cells.toDouble() },

        Achievement("dist_1", "👣", "千里之行", "累積步行 1 公里", 1_000.0) { it.distanceM },
        Achievement("dist_5", "🚶", "城市漫遊", "累積步行 5 公里", 5_000.0) { it.distanceM },
        Achievement("dist_10", "🏃", "十公里俱樂部", "累積步行 10 公里", 10_000.0) { it.distanceM },
        Achievement("dist_42", "🏅", "馬拉松之魂", "累積步行 42.195 公里", 42_195.0) { it.distanceM },
        Achievement("dist_100", "🛤️", "百里長征", "累積步行 100 公里", 100_000.0) { it.distanceM },
        Achievement("dist_500", "🧭", "大陸縱走", "累積步行 500 公里", 500_000.0) { it.distanceM },
        Achievement("dist_1000", "🌍", "環球起點", "累積步行 1000 公里", 1_000_000.0) { it.distanceM },

        Achievement("area_01", "✨", "初亮", "點亮 0.1 平方公里", 0.1) { it.areaKm2 },
        Achievement("area_1", "🗺️", "一方天地", "點亮 1 平方公里", 1.0) { it.areaKm2 },
        Achievement("area_10", "🏙️", "街區重光", "點亮 10 平方公里", 10.0) { it.areaKm2 },
        Achievement("area_50", "🌆", "半城燈火", "點亮 50 平方公里", 50.0) { it.areaKm2 },
        Achievement("area_100", "🌐", "破夜者", "點亮 100 平方公里", 100.0) { it.areaKm2 },

        Achievement("streak_3", "📅", "三日不輟", "連續 3 天出門探索", 3.0) { it.streak.toDouble() },
        Achievement("streak_7", "🔥", "一週堅持", "連續 7 天出門探索", 7.0) { it.streak.toDouble() },
        Achievement("streak_30", "💎", "月之行者", "連續 30 天出門探索", 30.0) { it.streak.toDouble() },
        Achievement("days_50", "🗓️", "五十日談", "累積 50 個探索日", 50.0) { it.activeDays.toDouble() },
        Achievement("day_5k", "⚡", "單日五公里", "在同一天內走滿 5 公里", 5_000.0) { it.bestDayM },
        Achievement("session_5k", "🎯", "一氣呵成", "單次探索走滿 5 公里", 5_000.0) { it.bestSessionM },

        Achievement("night", "🌙", "夜行者", "在凌晨 0 點到 4 點之間探索", 1.0) { it.nightWalk.toDouble() },
        Achievement("dawn", "🌅", "拂曉偵察", "在清晨 5 點到 7 點之間探索", 1.0) { it.dawnWalk.toDouble() },

        Achievement("landmark_1", "📍", "世界的第一站", "造訪第 1 個世界地標", 1.0) { it.landmarks.toDouble() },
        Achievement("landmark_5", "🎒", "背包客", "造訪 5 個世界地標", 5.0) { it.landmarks.toDouble() },
        Achievement("landmark_15", "✈️", "旅人", "造訪 15 個世界地標", 15.0) { it.landmarks.toDouble() },
        Achievement("landmark_30", "🏛️", "世界收藏家", "造訪 30 個世界地標", 30.0) { it.landmarks.toDouble() },
        Achievement("continent_2", "🌏", "跨洲旅人", "在 2 個大洲留下足跡", 2.0) { it.continents.toDouble() },
        Achievement("continent_4", "🌎", "四海為家", "在 4 個大洲留下足跡", 4.0) { it.continents.toDouble() },
        Achievement("continent_6", "🌍", "六大洲征服者", "在 6 個大洲留下足跡", 6.0) { it.continents.toDouble() },

        Achievement("alt_1000", "⛰️", "高地偵察", "在海拔 1000 公尺以上探索", 1_000.0) { it.maxAltitude },
        Achievement("alt_2500", "🏔️", "雲上行者", "在海拔 2500 公尺以上探索", 2_500.0) { it.maxAltitude },

        Achievement("cells_1000", "🌁", "拾光獵人", "點亮 1000 個光格", 1_000.0) { it.cells.toDouble() },
        Achievement("cells_10000", "👑", "長夜終結者", "點亮 10000 個光格", 10_000.0) { it.cells.toDouble() },
    )

    val COUNT = ALL.size

    /** 這次新達成、尚未記錄的成就 */
    fun evaluate(stats: Stats, unlocked: Set<String>): List<Achievement> =
        ALL.filter { it.id !in unlocked && it.value(stats) >= it.target }
}

data class Rank(val icon: String, val name: String, val at: Double)

object Ranks {
    val ALL = listOf(
        Rank("🕯️", "提燈人", 0.0),
        Rank("🔦", "尋光者", 0.25),
        Rank("🪔", "拾光者", 1.0),
        Rank("🧭", "巡路人", 5.0),
        Rank("🗺️", "繪光師", 15.0),
        Rank("⚡", "破夜者", 40.0),
        Rank("🌅", "曦光使者", 100.0),
        Rank("👑", "白晝之主", 250.0),
    )

    data class Progress(val rank: Rank, val level: Int, val next: Rank?, val fraction: Float)

    fun of(areaKm2: Double): Progress {
        var idx = 0
        ALL.forEachIndexed { i, r -> if (areaKm2 >= r.at) idx = i }
        val cur = ALL[idx]
        val next = ALL.getOrNull(idx + 1)
        val frac = if (next == null) 1f
        else ((areaKm2 - cur.at) / (next.at - cur.at)).toFloat().coerceIn(0f, 1f)
        return Progress(cur, idx + 1, next, frac)
    }
}
