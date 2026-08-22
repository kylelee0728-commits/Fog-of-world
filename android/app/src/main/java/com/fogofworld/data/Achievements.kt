package com.fogofworld.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.fogofworld.R

/**
 * 成就：以「目前數值 / 目標數值」判定，達標即解鎖。
 *
 * id 是存進存檔的鍵，永遠不能改；顯示文字一律走字串資源，才能跟著語言切換。
 */
data class Achievement(
    val id: String,
    @DrawableRes val icon: Int,
    @StringRes val nameRes: Int,
    @StringRes val descRes: Int,
    val target: Double,
    val value: (Stats) -> Double,
)

object Achievements {

    val ALL: List<Achievement> = listOf(
        Achievement("first_step", R.drawable.ic_ach_first, R.string.ach_first_step_name, R.string.ach_first_step_desc, 1.0) { it.cells.toDouble() },

        Achievement("dist_1", R.drawable.ic_ach_distance, R.string.ach_dist_1_name, R.string.ach_dist_1_desc, 1_000.0) { it.distanceM },
        Achievement("dist_5", R.drawable.ic_walk, R.string.ach_dist_5_name, R.string.ach_dist_5_desc, 5_000.0) { it.distanceM },
        Achievement("dist_10", R.drawable.ic_ach_distance, R.string.ach_dist_10_name, R.string.ach_dist_10_desc, 10_000.0) { it.distanceM },
        Achievement("dist_42", R.drawable.ic_award, R.string.ach_dist_42_name, R.string.ach_dist_42_desc, 42_195.0) { it.distanceM },
        Achievement("dist_100", R.drawable.ic_ach_distance, R.string.ach_dist_100_name, R.string.ach_dist_100_desc, 100_000.0) { it.distanceM },
        Achievement("dist_500", R.drawable.ic_rank_compass, R.string.ach_dist_500_name, R.string.ach_dist_500_desc, 500_000.0) { it.distanceM },
        Achievement("dist_1000", R.drawable.ic_globe, R.string.ach_dist_1000_name, R.string.ach_dist_1000_desc, 1_000_000.0) { it.distanceM },

        Achievement("area_01", R.drawable.ic_ach_area, R.string.ach_area_01_name, R.string.ach_area_01_desc, 0.1) { it.areaKm2 },
        Achievement("area_1", R.drawable.ic_rank_map, R.string.ach_area_1_name, R.string.ach_area_1_desc, 1.0) { it.areaKm2 },
        Achievement("area_10", R.drawable.ic_lm_city, R.string.ach_area_10_name, R.string.ach_area_10_desc, 10.0) { it.areaKm2 },
        Achievement("area_50", R.drawable.ic_ach_area, R.string.ach_area_50_name, R.string.ach_area_50_desc, 50.0) { it.areaKm2 },
        Achievement("area_100", R.drawable.ic_globe, R.string.ach_area_100_name, R.string.ach_area_100_desc, 100.0) { it.areaKm2 },

        Achievement("streak_3", R.drawable.ic_ach_streak, R.string.ach_streak_3_name, R.string.ach_streak_3_desc, 3.0) { it.streak.toDouble() },
        Achievement("streak_7", R.drawable.ic_flame, R.string.ach_streak_7_name, R.string.ach_streak_7_desc, 7.0) { it.streak.toDouble() },
        Achievement("streak_30", R.drawable.ic_ach_streak, R.string.ach_streak_30_name, R.string.ach_streak_30_desc, 30.0) { it.streak.toDouble() },
        Achievement("days_50", R.drawable.ic_ach_time, R.string.ach_days_50_name, R.string.ach_days_50_desc, 50.0) { it.activeDays.toDouble() },
        Achievement("day_5k", R.drawable.ic_rank_bolt, R.string.ach_day_5k_name, R.string.ach_day_5k_desc, 5_000.0) { it.bestDayM },
        Achievement("session_5k", R.drawable.ic_target, R.string.ach_session_5k_name, R.string.ach_session_5k_desc, 5_000.0) { it.bestSessionM },

        Achievement("night", R.drawable.ic_rank_lamp, R.string.ach_night_name, R.string.ach_night_desc, 1.0) { it.nightWalk.toDouble() },
        Achievement("dawn", R.drawable.ic_rank_sunrise, R.string.ach_dawn_name, R.string.ach_dawn_desc, 1.0) { it.dawnWalk.toDouble() },

        Achievement("landmark_1", R.drawable.ic_ach_landmark, R.string.ach_landmark_1_name, R.string.ach_landmark_1_desc, 1.0) { it.landmarks.toDouble() },
        Achievement("landmark_5", R.drawable.ic_passport, R.string.ach_landmark_5_name, R.string.ach_landmark_5_desc, 5.0) { it.landmarks.toDouble() },
        Achievement("landmark_15", R.drawable.ic_ach_landmark, R.string.ach_landmark_15_name, R.string.ach_landmark_15_desc, 15.0) { it.landmarks.toDouble() },
        Achievement("landmark_30", R.drawable.ic_lm_temple, R.string.ach_landmark_30_name, R.string.ach_landmark_30_desc, 30.0) { it.landmarks.toDouble() },
        Achievement("continent_2", R.drawable.ic_globe, R.string.ach_continent_2_name, R.string.ach_continent_2_desc, 2.0) { it.continents.toDouble() },
        Achievement("continent_4", R.drawable.ic_globe, R.string.ach_continent_4_name, R.string.ach_continent_4_desc, 4.0) { it.continents.toDouble() },
        Achievement("continent_6", R.drawable.ic_globe, R.string.ach_continent_6_name, R.string.ach_continent_6_desc, 6.0) { it.continents.toDouble() },

        Achievement("alt_1000", R.drawable.ic_ach_altitude, R.string.ach_alt_1000_name, R.string.ach_alt_1000_desc, 1_000.0) { it.maxAltitude },
        Achievement("alt_2500", R.drawable.ic_lm_mountain, R.string.ach_alt_2500_name, R.string.ach_alt_2500_desc, 2_500.0) { it.maxAltitude },

        Achievement("cells_1000", R.drawable.ic_ach_cells, R.string.ach_cells_1000_name, R.string.ach_cells_1000_desc, 1_000.0) { it.cells.toDouble() },
        Achievement("cells_10000", R.drawable.ic_rank_crown, R.string.ach_cells_10000_name, R.string.ach_cells_10000_desc, 10_000.0) { it.cells.toDouble() },
    )

    val COUNT = ALL.size

    /** 這次新達成、尚未記錄的成就 */
    fun evaluate(stats: Stats, unlocked: Set<String>): List<Achievement> =
        ALL.filter { it.id !in unlocked && it.value(stats) >= it.target }
}

data class Rank(@DrawableRes val icon: Int, @StringRes val nameRes: Int, val at: Double)

object Ranks {
    val ALL = listOf(
        Rank(R.drawable.ic_rank_candle, R.string.rank_1, 0.0),
        Rank(R.drawable.ic_rank_beam, R.string.rank_2, 0.25),
        Rank(R.drawable.ic_rank_lamp, R.string.rank_3, 1.0),
        Rank(R.drawable.ic_rank_compass, R.string.rank_4, 5.0),
        Rank(R.drawable.ic_rank_map, R.string.rank_5, 15.0),
        Rank(R.drawable.ic_rank_bolt, R.string.rank_6, 40.0),
        Rank(R.drawable.ic_rank_sunrise, R.string.rank_7, 100.0),
        Rank(R.drawable.ic_rank_crown, R.string.rank_8, 250.0),
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
