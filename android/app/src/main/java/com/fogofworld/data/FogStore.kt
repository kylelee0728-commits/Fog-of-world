package com.fogofworld.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Stats(
    val distanceM: Double = 0.0,
    val areaKm2: Double = 0.0,
    val cells: Int = 0,
    val landmarks: Int = 0,
    val continents: Int = 0,
    val streak: Int = 0,
    val activeDays: Int = 0,
    val bestDayM: Double = 0.0,
    val bestSessionM: Double = 0.0,
    val maxAltitude: Double = 0.0,
    val nightWalk: Int = 0,
    val dawnWalk: Int = 0,
    val achievementCount: Int = 0,
)

data class Fix(
    val lat: Double,
    val lng: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val time: Long = System.currentTimeMillis(),
)

/** 一個索引格內的所有已探索座標，用平行陣列避免每格都裝箱 */
class Bucket {
    var lats = DoubleArray(32)
    var lngs = DoubleArray(32)
    var size = 0
        private set

    fun add(lat: Double, lng: Double) {
        if (size == lats.size) {
            lats = lats.copyOf(size * 2)
            lngs = lngs.copyOf(size * 2)
        }
        lats[size] = lat
        lngs[size] = lng
        size++
    }
}

/**
 * 足跡與進度的唯一真實來源。所有變更都在主執行緒發生（定位回呼與繪製同一條執行緒），
 * 寫檔則先快照再丟到 IO 執行緒，避免邊寫邊改。
 */
object FogStore {

    private const val MIN_STEP_M = 3.0      // 小於此距離視為 GPS 抖動
    private const val MAX_STEP_M = 300.0    // 大於此距離視為訊號跳點
    private const val MAX_INTERP = 300      // 兩點間最多補幾個中繼格

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dir: File
    private lateinit var appContext: Context

    val cells = HashSet<Long>()
    val blocks = HashSet<Long>()
    val index = HashMap<Long, Bucket>()

    var distanceM = 0.0; private set
    var maxAltitude = 0.0; private set
    private var nightWalk = 0
    private var dawnWalk = 0
    private var bestSessionM = 0.0
    private var sessionM = 0.0
    private val days = HashMap<String, Double>()
    private val visitedLandmarks = HashMap<String, Long>()
    private val unlockedAchievements = HashMap<String, Long>()

    var lastFix: Fix? = null; private set
    private var anchor: Fix? = null

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    /** 撥霧半徑，由設定注入 */
    @Volatile
    var revealRadius: Double = 60.0

    private var dirty = false
    private var loaded = false

    sealed interface Event {
        data class Unlocked(val achievements: List<Achievement>) : Event
        data class Stamped(val landmarks: List<Landmark>) : Event
        data object FogChanged : Event
    }

    // ── 生命週期 ────────────────────────────────────────

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        appContext = context.applicationContext
        dir = File(appContext.filesDir, "fog").apply { mkdirs() }
        revealRadius = Settings.revealRadius(appContext).toDouble()
        loadCells(File(dir, "cells.bin"), cells) { k ->
            index.getOrPut(bucketOf(k)) { Bucket() }
                .add(Grid.cellLat(k, Grid.CELL_M), Grid.cellLng(k, Grid.CELL_M))
        }
        loadCells(File(dir, "blocks.bin"), blocks) { }
        loadMeta()
        loaded = true
        publish()
    }

    private fun bucketOf(cellKey: Long): Long {
        val lat = Grid.cellLat(cellKey, Grid.CELL_M)
        val lng = Grid.cellLng(cellKey, Grid.CELL_M)
        return Grid.cellOf(lat, lng, Grid.BUCKET_M)
    }

    // ── 記錄一個定位點 ──────────────────────────────────

    fun addPosition(fix: Fix): Boolean {
        if (!loaded) return false
        val prev = anchor
        var moved = 0.0
        var fogChanged = false

        // 1. 撥霧：這個點，以及與上一點之間的路徑
        if (prev != null) {
            moved = Grid.distanceM(prev.lat, prev.lng, fix.lat, fix.lng)
            if (moved > Grid.CELL_M && moved <= MAX_STEP_M) {
                val steps = minOf(MAX_INTERP, Math.ceil(moved / (Grid.CELL_M * 0.7)).toInt())
                for (s in 1 until steps) {
                    val t = s.toDouble() / steps
                    if (mark(prev.lat + (fix.lat - prev.lat) * t, prev.lng + (fix.lng - prev.lng) * t)) {
                        fogChanged = true
                    }
                }
            }
        }
        if (mark(fix.lat, fix.lng)) fogChanged = true

        // 2. 里程
        //    位移小於雜訊門檻時不更新錨點，讓慢速步行的位移累積到門檻，
        //    否則以走路速度（約 1.4 m/s）每次定位都低於門檻，里程會永遠是 0。
        when {
            prev == null -> {
                days.getOrPut(dayKey(fix.time)) { 0.0 }
                anchor = fix
            }
            moved > MAX_STEP_M -> anchor = fix
            moved >= MIN_STEP_M -> {
                distanceM += moved
                sessionM += moved
                val dk = dayKey(fix.time)
                days[dk] = (days[dk] ?: 0.0) + moved
                if (sessionM > bestSessionM) bestSessionM = sessionM
                anchor = fix
            }
        }
        lastFix = fix

        // 3. 海拔與時段
        if (fix.altitude > maxAltitude) maxAltitude = fix.altitude
        val hour = Calendar.getInstance().apply { timeInMillis = fix.time }.get(Calendar.HOUR_OF_DAY)
        if (hour < 4) nightWalk = 1 else if (hour in 5..6) dawnWalk = 1

        dirty = true

        // 4. 地標
        val stamped = ArrayList<Landmark>()
        for (lm in Landmarks.all(appContext)) {
            if (visitedLandmarks.containsKey(lm.id)) continue
            if (Grid.distanceM(fix.lat, fix.lng, lm.lat, lm.lng) <= Landmarks.VISIT_RADIUS_M) {
                visitedLandmarks[lm.id] = fix.time
                stamped.add(lm)
            }
        }

        // 5. 成就
        val snapshot = computeStats()
        val fresh = Achievements.evaluate(snapshot, unlockedAchievements.keys)
        for (a in fresh) unlockedAchievements[a.id] = fix.time

        publish()
        // 每次定位都通知畫面：不只重畫霧，還要更新玩家位置與跟隨鏡頭
        _events.tryEmit(Event.FogChanged)
        if (stamped.isNotEmpty()) _events.tryEmit(Event.Stamped(stamped))
        if (fresh.isNotEmpty()) _events.tryEmit(Event.Unlocked(fresh))
        return fogChanged
    }

    /** 標記一個座標：細網格畫霧，粗網格算面積 */
    private fun mark(lat: Double, lng: Double): Boolean {
        val k = Grid.cellOf(lat, lng, Grid.CELL_M)
        val isNew = cells.add(k)
        if (isNew) {
            index.getOrPut(Grid.cellOf(lat, lng, Grid.BUCKET_M)) { Bucket() }.add(lat, lng)
        }
        Grid.forEachCellInRadius(lat, lng, revealRadius, Grid.BLOCK_M) { blocks.add(it) }
        return isNew
    }

    fun startSession() {
        sessionM = 0.0
        anchor = null
    }

    // ── 統計 ────────────────────────────────────────────

    private fun computeStats(): Stats {
        val continents = visitedLandmarks.keys
            .mapNotNull { id -> Landmarks.all(appContext).firstOrNull { it.id == id }?.continent }
            .toHashSet()
        return Stats(
            distanceM = distanceM,
            areaKm2 = blocks.size * Grid.BLOCK_AREA_KM2,
            cells = cells.size,
            landmarks = visitedLandmarks.size,
            continents = continents.size,
            streak = currentStreak(),
            activeDays = days.size,
            bestDayM = days.values.maxOrNull() ?: 0.0,
            bestSessionM = bestSessionM,
            maxAltitude = maxAltitude,
            nightWalk = nightWalk,
            dawnWalk = dawnWalk,
            achievementCount = unlockedAchievements.size,
        )
    }

    private fun publish() {
        _stats.value = computeStats()
    }

    fun visitedLandmarkIds(): Map<String, Long> = visitedLandmarks

    fun unlockedAchievementIds(): Map<String, Long> = unlockedAchievements

    private fun currentStreak(): Int {
        if (days.isEmpty()) return 0
        val oneDay = 86_400_000L
        var t = System.currentTimeMillis()
        if (!days.containsKey(dayKey(t))) t -= oneDay
        if (!days.containsKey(dayKey(t))) return 0
        var streak = 0
        while (days.containsKey(dayKey(t))) {
            streak++
            t -= oneDay
        }
        return streak
    }

    private fun dayKey(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ts))

    // ── 存檔 ────────────────────────────────────────────

    /** 快照後丟到 IO 執行緒寫入，避免邊寫邊改 */
    fun flush(force: Boolean = false) {
        if (!loaded || (!dirty && !force)) return
        dirty = false
        val cellSnapshot = cells.toLongArray()
        val blockSnapshot = blocks.toLongArray()
        val meta = metaJson().toString()
        scope.launch {
            writeCells(File(dir, "cells.bin"), cellSnapshot)
            writeCells(File(dir, "blocks.bin"), blockSnapshot)
            writeAtomic(File(dir, "meta.json"), meta.toByteArray())
        }
    }

    fun reset() {
        cells.clear()
        blocks.clear()
        index.clear()
        days.clear()
        visitedLandmarks.clear()
        unlockedAchievements.clear()
        distanceM = 0.0
        maxAltitude = 0.0
        nightWalk = 0
        dawnWalk = 0
        bestSessionM = 0.0
        sessionM = 0.0
        anchor = null
        lastFix = null
        dirty = true
        flush(force = true)
        publish()
        _events.tryEmit(Event.FogChanged)
    }

    private fun metaJson(): JSONObject {
        val o = JSONObject()
        o.put("distanceM", distanceM)
        o.put("maxAltitude", maxAltitude)
        o.put("nightWalk", nightWalk)
        o.put("dawnWalk", dawnWalk)
        o.put("bestSessionM", bestSessionM)
        o.put("days", JSONObject().also { d -> days.forEach { (k, v) -> d.put(k, v) } })
        o.put("landmarks", JSONObject().also { d -> visitedLandmarks.forEach { (k, v) -> d.put(k, v) } })
        o.put("achievements", JSONObject().also { d -> unlockedAchievements.forEach { (k, v) -> d.put(k, v) } })
        lastFix?.let {
            o.put("lastLat", it.lat)
            o.put("lastLng", it.lng)
        }
        return o
    }

    private fun loadMeta() {
        val f = File(dir, "meta.json")
        if (!f.exists()) return
        runCatching {
            val o = JSONObject(f.readText())
            distanceM = o.optDouble("distanceM", 0.0)
            maxAltitude = o.optDouble("maxAltitude", 0.0)
            nightWalk = o.optInt("nightWalk", 0)
            dawnWalk = o.optInt("dawnWalk", 0)
            bestSessionM = o.optDouble("bestSessionM", 0.0)
            o.optJSONObject("days")?.let { d ->
                d.keys().forEach { k -> days[k] = d.optDouble(k, 0.0) }
            }
            o.optJSONObject("landmarks")?.let { d ->
                d.keys().forEach { k -> visitedLandmarks[k] = d.optLong(k) }
            }
            o.optJSONObject("achievements")?.let { d ->
                d.keys().forEach { k -> unlockedAchievements[k] = d.optLong(k) }
            }
            if (o.has("lastLat")) {
                lastFix = Fix(o.getDouble("lastLat"), o.getDouble("lastLng"))
            }
        }
    }

    private fun loadCells(file: File, into: HashSet<Long>, onEach: (Long) -> Unit) {
        if (!file.exists()) return
        runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val count = input.readInt()
                repeat(count) {
                    val k = input.readLong()
                    into.add(k)
                    onEach(k)
                }
            }
        }
    }

    private fun writeCells(file: File, keys: LongArray) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(keys.size)
                for (k in keys) out.writeLong(k)
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }
}
