package com.fogofworld.data

import android.content.Context
import androidx.core.content.edit

/** 使用者設定（SharedPreferences，不需要額外相依） */
object Settings {

    private const val FILE = "fow_settings"
    private const val KEY_RADIUS = "reveal_radius"
    private const val KEY_OPACITY = "fog_opacity"
    private const val KEY_ACCURACY = "accuracy_limit"
    private const val KEY_FOLLOW = "follow"
    private const val KEY_INTERVAL = "interval_sec"
    private const val KEY_SEEN_INTRO = "seen_intro"
    private const val KEY_AUTO_UPDATE = "auto_update"
    private const val KEY_LAST_CHECK = "last_update_check"
    private const val KEY_LANGUAGE = "language_tag"
    private const val KEY_IMPERIAL = "imperial_units"
    private const val KEY_DAILY_GOAL = "daily_goal_m"
    private const val KEY_GOAL_DAY = "daily_goal_day"
    private const val KEY_NEARBY = "nearby_alert"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun revealRadius(context: Context): Int = prefs(context).getInt(KEY_RADIUS, 60)
    fun setRevealRadius(context: Context, v: Int) = prefs(context).edit { putInt(KEY_RADIUS, v) }

    fun fogOpacity(context: Context): Int = prefs(context).getInt(KEY_OPACITY, 88)
    fun setFogOpacity(context: Context, v: Int) = prefs(context).edit { putInt(KEY_OPACITY, v) }

    fun accuracyLimit(context: Context): Int = prefs(context).getInt(KEY_ACCURACY, 60)
    fun setAccuracyLimit(context: Context, v: Int) = prefs(context).edit { putInt(KEY_ACCURACY, v) }

    fun follow(context: Context): Boolean = prefs(context).getBoolean(KEY_FOLLOW, true)
    fun setFollow(context: Context, v: Boolean) = prefs(context).edit { putBoolean(KEY_FOLLOW, v) }

    /** 定位更新間隔（秒）；越長越省電 */
    fun intervalSec(context: Context): Int = prefs(context).getInt(KEY_INTERVAL, 3)
    fun setIntervalSec(context: Context, v: Int) = prefs(context).edit { putInt(KEY_INTERVAL, v) }

    fun seenIntro(context: Context): Boolean = prefs(context).getBoolean(KEY_SEEN_INTRO, false)
    fun setSeenIntro(context: Context, v: Boolean) = prefs(context).edit { putBoolean(KEY_SEEN_INTRO, v) }

    /** 開啟 App 時自動檢查有沒有新版本 */
    fun autoUpdate(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_UPDATE, true)
    fun setAutoUpdate(context: Context, v: Boolean) = prefs(context).edit { putBoolean(KEY_AUTO_UPDATE, v) }

    fun lastUpdateCheck(context: Context): Long = prefs(context).getLong(KEY_LAST_CHECK, 0L)
    fun setLastUpdateCheck(context: Context, v: Long) = prefs(context).edit { putLong(KEY_LAST_CHECK, v) }

    /** 語言標籤；空字串代表跟隨系統 */
    fun languageTag(context: Context): String = prefs(context).getString(KEY_LANGUAGE, "") ?: ""
    fun setLanguageTag(context: Context, v: String) = prefs(context).edit { putString(KEY_LANGUAGE, v) }

    fun imperial(context: Context): Boolean = prefs(context).getBoolean(KEY_IMPERIAL, false)
    fun setImperial(context: Context, v: Boolean) = prefs(context).edit { putBoolean(KEY_IMPERIAL, v) }

    /** 每日目標（公尺）；0 表示不設目標 */
    fun dailyGoal(context: Context): Int = prefs(context).getInt(KEY_DAILY_GOAL, 0)
    fun setDailyGoal(context: Context, v: Int) = prefs(context).edit { putInt(KEY_DAILY_GOAL, v) }

    /** 走近還沒蓋章的地標時通知 */
    fun nearbyAlert(context: Context): Boolean = prefs(context).getBoolean(KEY_NEARBY, true)
    fun setNearbyAlert(context: Context, v: Boolean) = prefs(context).edit { putBoolean(KEY_NEARBY, v) }

    /** 記住哪一天已經通知過達標，避免同一天重複提示 */
    fun goalNotifiedDay(context: Context): String = prefs(context).getString(KEY_GOAL_DAY, "") ?: ""
    fun setGoalNotifiedDay(context: Context, v: String) = prefs(context).edit { putString(KEY_GOAL_DAY, v) }
}
