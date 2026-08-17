package com.fogofworld.data

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import java.io.File

/**
 * 圖磚快取的調校與預先下載。
 *
 * 能做的事分兩種：
 *  1. 合規的加速 —— 加大記憶體／磁碟快取、延長到期時間，讓走過的地方永遠不用重抓。
 *     這對走路 App 特別有效，因為同一區會反覆走。
 *  2. 預先下載 —— 只有在自訂圖磚來源時才開放，OSM 公用伺服器政策禁止。
 */
object TileCache {

    /** 30 天內不重抓已快取的圖磚 */
    private const val EXPIRY_MS = 30L * 24 * 60 * 60 * 1000

    /** 一次最多下載幾張，避免使用者不小心對整個國家按下去 */
    const val MAX_TILES_PER_JOB = 3000

    fun configure(context: Context, base: File) {
        val conf = Configuration.getInstance()

        // 磁碟快取：預設 600MB，這裡放寬讓常走的區域留得住
        conf.osmdroidBasePath = base
        conf.osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
        conf.tileFileSystemCacheMaxBytes = 1024L * 1024 * 1024
        conf.tileFileSystemCacheTrimBytes = 800L * 1024 * 1024

        // 記憶體快取：多留一些，平移回剛看過的地方就不會再讀檔
        conf.cacheMapTileCount = 24
        conf.tileFileSystemThreads = 8
        conf.tileFileSystemMaxQueueSize = 200

        // 下載執行緒：實際上限仍由圖磚來源的政策決定（OSM 只允許 1 條）
        conf.tileDownloadThreads = 4
        conf.tileDownloadMaxQueueSize = 200

        // 已下載的圖磚在期限內直接沿用，不再回頭問伺服器
        conf.expirationExtendedDuration = EXPIRY_MS
        conf.expirationOverrideDuration = EXPIRY_MS

        // 預先載入視野外的一圈圖磚。來源政策若標示 NO_PREVENTIVE（OSM 就是），
        // osmdroid 會自行忽略，所以這裡設了也不會違規。
        conf.cacheMapTileOvershoot = if (MapSource.allowsPrefetch(context)) 2 else 0
    }

    sealed interface Progress {
        data class Counting(val tiles: Int) : Progress
        data class Running(val done: Int, val total: Int) : Progress
        data class Done(val tiles: Int) : Progress
        data class TooMany(val tiles: Int) : Progress
        data object Failed : Progress
        data object NotAllowed : Progress
    }

    private var job: CacheManager.CacheManagerTask? = null

    /**
     * 下載目前畫面這一區（含往下兩級縮放，走路時多半在放大的層級看）。
     * @param onProgress 一律在主執行緒回呼
     */
    fun downloadVisibleArea(
        context: Context,
        map: MapView,
        onProgress: (Progress) -> Unit,
    ) {
        if (!MapSource.allowsPrefetch(context)) {
            onProgress(Progress.NotAllowed)
            return
        }
        val zoom = map.zoomLevelDouble.toInt()
        val zoomMin = zoom.coerceIn(3, 19)
        val zoomMax = (zoom + 2).coerceAtMost(19)
        val box: BoundingBox = map.boundingBox

        val manager = runCatching { CacheManager(map) }.getOrNull()
        if (manager == null) {
            // 來源政策不允許批次下載時，CacheManager 的建構子會丟例外
            onProgress(Progress.NotAllowed)
            return
        }

        val total = runCatching { manager.possibleTilesInArea(box, zoomMin, zoomMax) }.getOrDefault(0)
        if (total <= 0) {
            onProgress(Progress.Failed)
            return
        }
        if (total > MAX_TILES_PER_JOB) {
            onProgress(Progress.TooMany(total))
            return
        }
        onProgress(Progress.Counting(total))

        job = manager.downloadAreaAsyncNoUI(
            context, box, zoomMin, zoomMax,
            object : CacheManager.CacheManagerCallback {
                override fun onTaskComplete() {
                    job = null
                    onProgress(Progress.Done(total))
                }

                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin2: Int, zoomMax2: Int) {
                    onProgress(Progress.Running(progress, total))
                }

                override fun downloadStarted() = Unit

                override fun setPossibleTilesInArea(total2: Int) = Unit

                override fun onTaskFailed(errors: Int) {
                    job = null
                    onProgress(Progress.Failed)
                }
            },
        )
    }

    fun cancel() {
        runCatching { job?.cancel(true) }
        job = null
    }

    /** 清掉圖磚快取（不會動到足跡資料） */
    fun clear(context: Context) {
        runCatching {
            Configuration.getInstance().osmdroidTileCache.deleteRecursively()
            Configuration.getInstance().osmdroidTileCache.mkdirs()
        }
    }
}
