package com.fogofworld.data

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 地理與網格運算。格子代號打包成 Long（高 32 位是緯度格號，低 32 位是經度格號）。 */
object Grid {

    const val M_PER_DEG_LAT = 111320.0

    /** 細網格：撥霧軌跡的解析度（公尺） */
    const val CELL_M = 20.0

    /** 粗網格：統計已撥霧面積的單位（公尺） */
    const val BLOCK_M = 50.0

    /** 空間索引的格子大小（公尺） */
    const val BUCKET_M = 1280.0

    val BLOCK_AREA_KM2 = (BLOCK_M * BLOCK_M) / 1_000_000.0

    fun key(i: Int, j: Int): Long = (i.toLong() shl 32) or (j.toLong() and 0xFFFFFFFFL)

    fun keyI(k: Long): Int = (k shr 32).toInt()

    fun keyJ(k: Long): Int = k.toInt()

    /** 經度縮放係數，靠近極區時夾住避免除以 0 */
    fun lngScale(lat: Double): Double = max(cos(Math.toRadians(lat)), 0.05)

    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_008.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * asin(min(1.0, sqrt(a)))
    }

    fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lng2 - lng1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    private val COMPASS = arrayOf("北", "東北", "東", "東南", "南", "西南", "西", "西北")

    fun compass(deg: Double): String = COMPASS[(((deg / 45).roundToInt()) % 8 + 8) % 8]

    /**
     * 把座標量化成格子。緯度方向固定步長，經度方向依該格緯度補償，
     * 因此格子在任何緯度都接近正方形。
     */
    fun cellOf(lat: Double, lng: Double, sizeM: Double): Long {
        val stepLat = sizeM / M_PER_DEG_LAT
        val i = Math.round(lat / stepLat).toInt()
        val cellLat = i * stepLat
        val stepLng = sizeM / (M_PER_DEG_LAT * lngScale(cellLat))
        val j = Math.round(lng / stepLng).toInt()
        return key(i, j)
    }

    fun cellLat(k: Long, sizeM: Double): Double = keyI(k) * (sizeM / M_PER_DEG_LAT)

    fun cellLng(k: Long, sizeM: Double): Double {
        val lat = cellLat(k, sizeM)
        return keyJ(k) * (sizeM / (M_PER_DEG_LAT * lngScale(lat)))
    }

    /** 以某點為圓心、半徑內的所有格子（用於計算已撥霧面積） */
    inline fun forEachCellInRadius(
        lat: Double,
        lng: Double,
        radiusM: Double,
        sizeM: Double,
        action: (Long) -> Unit,
    ) {
        val stepLat = sizeM / M_PER_DEG_LAT
        val span = Math.ceil(radiusM / sizeM).toInt()
        val i0 = Math.round(lat / stepLat).toInt()
        for (di in -span..span) {
            val i = i0 + di
            val cLat = i * stepLat
            val stepLng = sizeM / (M_PER_DEG_LAT * lngScale(cLat))
            val j0 = Math.round(lng / stepLng).toInt()
            for (dj in -span..span) {
                val j = j0 + dj
                if (distanceM(lat, lng, cLat, j * stepLng) <= radiusM) action(key(i, j))
            }
        }
    }

    /** 目前視角下 1 像素代表幾公尺（用投影實測，不假設圖磚大小） */
    fun metersPerPixel(pixelsPerHundredthDegLng: Double, lat: Double): Double {
        val meters = M_PER_DEG_LAT * lngScale(lat) * 0.01
        return max(0.05, meters / max(1.0, abs(pixelsPerHundredthDegLng)))
    }
}
