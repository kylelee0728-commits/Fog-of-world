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
import com.fogofworld.data.FogStore
import com.fogofworld.location.LocationService
import com.fogofworld.ui.FogScreen
import com.fogofworld.ui.FogTheme

class MainActivity : ComponentActivity() {

    private var walking by mutableStateOf(false)
    private var hasBackground by mutableStateOf(false)
    private var pendingStart = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FogStore.init(this)
        refreshPermissionState()

        setContent {
            FogTheme {
                FogScreen(
                    walking = walking,
                    onToggleWalk = { toggleWalking() },
                    onRequestBackground = { requestBackgroundPermission() },
                    hasBackgroundPermission = hasBackground,
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
}
