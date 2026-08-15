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
import com.fogofworld.data.Achievements
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
    onDismiss: () -> Unit,
) {
    val rank = Ranks.of(stats.areaKm2)
    SheetScaffold("📊 我的紀錄", "Lv.${rank.level} ${rank.rank.name}", onDismiss) {
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
                                "Lv.${rank.level} ${rank.rank.name}",
                                color = FogText, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            )
                            Text(
                                rank.next?.let {
                                    "距離「${it.name}」還差 ${fmt2(it.at - stats.areaKm2)} km²"
                                } ?: "已達最高階級",
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

            // 近 14 天
            Text("近 14 天", color = FogText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            DailyChart(daily)

            Spacer(Modifier.height(16.dp))

            val rows = listOf(
                "總距離" to fmtDistance(stats.distanceM),
                "已撥霧面積" to "${fmt2(stats.areaKm2)} km²",
                "霧格數" to "${stats.cells}",
                "探索天數" to "${stats.activeDays} 天",
                "目前連續" to "${stats.streak} 天",
                "單日最遠" to fmtDistance(stats.bestDayM),
                "單次最長" to fmtDistance(stats.bestSessionM),
                "最高海拔" to "${stats.maxAltitude.roundToInt()} 公尺",
                "世界地標" to "${stats.landmarks} / $landmarkTotal",
                "足跡大洲" to "${stats.continents} / ${Landmarks.CONTINENTS.size}",
                "成就" to "${stats.achievementCount} / ${Achievements.COUNT}",
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
        "這兩週共走了 ${fmtDistance(total)}",
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
            title = { Text("有新版本 ${state.release.version}", color = FogText) },
            text = {
                Column {
                    Text(
                        "目前是 $currentVersion，可以直接更新，走過的紀錄會保留。",
                        color = FogMuted, fontSize = 13.sp,
                    )
                    if (state.release.sizeBytes > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "下載大小約 ${"%.1f".format(state.release.sizeBytes / 1024.0 / 1024.0)} MB",
                            color = FogMuted, fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onInstall) { Text("下載並安裝", color = FogAccent) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("稍後再說", color = FogMuted) }
            },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            containerColor = FogPanel,
            title = { Text("下載中", color = FogText) },
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
            title = { Text("需要安裝權限", color = FogText) },
            text = {
                Text(
                    "Android 需要你允許本 App 安裝應用程式，才能自動更新。\n" +
                        "開啟後回到這裡再按一次更新即可。",
                    color = FogMuted, fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = onGrantPermission) { Text("去設定", color = FogAccent) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消", color = FogMuted) }
            },
        )

        is UpdateState.Message -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = FogPanel,
            text = { Text(state.text, color = FogText, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("好", color = FogAccent) }
            },
        )

        else -> Unit
    }
}

// ── 共用 ───────────────────────────────────────────────

internal fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)

internal fun fmtDistance(m: Double): String =
    if (m < 1000) "${m.roundToInt()} 公尺"
    else String.format(Locale.US, if (m < 10_000) "%.2f 公里" else "%.1f 公里", m / 1000)
