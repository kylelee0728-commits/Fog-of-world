package com.fogofworld

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fogofworld.data.FogStore
import com.fogofworld.data.Settings
import com.fogofworld.data.UpdateChecker
import com.fogofworld.location.LocationService
import com.fogofworld.ui.FogScreen
import com.fogofworld.ui.FogTheme
import com.fogofworld.ui.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var walking by mutableStateOf(false)
    private var hasBackground by mutableStateOf(false)
    private var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
    private var pendingStart = false
    private var downloadedApk: File? = null

    private val requestForeground = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && pendingStart) startTracking()
        pendingStart = false
        refreshPermissionState()
    }

    private val requestBackground = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissionState() }

    // 備份：交給系統的檔案選擇器，不需要儲存空間權限
    private val exportFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) writeBackup(uri) }

    private val importFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) readBackup(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FogStore.init(this)
        refreshPermissionState()
        maybeAutoCheckUpdate()

        setContent {
            FogTheme {
                FogScreen(
                    walking = walking,
                    hasBackgroundPermission = hasBackground,
                    appVersion = BuildConfig.VERSION_NAME,
                    updateState = updateState,
                    onToggleWalk = { toggleWalking() },
                    onRequestBackground = { requestBackgroundPermission() },
                    onCheckUpdate = { checkUpdate(manual = true) },
                    onInstallUpdate = { installUpdate() },
                    onGrantInstallPermission = { UpdateChecker.openInstallPermissionSettings(this) },
                    onDismissUpdate = { updateState = UpdateState.Idle },
                    onExport = { exportFile.launch(defaultBackupName()) },
                    onImport = { importFile.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        walking = LocationService.isRunning
        refreshPermissionState()
    }

    override fun onPause() {
        super.onPause()
        // 沒在追蹤時也要保住進度（追蹤中由服務負責寫檔）
        if (!LocationService.isRunning) FogStore.flush(force = true)
    }

    // ── 權限 ────────────────────────────────────────────

    private fun refreshPermissionState() {
        hasBackground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            // Android 9 以下沒有背景定位的獨立權限，前景服務即可持續記錄
            true
        }
    }

    private fun hasForegroundLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestForegroundPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        requestForeground.launch(perms.toTypedArray())
    }

    /**
     * 背景定位必須「先拿到前景權限，再單獨要一次」。
     * Android 11 以上系統不再顯示對話框，只能引導使用者到設定頁選「一律允許」。
     */
    private fun requestBackgroundPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!hasForegroundLocation()) {
            pendingStart = false
            requestForegroundPermissions()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                startActivity(
                    Intent(
                        AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null),
                    )
                )
            }
        } else {
            requestBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    // ── 開始／暫停 ──────────────────────────────────────

    private fun toggleWalking() {
        if (LocationService.isRunning) {
            LocationService.stop(this)
            walking = false
        } else if (hasForegroundLocation()) {
            startTracking()
        } else {
            pendingStart = true
            requestForegroundPermissions()
        }
    }

    private fun startTracking() {
        LocationService.start(this)
        walking = true
    }

    // ── 備份 ────────────────────────────────────────────

    private fun defaultBackupName(): String {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "fog-of-world-$day.json"
    }

    private fun writeBackup(uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = FogStore.exportJson()
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    true
                }.getOrDefault(false)
            }
            updateState = UpdateState.Message(
                if (ok) "存檔已匯出。這個檔案網頁版也能匯入。" else "匯出失敗，請換個位置再試一次。"
            )
        }
    }

    private fun readBackup(uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: return@runCatching false
                    FogStore.importJson(text)
                }.getOrDefault(false)
            }
            updateState = UpdateState.Message(
                if (ok) "存檔已匯入，地圖已經照新的紀錄重畫。" else "匯入失敗：檔案格式看起來不是拾光者的存檔。"
            )
        }
    }

    // ── 更新 ────────────────────────────────────────────

    private fun maybeAutoCheckUpdate() {
        if (!Settings.autoUpdate(this)) return
        val since = System.currentTimeMillis() - Settings.lastUpdateCheck(this)
        if (since < 12 * 60 * 60 * 1000L) return
        checkUpdate(manual = false)
    }

    private fun checkUpdate(manual: Boolean) {
        if (manual) updateState = UpdateState.Checking
        lifecycleScope.launch {
            Settings.setLastUpdateCheck(this@MainActivity, System.currentTimeMillis())
            when (val result = UpdateChecker.check(BuildConfig.VERSION_NAME)) {
                is UpdateChecker.Result.Available -> updateState = UpdateState.Found(result.release)
                is UpdateChecker.Result.UpToDate ->
                    updateState = if (manual) UpdateState.Message("已經是最新版本 ${BuildConfig.VERSION_NAME}") else UpdateState.Idle
                is UpdateChecker.Result.Failed ->
                    updateState = if (manual) UpdateState.Message("檢查更新失敗：${result.reason}") else UpdateState.Idle
            }
        }
    }

    private fun installUpdate() {
        val release = (updateState as? UpdateState.Found)?.release ?: return
        if (!UpdateChecker.canInstall(this)) {
            updateState = UpdateState.NeedPermission(release)
            return
        }
        updateState = UpdateState.Downloading(0f)
        lifecycleScope.launch {
            val file = UpdateChecker.download(this@MainActivity, release) { p ->
                lifecycleScope.launch { updateState = UpdateState.Downloading(p) }
            }
            if (file == null) {
                updateState = UpdateState.Message("下載失敗，請確認網路後再試一次。")
                return@launch
            }
            downloadedApk = file
            // 安裝前先把進度寫進檔案，避免安裝過程中遺失
            FogStore.flush(force = true)
            if (!UpdateChecker.install(this@MainActivity, file)) {
                updateState = UpdateState.Message("叫不出安裝畫面，可以到設定手動安裝下載好的檔案。")
            } else {
                updateState = UpdateState.Idle
            }
        }
    }
}
