package com.fogofworld.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fogofworld.R
import com.fogofworld.data.Achievements
import com.fogofworld.data.Format
import com.fogofworld.data.Landmarks
import com.fogofworld.data.Ranks
import com.fogofworld.data.Stats
import com.fogofworld.data.UpdateChecker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

// ── 統計 ───────────────────────────────────────────────

@Composable
fun StatsSheet(
    stats: Stats,
    daily: Map<String, Double>,
    landmarkTotal: Int,
    dailyGoalM: Int,
    todayM: Double,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val rank = Ranks.of(stats.areaKm2)
    SheetScaffold(
        stringResource(R.string.sheet_stats),
        "Lv.${rank.level} " + stringResource(rank.rank.nameRes),
        onDismiss,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

            // 階級進度
            Surface(
                color = FogAccent.copy(alpha = 0.07f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, FogAccent.copy(alpha = 0.3f)),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(rank.rank.icon, fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Lv.${rank.level} " + stringResource(rank.rank.nameRes),
                                color = FogText, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            )
                            Text(
                                rank.next?.let {
                                    stringResource(
                                        R.string.stats_rank_next,
                                        stringResource(it.nameRes),
                                        Format.area(context, it.at - stats.areaKm2),
                                    )
                                } ?: stringResource(R.string.stats_max_rank),
                                color = FogMuted, fontSize = 11.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { rank.fraction },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)),
                        color = FogAccent,
                        trackColor = Color.White.copy(alpha = 0.12f),
                        drawStopIndicator = {},
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 每日目標
            if (dailyGoalM > 0) {
                Spacer(Modifier.height(14.dp))
                val frac = (todayM / dailyGoalM).toFloat().coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.stats_goal_today),
                        color = FogText, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Format.distance(context, todayM) + " / " + Format.distance(context, dailyGoalM.toDouble()),
                        color = if (frac >= 1f) FogAccent else FogMuted, fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)),
                    color = if (frac >= 1f) FogAccent else FogTeal,
                    trackColor = Color.White.copy(alpha = 0.12f),
                    drawStopIndicator = {},
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.stats_recent), color = FogText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            DailyChart(daily)

            Spacer(Modifier.height(16.dp))

            val rows = listOf(
                stringResource(R.string.row_distance) to Format.distance(context, stats.distanceM),
                stringResource(R.string.row_area) to Format.area(context, stats.areaKm2),
                stringResource(R.string.row_cells) to "${stats.cells}",
                stringResource(R.string.row_days) to stringResource(R.string.days_unit, stats.activeDays),
                stringResource(R.string.row_streak) to stringResource(R.string.days_unit, stats.streak),
                stringResource(R.string.row_best_day) to Format.distance(context, stats.bestDayM),
                stringResource(R.string.row_best_session) to Format.distance(context, stats.bestSessionM),
                stringResource(R.string.row_altitude) to Format.altitude(context, stats.maxAltitude),
                stringResource(R.string.row_landmarks) to "${stats.landmarks} / $landmarkTotal",
                stringResource(R.string.row_continents) to "${stats.continents} / ${Landmarks.CONTINENTS.size}",
                stringResource(R.string.row_achievements) to "${stats.achievementCount} / ${Achievements.COUNT}",
            )
            rows.forEach { (label, value) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = FogMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(value, color = FogText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DailyChart(daily: Map<String, Double>) {
    val context = LocalContext.current
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val labelFmt = SimpleDateFormat("d", Locale.US)
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -13)

    val bars = ArrayList<Pair<String, Double>>(14)
    repeat(14) {
        val key = fmt.format(cal.time)
        bars.add(labelFmt.format(cal.time) to (daily[key] ?: 0.0))
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    val max = bars.maxOf { it.second }.coerceAtLeast(1.0)

    Row(
        Modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { (label, meters) ->
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                val frac = (meters / max).toFloat().coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((4 + 72 * frac).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (meters > 0) FogAccent else Color.White.copy(alpha = 0.08f))
                )
                Spacer(Modifier.height(4.dp))
                Text(label, color = FogMuted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
    val total = bars.sumOf { it.second }
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(R.string.stats_recent_total, Format.distance(context, total)),
        color = FogMuted, fontSize = 11.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── 更新 ───────────────────────────────────────────────

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Found(val release: UpdateChecker.Release) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class NeedPermission(val release: UpdateChecker.Release) : UpdateState
    data class Message(val text: String) : UpdateState
}

@Composable
fun UpdateDialog(
    state: UpdateState,
    currentVersion: String,
    onInstall: () -> Unit,
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateState.Found -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = FogPanel,
            title = { Text(stringResource(R.string.update_title, state.release.version), color = FogText) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.update_body, currentVersion),
                        color = FogMuted, fontSize = 13.sp,
                    )
                    if (state.release.sizeBytes > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(
                                R.string.update_size,
                                String.format(Locale.getDefault(), "%.1f MB", state.release.sizeBytes / 1024.0 / 1024.0),
                            ),
                            color = FogMuted, fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onInstall) { Text(stringResource(R.string.update_download), color = FogAccent) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later), color = FogMuted) }
            },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            containerColor = FogPanel,
            title = { Text(stringResource(R.string.update_downloading), color = FogText) },
            text = {
                Column {
                    if (state.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
                            color = FogAccent,
                            trackColor = Color.White.copy(alpha = 0.12f),
                            drawStopIndicator = {},
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("${(state.progress * 100).roundToInt()}%", color = FogMuted, fontSize = 12.sp)
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
                            color = FogAccent,
                            trackColor = Color.White.copy(alpha = 0.12f),
                        )
                    }
                }
            },
            confirmButton = {},
        )

        is UpdateState.NeedPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = FogPanel,
            title = { Text(stringResource(R.string.update_perm_title), color = FogText) },
            text = {
                Text(stringResource(R.string.update_perm_text), color = FogMuted, fontSize = 13.sp)
            },
            confirmButton = {
                TextButton(onClick = onGrantPermission) { Text(stringResource(R.string.update_goto_settings), color = FogAccent) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = FogMuted) }
            },
        )

        is UpdateState.Message -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = FogPanel,
            text = { Text(state.text, color = FogText, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok), color = FogAccent) }
            },
        )

        else -> Unit
    }
}

// ── 共用 ───────────────────────────────────────────────

// 顯示格式一律走 Format，會跟著語言與單位設定
