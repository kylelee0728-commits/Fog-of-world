package com.fogofworld.data

import androidx.annotation.DrawableRes
import com.fogofworld.R

/**
 * 全 App 的圖示都是自己畫的向量圖（res/drawable/ic_*.xml），不使用表情符號 ——
 * 表情符號在不同廠商的系統上長相差很多，也沒辦法跟著主題上色。
 *
 * 地標資料 landmarks.json 與網頁版共用，裡面存的還是表情符號，
 * 所以這裡把它歸成八類，對應到八個手繪圖示。
 */
object Icons {

    /** 找不到對應時的預設地標圖示 */
    @DrawableRes
    val LANDMARK_FALLBACK = R.drawable.ic_ach_landmark

    private val LANDMARK_BY_EMOJI: Map<String, Int> = buildMap {
        fun bind(icon: Int, vararg emojis: String) = emojis.forEach { this[it] = icon }

        bind(R.drawable.ic_lm_city, "🏙️", "🌆", "🌃", "🏢", "🌉", "🚂", "🚗", "🚲", "🚠", "🎰", "🎬", "🎭", "🖼️", "🏟️", "🏮")
        bind(R.drawable.ic_lm_mountain, "🏔️", "⛰️", "🗻", "🌋", "🪨")
        bind(R.drawable.ic_lm_water, "🌊", "💦", "⛵", "🚤", "♨️", "🐠", "🐢")
        bind(R.drawable.ic_lm_temple, "🏛️", "⛪", "🕌", "🛕", "⛩️", "🕍", "🏯", "🏰", "🪔")
        bind(R.drawable.ic_lm_monument, "🗼", "🗿", "🗽", "🔺", "🧱", "🕰️", "🧭")
        bind(R.drawable.ic_lm_nature, "🏞️", "🌳", "🌴", "🌺", "🦁", "🐪", "🏜️", "🧂", "🌅", "🌞", "💨", "🕊️", "🎈")
        bind(R.drawable.ic_lm_island, "🏝️", "🏖️", "🏡")
        bind(R.drawable.ic_lm_polar, "🧊", "🐧", "🐻‍❄️")
    }

    /** 地標的表情符號 → 手繪分類圖示 */
    @DrawableRes
    fun forLandmark(emoji: String): Int = LANDMARK_BY_EMOJI[emoji] ?: LANDMARK_FALLBACK
}
