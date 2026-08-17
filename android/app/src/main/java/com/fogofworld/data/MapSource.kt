package com.fogofworld.data

import android.content.Context
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource

/**
 * 地圖圖磚來源。
 *
 * OpenStreetMap 公用伺服器的政策是 TileSourcePolicy(1, 15)：同時只允許 1 個下載連線，
 * 並且帶有 FLAG_NO_BULK 與 FLAG_NO_PREVENTIVE —— 也就是禁止批次下載與預先載入。
 * 這是 OSM 的使用政策，osmdroid 直接在程式裡擋掉（CacheManager 會丟例外），
 * 不是可以靠設定繞過的東西，所以預先下載只在自訂來源時開放。
 */
object MapSource {

    /** 自訂來源：允許快取與預先下載，並開放較高的並行數 */
    private val CUSTOM_POLICY = TileSourcePolicy(
        4,
        TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL,
    )

    fun current(context: Context): ITileSource {
        val url = Settings.tileUrl(context).trim()
        if (!isUsableTemplate(url)) return TileSourceFactory.MAPNIK
        val base = url.substringBefore("{z}")
        return XYTileSource(
            "custom",
            0, 19, 256,
            suffixOf(url),
            arrayOf(base),
            "© 地圖來源提供者",
            CUSTOM_POLICY,
        )
    }

    /** 目前來源是否允許預先下載 */
    fun allowsPrefetch(context: Context): Boolean = isUsableTemplate(Settings.tileUrl(context).trim())

    private fun isUsableTemplate(url: String): Boolean =
        url.startsWith("http") && url.contains("{z}") && url.contains("{x}") && url.contains("{y}")

    /**
     * osmdroid 的 XYTileSource 會自己組成 base + z/x/y + suffix，
     * 所以這裡把 {z}/{x}/{y} 之後的部分（副檔名或查詢字串）抽出來當 suffix。
     */
    private fun suffixOf(url: String): String {
        val after = url.substringAfter("{y}", "")
        return if (after.isEmpty()) ".png" else after
    }
}
