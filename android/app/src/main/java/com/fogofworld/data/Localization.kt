package com.fogofworld.data

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.fogofworld.R
import java.util.Locale
import kotlin.math.roundToInt

/** 支援的語言。tag 空字串代表跟隨系統。 */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int, val label: String) {
    SYSTEM("", R.string.lang_system, "System"),
    ZH_TW("zh-TW", R.string.lang_system, "繁體中文"),
    EN("en", R.string.lang_system, "English"),
    JA("ja", R.string.lang_system, "日本語"),
    ZH_CN("zh-Hans", R.string.lang_system, "简体中文");

    companion object {
        fun fromTag(tag: String): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

enum class UnitSystem { METRIC, IMPERIAL }

/**
 * 語言覆寫：不改系統語言也能在 App 內切換。
 * 作法是在 attachBaseContext 用指定 Locale 包一層 Context，Activity 與 Service 都要包。
 */
object LocaleHelper {

    fun wrap(base: Context): Context {
        val tag = Settings.languageTag(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}

/** 距離、面積、海拔的顯示，會跟著語言與單位設定 */
object Format {

    private const val M_PER_MILE = 1609.344
    private const val M_PER_FOOT = 0.3048
    private const val KM2_PER_MI2 = 2.589988

    private fun units(context: Context): UnitSystem =
        if (Settings.imperial(context)) UnitSystem.IMPERIAL else UnitSystem.METRIC

    private fun num(value: Double, decimals: Int): String =
        String.format(Locale.getDefault(), "%.${decimals}f", value)

    fun distance(context: Context, meters: Double): String = when (units(context)) {
        UnitSystem.METRIC ->
            if (meters < 1000) context.getString(R.string.dist_m, meters.roundToInt().toString())
            else context.getString(R.string.dist_km, num(meters / 1000, if (meters < 10_000) 2 else 1))

        UnitSystem.IMPERIAL -> {
            val miles = meters / M_PER_MILE
            if (miles < 0.1) context.getString(R.string.dist_ft, (meters / M_PER_FOOT).roundToInt().toString())
            else context.getString(R.string.dist_mi, num(miles, if (miles < 10) 2 else 1))
        }
    }

    fun area(context: Context, km2: Double): String = when (units(context)) {
        UnitSystem.METRIC -> context.getString(R.string.area_km2, num(km2, if (km2 < 10) 2 else 1))
        UnitSystem.IMPERIAL -> {
            val mi2 = km2 / KM2_PER_MI2
            context.getString(R.string.area_mi2, num(mi2, if (mi2 < 10) 2 else 1))
        }
    }

    /** 狀態列數字用：只要數值，單位另外標 */
    fun distanceValue(context: Context, meters: Double): String = when (units(context)) {
        UnitSystem.METRIC -> num(meters / 1000, 2)
        UnitSystem.IMPERIAL -> num(meters / M_PER_MILE, 2)
    }

    fun areaValue(context: Context, km2: Double): String = when (units(context)) {
        UnitSystem.METRIC -> num(km2, if (km2 < 10) 2 else 1)
        UnitSystem.IMPERIAL -> num(km2 / KM2_PER_MI2, if (km2 / KM2_PER_MI2 < 10) 2 else 1)
    }

    fun distanceUnit(context: Context): String =
        if (units(context) == UnitSystem.METRIC) "km" else "mi"

    fun areaUnit(context: Context): String =
        if (units(context) == UnitSystem.METRIC) "km²" else "mi²"

    fun altitude(context: Context, meters: Double): String = when (units(context)) {
        UnitSystem.METRIC -> context.getString(R.string.alt_m, meters.roundToInt().toString())
        UnitSystem.IMPERIAL -> context.getString(R.string.alt_ft, (meters / M_PER_FOOT).roundToInt().toString())
    }

    /** 方位：把角度轉成當地語言的八方位 */
    fun compass(context: Context, deg: Double): String {
        val res = intArrayOf(
            R.string.compass_n, R.string.compass_ne, R.string.compass_e, R.string.compass_se,
            R.string.compass_s, R.string.compass_sw, R.string.compass_w, R.string.compass_nw,
        )
        return context.getString(res[(((deg / 45).roundToInt()) % 8 + 8) % 8])
    }

    /** 大洲名稱：資料檔存的是中文，顯示時轉成當地語言 */
    fun continent(context: Context, name: String): String {
        val res = when (name) {
            "亞洲" -> R.string.cont_asia
            "歐洲" -> R.string.cont_europe
            "非洲" -> R.string.cont_africa
            "北美洲" -> R.string.cont_north_america
            "南美洲" -> R.string.cont_south_america
            "大洋洲" -> R.string.cont_oceania
            "南極洲" -> R.string.cont_antarctica
            else -> return name
        }
        return context.getString(res)
    }

    /** 地標名稱：中文語系用中文名，其他語系用英文名 */
    fun landmarkName(context: Context, landmark: Landmark): String {
        val lang = Locale.getDefault().language
        return if (lang == "zh") landmark.zh else landmark.en
    }

    fun landmarkSecondary(context: Context, landmark: Landmark): String {
        val lang = Locale.getDefault().language
        return if (lang == "zh") landmark.en else landmark.zh
    }
}
