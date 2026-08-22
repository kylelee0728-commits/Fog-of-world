package com.fogofworld.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fogofworld.MainActivity
import com.fogofworld.R
import com.fogofworld.data.Fix
import com.fogofworld.data.Format
import com.fogofworld.data.LocaleHelper
import com.fogofworld.data.FogStore
import com.fogofworld.data.Ranks
import com.fogofworld.data.Grid
import com.fogofworld.data.Landmark
import com.fogofworld.data.Landmarks
import com.fogofworld.data.Settings

/**
 * 前景服務：螢幕關掉、App 切到背景時仍然持續記錄足跡。
 * 這是原生版相對於網頁版最主要的差別。
 *
 * 用系統的 LocationManager，不依賴 Google Play 服務，任何 Android 裝置都能跑。
 */
class LocationService : Service(), LocationListener {

    companion object {
        const val ACTION_START = "com.fogofworld.START"
        const val ACTION_STOP = "com.fogofworld.STOP"
        private const val CHANNEL_ID = "walking"
        private const val NOTIFICATION_ID = 1001

        /** 走進這個距離內就提醒還沒蓋章的地標（蓋章半徑是 1 公里） */
        private const val NEARBY_ALERT_M = 2_000.0
        /** 每次最多檢查一輪，太頻繁沒有意義也費電 */
        private const val NEARBY_CHECK_MS = 20_000L

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LocationService::class.java).setAction(ACTION_STOP))
        }
    }

    private lateinit var locationManager: LocationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotified = 0L
    private var lastNearbyCheck = 0L
    /** 這次記錄期間已經提醒過的地標，停止記錄時清空 */
    private val nearbyNotified = HashSet<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    /** 通知文字也要跟著 App 內的語言設定 */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannel()
        FogStore.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            else -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (isRunning) return
        startForegroundWithType(buildNotification())

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        val intervalMs = Settings.intervalSec(this).coerceIn(1, 60) * 1000L
        FogStore.startSession()

        // GPS 為主；有些室內／隧道情境只有網路定位可用，一併訂閱由精度門檻過濾
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, intervalMs, 0f, this, Looper.getMainLooper()
            )
        }
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, intervalMs, 0f, this, Looper.getMainLooper()
            )
        }

        // 螢幕關閉後仍需持續拿到定位回呼
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fogofworld:tracking")
            .apply { setReferenceCounted(false); acquire(12 * 60 * 60 * 1000L) }

        isRunning = true
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopTracking() {
        if (isRunning) {
            runCatching { locationManager.removeUpdates(this) }
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            isRunning = false
        }
        nearbyNotified.clear()
        FogStore.flush(force = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(this) }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        isRunning = false
        FogStore.flush(force = true)
        super.onDestroy()
    }

    // ── 定位回呼 ────────────────────────────────────────

    override fun onLocationChanged(location: Location) {
        val limit = Settings.accuracyLimit(this)
        if (location.hasAccuracy() && location.accuracy > limit) return

        FogStore.addPosition(
            Fix(
                lat = location.latitude,
                lng = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else 0.0,
                accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
                time = if (location.time > 0) location.time else System.currentTimeMillis(),
            )
        )

        checkDailyGoal()
        checkNearbyLandmark()

        val now = System.currentTimeMillis()
        if (now - lastNotified > 10_000) {
            lastNotified = now
            notify(buildNotification())
        }
        if (now % 30_000 < 3_000) FogStore.flush()
    }

    /**
     * 走近還沒蓋章的地標時提醒一次。
     *
     * 同一個地標在這次記錄期間只提醒一次 —— 站在原地不動也不會一直跳通知；
     * 重新開始探索才會重置。
     */
    private fun checkNearbyLandmark() {
        if (!Settings.nearbyAlert(this)) return
        val now = System.currentTimeMillis()
        if (now - lastNearbyCheck < NEARBY_CHECK_MS) return
        lastNearbyCheck = now

        val fix = FogStore.lastFix ?: return
        val visited = FogStore.visitedLandmarkIds()
        var best: Landmark? = null
        var bestDist = NEARBY_ALERT_M
        for (lm in Landmarks.all(this)) {
            if (lm.id in visited || lm.id in nearbyNotified) continue
            val d = Grid.distanceM(fix.lat, fix.lng, lm.lat, lm.lng)
            if (d < bestDist) { best = lm; bestDist = d }
        }
        val lm = best ?: return
        nearbyNotified.add(lm.id)
        notifyNearby(lm, bestDist, Grid.bearing(fix.lat, fix.lng, lm.lat, lm.lng))
    }

    private fun notifyNearby(landmark: Landmark, distance: Double, bearing: Double) {
        if (!canPostNotifications()) return
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fog_notification)
            .setContentTitle(getString(R.string.nearby_title))
            .setContentText(
                getString(
                    R.string.nearby_sub,
                    Format.landmarkName(this, landmark),
                    Format.compass(this, bearing),
                    Format.distance(this, distance),
                )
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1003, n)
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** 走到當日目標時提醒一次，同一天不重複 */
    private fun checkDailyGoal() {
        val goal = Settings.dailyGoal(this)
        if (goal <= 0) return
        val today = FogStore.todayKey()
        if (Settings.goalNotifiedDay(this) == today) return
        if (FogStore.todayDistance() < goal) return
        Settings.setGoalNotifiedDay(this, today)
        notifyGoalReached()
    }

    private fun notifyGoalReached() {
        if (!canPostNotifications()) return
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fog_notification)
            .setContentTitle(getString(R.string.goal_reached))
            .setContentText(
                getString(R.string.goal_reached_sub, Format.distance(this, FogStore.todayDistance()))
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1002, n)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    // ── 通知 ────────────────────────────────────────────

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_walking),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.channel_walking_desc)
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stats = FogStore.stats.value
        val rank = Ranks.of(stats.areaKm2)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, LocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val goal = Settings.dailyGoal(this)
        val today = FogStore.todayDistance()
        val text = buildString {
            append(
                getString(
                    R.string.notif_text,
                    Format.distance(this@LocationService, stats.distanceM),
                    Format.area(this@LocationService, stats.areaKm2),
                )
            )
            if (goal > 0) {
                val pct = ((today / goal) * 100).toInt().coerceIn(0, 999)
                append(" · ")
                append(getString(R.string.notif_goal, pct))
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fog_notification)
            .setContentTitle(getString(R.string.notif_title, getString(rank.rank.nameRes)))
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun notify(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        }
    }
}
