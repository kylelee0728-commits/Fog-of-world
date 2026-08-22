package com.fogofworld.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.fogofworld.R
import com.fogofworld.data.AppLanguage
import com.fogofworld.data.FogStore
import com.fogofworld.data.Format
import com.fogofworld.data.Landmark
import com.fogofworld.data.Landmarks
import com.fogofworld.data.Ranks
import com.fogofworld.data.Settings

private enum class Sheet { NONE, ACHIEVEMENTS, PASSPORT, SETTINGS, STATS }

data class Toast(val id: Long, @androidx.annotation.DrawableRes val icon: Int, val title: String, val sub: String)

@Composable
fun FogScreen(
    walking: Boolean,
    hasBackgroundPermission: Boolean,
    appVersion: String,
    updateState: UpdateState,
    onLanguage: (AppLanguage) -> Unit,
    onToggleWalk: () -> Unit,
    onRequestBackground: () -> Unit,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onGrantInstallPermission: () -> Unit,
    onDismissUpdate: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    val stats by FogStore.stats.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var showIntro by remember { mutableStateOf(!Settings.seenIntro(context)) }
    // 點開的地標詳情；wishTick 只是為了讓護照在想去清單變動後重新排版
    var detail by remember { mutableStateOf<Landmark?>(null) }
    var wishTick by remember { mutableIntStateOf(0) }
    val toasts = remember { mutableStateListOf<Toast>() }

    var controller by remember { mutableStateOf<MapController?>(null) }
    var follow by remember { mutableStateOf(Settings.follow(context)) }
    var radius by remember { mutableStateOf(Settings.revealRadius(context)) }
    var opacity by remember { mutableStateOf(Settings.fogOpacity(context)) }
    var accuracy by remember { mutableStateOf(Settings.accuracyLimit(context)) }
    var interval by remember { mutableStateOf(Settings.intervalSec(context)) }
    var autoUpdate by remember { mutableStateOf(Settings.autoUpdate(context)) }
    var nearbyAlert by remember { mutableStateOf(Settings.nearbyAlert(context)) }
    var imperial by remember { mutableStateOf(Settings.imperial(context)) }
    var dailyGoal by remember { mutableStateOf(Settings.dailyGoal(context)) }
    val language = AppLanguage.fromTag(Settings.languageTag(context))

    // 事件：撥霧重畫、跟隨鏡頭、成就與蓋章提示
    LaunchedEffect(Unit) {
        FogStore.events.collect { event ->
            when (event) {
                is FogStore.Event.FogChanged -> {
                    controller?.invalidateFog()
                    if (follow) {
                        FogStore.lastFix?.let { fix -> controller?.animateTo(fix.lat, fix.lng) }
                    }
                }

                is FogStore.Event.Unlocked -> {
                    event.achievements.take(2).forEach {
                        toasts.add(
                            Toast(
                                System.nanoTime(), it.icon,
                                context.getString(R.string.toast_achievement, context.getString(it.nameRes)),
                                context.getString(it.descRes),
                            )
                        )
                    }
                    if (event.achievements.size > 2) {
                        toasts.add(
                            Toast(
                                System.nanoTime(), R.drawable.ic_award,
                                context.getString(R.string.toast_achievement_more, event.achievements.size - 2),
                                context.getString(R.string.toast_achievement_more_sub),
                            )
                        )
                    }
                }

                is FogStore.Event.Stamped -> {
                    event.landmarks.take(2).forEach {
                        toasts.add(
                            Toast(
                                System.nanoTime(), it.icon,
                                context.getString(R.string.toast_stamp, Format.landmarkName(context, it)),
                                "${it.country} · ${Format.continent(context, it.continent)}",
                            )
                        )
                    }
                }
            }
        }
    }

    // 提示訊息自動消失
    LaunchedEffect(toasts.size) {
        if (toasts.isNotEmpty()) {
            kotlinx.coroutines.delay(4500)
            if (toasts.isNotEmpty()) toasts.removeAt(0)
        }
    }

    DisposableEffect(Unit) {
        onDispose { FogStore.flush(force = true) }
    }

    Box(Modifier.fillMaxSize().background(FogBg)) {

        // ── 地圖 + 迷霧 ──────────────────────────────
        MapHost(
            modifier = Modifier.fillMaxSize(),
            radiusM = radius.toDouble(),
            opacity = opacity / 100f,
            onReady = { controller = it },
        )

        // ── 頂部狀態 ────────────────────────────────
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xF0060910), Color(0xB0060910), Color(0x00060910))
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            val rank = Ranks.of(stats.areaKm2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(FogAccent.copy(alpha = 0.12f))
                        .border(1.dp, FogAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { FogIcon(rank.rank.icon, size = 20.dp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Lv.${rank.level} " + stringResource(rank.rank.nameRes),
                        color = FogText, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { rank.fraction },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                        color = FogAccent,
                        trackColor = Color.White.copy(alpha = 0.12f),
                        drawStopIndicator = {},
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable { sheet = Sheet.STATS },
            ) {
                StatChip(
                    Modifier.weight(1f),
                    Format.distanceValue(context, stats.distanceM),
                    Format.distanceUnit(context) + " " + stringResource(R.string.chip_distance),
                )
                StatChip(
                    Modifier.weight(1f),
                    Format.areaValue(context, stats.areaKm2),
                    Format.areaUnit(context) + " " + stringResource(R.string.chip_area),
                )
                StatChip(Modifier.weight(1f), "${stats.landmarks}", stringResource(R.string.chip_landmarks))
            }
            if (walking && !hasBackgroundPermission) {
                Spacer(Modifier.height(8.dp))
                BackgroundHint(onRequestBackground)
            }
        }

        // ── 提示訊息 ────────────────────────────────
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 150.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            toasts.takeLast(4).forEach { t ->
                AnimatedVisibility(true, enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        color = Color(0xFF3A2D14),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FogAccent.copy(alpha = 0.55f)),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FogIcon(t.icon, size = 24.dp)
                            Spacer(Modifier.width(11.dp))
                            Column {
                                Text(t.title, color = FogText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                if (t.sub.isNotEmpty()) {
                                    Text(t.sub, color = FogMuted, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 底部工具列 ──────────────────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(10.dp)
                .fillMaxWidth(),
            color = FogPanel.copy(alpha = 0.94f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, FogLine),
        ) {
            Row(
                Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DockButton(Modifier.weight(1f), R.drawable.ic_award, stringResource(R.string.dock_achievements)) { sheet = Sheet.ACHIEVEMENTS }
                DockButton(Modifier.weight(1f), R.drawable.ic_passport, stringResource(R.string.dock_passport)) { sheet = Sheet.PASSPORT }
                Button(
                    onClick = onToggleWalk,
                    modifier = Modifier.weight(1.6f).height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    // 預設內距左右各 24dp，四個中文字會被擠掉
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (walking) FogTeal else FogAccent,
                        contentColor = Color(0xFF10161F),
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FogIcon(
                            if (walking) R.drawable.ic_pause else R.drawable.ic_walk,
                            size = 19.dp,
                            tint = Color(0xFF10161F),
                        )
                        Text(
                            stringResource(if (walking) R.string.dock_pause else R.string.dock_start),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                DockButton(Modifier.weight(1f), R.drawable.ic_target, stringResource(R.string.dock_locate)) {
                    FogStore.lastFix?.let {
                        controller?.animateTo(it.lat, it.lng)
                        controller?.setZoom(16.0)
                    }
                }
                DockButton(Modifier.weight(1f), R.drawable.ic_settings, stringResource(R.string.dock_settings)) { sheet = Sheet.SETTINGS }
            }
        }

        // ── 面板 ────────────────────────────────────
        when (sheet) {
            Sheet.ACHIEVEMENTS -> AchievementSheet(stats) { sheet = Sheet.NONE }
            Sheet.PASSPORT -> PassportSheet(
                wishTick = wishTick,
                onPick = { detail = it },
                onDismiss = { sheet = Sheet.NONE },
            )

            Sheet.SETTINGS -> SettingsSheet(
                radius = radius,
                opacity = opacity,
                accuracy = accuracy,
                interval = interval,
                follow = follow,
                autoUpdate = autoUpdate,
                nearbyAlert = nearbyAlert,
                imperial = imperial,
                dailyGoalM = dailyGoal,
                language = language,
                hasBackground = hasBackgroundPermission,
                appVersion = appVersion,
                onRadius = {
                    radius = it
                    Settings.setRevealRadius(context, it)
                    FogStore.revealRadius = it.toDouble()
                    controller?.invalidateFog()
                },
                onOpacity = {
                    opacity = it
                    Settings.setFogOpacity(context, it)
                    controller?.invalidateFog()
                },
                onAccuracy = { accuracy = it; Settings.setAccuracyLimit(context, it) },
                onInterval = { interval = it; Settings.setIntervalSec(context, it) },
                onFollow = { follow = it; Settings.setFollow(context, it) },
                onAutoUpdate = { autoUpdate = it; Settings.setAutoUpdate(context, it) },
                onNearbyAlert = { nearbyAlert = it; Settings.setNearbyAlert(context, it) },
                onImperial = { imperial = it; Settings.setImperial(context, it) },
                onDailyGoal = { dailyGoal = it; Settings.setDailyGoal(context, it) },
                onLanguage = onLanguage,
                onRequestBackground = onRequestBackground,
                onCheckUpdate = { sheet = Sheet.NONE; onCheckUpdate() },
                onExport = { sheet = Sheet.NONE; onExport() },
                onImport = { sheet = Sheet.NONE; onImport() },
                onReset = {
                    FogStore.reset()
                    controller?.invalidateFog()
                },
                onDismiss = { sheet = Sheet.NONE },
            )

            Sheet.STATS -> StatsSheet(
                stats = stats,
                daily = FogStore.dailyDistances(),
                landmarkTotal = Landmarks.all(context).size,
                dailyGoalM = dailyGoal,
                todayM = FogStore.todayDistance(),
                onDismiss = { sheet = Sheet.NONE },
            )

            Sheet.NONE -> Unit
        }

        // ── 地標詳情 ────────────────────────────────
        detail?.let { lm ->
            LandmarkSheet(
                landmark = lm,
                onShowOnMap = {
                    detail = null
                    sheet = Sheet.NONE
                    controller?.animateTo(lm.lat, lm.lng)
                    controller?.setZoom(13.0)
                },
                onWishChanged = {
                    wishTick++
                    toasts.add(
                        Toast(
                            System.nanoTime(), R.drawable.ic_flame,
                            context.getString(
                                if (FogStore.isWished(lm.id)) R.string.toast_wish_added
                                else R.string.toast_wish_removed
                            ),
                            Format.landmarkName(context, lm),
                        )
                    )
                },
                onDismiss = { detail = null },
            )
        }

        // ── 更新 ────────────────────────────────────
        UpdateDialog(
            state = updateState,
            currentVersion = appVersion,
            onInstall = onInstallUpdate,
            onGrantPermission = onGrantInstallPermission,
            onDismiss = onDismissUpdate,
        )

        // ── 開場 ────────────────────────────────────
        if (showIntro) {
            IntroOverlay {
                Settings.setSeenIntro(context, true)
                showIntro = false
                onToggleWalk()
            }
        }
    }
}

@Composable
private fun StatChip(modifier: Modifier, value: String, label: String) {
    Surface(
        modifier = modifier,
        color = FogPanel.copy(alpha = 0.92f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FogLine),
    ) {
        Column(
            Modifier.padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, color = FogText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(label, color = FogMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun DockButton(
    modifier: Modifier,
    @androidx.annotation.DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    // 刻意不用 TextButton：它預設左右各有 12dp 內距又有最小寬度，
    // 五個項目擠一列時會把中文字切掉。
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FogIcon(icon, size = 20.dp)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = FogMuted,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BackgroundHint(onRequest: () -> Unit) {
    Surface(
        color = FogTeal.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FogTeal.copy(alpha = 0.4f)),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.bg_hint_title), color = FogText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.bg_hint_sub), color = FogMuted, fontSize = 11.sp)
            }
            TextButton(onClick = onRequest) {
                Text(stringResource(R.string.bg_hint_action), color = FogTeal, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun IntroOverlay(onStart: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF182231), Color(0xFF080B10)))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FogIcon(R.drawable.ic_rank_candle, size = 58.dp)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.app_name), color = FogText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.intro_lore),
                color = FogMuted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 24.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.intro_tagline),
                color = FogText, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FogAccent, contentColor = Color(0xFF10161F)
                ),
            ) { Text(stringResource(R.string.intro_start), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.intro_privacy),
                color = FogMuted, fontSize = 11.sp, textAlign = TextAlign.Center,
            )
        }
    }
}
