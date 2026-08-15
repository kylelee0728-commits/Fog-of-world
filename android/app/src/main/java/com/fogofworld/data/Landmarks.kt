package com.fogofworld.data

import android.content.Context
import org.json.JSONArray

data class Landmark(
    val id: String,
    val zh: String,
    val en: String,
    val lat: Double,
    val lng: Double,
    val country: String,
    val continent: String,
    val icon: String,
)

object Landmarks {

    /** 蓋章半徑（公尺） */
    const val VISIT_RADIUS_M = 1000.0

    val CONTINENTS = listOf("亞洲", "歐洲", "非洲", "北美洲", "南美洲", "大洋洲", "南極洲")

    @Volatile
    private var cache: List<Landmark>? = null

    /** 從 assets/landmarks.json 讀取（與網頁版共用同一份資料） */
    fun all(context: Context): List<Landmark> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val text = context.assets.open("landmarks.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            val list = ArrayList<Landmark>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Landmark(
                        id = o.getString("id"),
                        zh = o.getString("zh"),
                        en = o.getString("en"),
                        lat = o.getDouble("lat"),
                        lng = o.getDouble("lng"),
                        country = o.getString("country"),
                        continent = o.getString("continent"),
                        icon = o.optString("icon", "📍"),
                    )
                )
            }
            cache = list
            return list
        }
    }
}
