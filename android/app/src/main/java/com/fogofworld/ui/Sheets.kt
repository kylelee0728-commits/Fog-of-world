package com.fogofworld.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fogofworld.data.Achievements
import com.fogofworld.data.FogStore
import com.fogofworld.data.Grid
import com.fogofworld.data.Landmark
import com.fogofworld.data.Landmarks
import com.fogofworld.data.Stats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun formatDate(ts: Long): String =
    SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(ts))

private fun formatDistance(m: Double): String =
    if (m < 1000) "${m.roundToInt()} 公尺"
    else String.format(Locale.US, if (m < 10_000) "%.2f 公里" else "%.1f 公里", m / 1000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetScaffold(title: String, subtitle: String, onDismiss: () -> Unit, body: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FogPanel,
        contentColor = FogText,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FogText)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 12.sp, color = FogMuted)
            }
            Spacer(Modifier.height(12.dp))
            body()
        }
    }
}

// ── 成就 ───────────────────────────────────────────────

@Composable
fun AchievementSheet(stats: Stats, onDismiss: () -> Unit) {
    val unlocked = FogStore.unlockedAchievementIds()
    SheetScaffold("🏅 成就", "${unlocked.size} / ${Achievements.COUNT} 已解鎖", onDismiss) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(520.dp),
        ) {
            items(Achievements.ALL, key = { it.id }) { a ->
                val at = unlocked[a.id]
                val progress = (a.value(stats) / a.target).toFloat().coerceIn(0f, 1f)
                Surface(
                    color = if (at != null) FogAccent.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.035f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (at != null) FogAccent.copy(alpha = 0.4f) else FogLine,
                    ),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Text(if (at != null) a.icon else "🔒", fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                a.name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (at != null) FogText else FogText.copy(alpha = 0.55f),
                            )
                            Text(a.desc, fontSize = 11.sp, color = FogMuted)
                            Spacer(Modifier.height(5.dp))
                            if (at != null) {
                                Text("${formatDate(at)} 解鎖", fontSize = 10.sp, color = FogAccent)
                            } else {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)),
                                    color = FogTeal,
                                    trackColor = Color.White.copy(alpha = 0.1f),
                                    drawStopIndicator = {},
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 世界護照 ───────────────────────────────────────────

@Composable
fun PassportSheet(onPick: (Landmark) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val all = Landmarks.all(context)
    val visited = FogStore.visitedLandmarkIds()
    val here = FogStore.lastFix
    val continents = visited.keys.mapNotNull { id -> all.firstOrNull { it.id == id }?.continent }.toSet()

    SheetScaffold("🛂 世界護照", "${visited.size} / ${all.size} 個地標 · ${continents.size} 個大洲", onDismiss) {
        // 最近的未造訪地標：距離與方位都先算好，避免在 lambda 裡再處理可空的定位
        val nearest: Triple<Landmark, Double, String>? = here?.let { fix ->
            all.filter { it.id !in visited.keys }
                .minByOrNull { Grid.distanceM(fix.lat, fix.lng, it.lat, it.lng) }
                ?.let { lm ->
                    Triple(
                        lm,
                        Grid.distanceM(fix.lat, fix.lng, lm.lat, lm.lng),
                        Grid.compass(Grid.bearing(fix.lat, fix.lng, lm.lat, lm.lng)),
                    )
                }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.height(520.dp),
        ) {
            if (nearest != null) {
                item {
                    val (lm, dist, dir) = nearest
                    Surface(
                        color = FogTeal.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, FogTeal.copy(alpha = 0.3f)),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "${lm.icon} 最近的目標：${lm.zh}",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FogText,
                            )
                            Text(
                                "往${dir}方 ${formatDistance(dist)} · 走進 1 公里內即可蓋章",
                                fontSize = 12.sp, color = FogMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            for (continent in Landmarks.CONTINENTS) {
                val list = all.filter { it.continent == continent }
                if (list.isEmpty()) continue
                item(key = "head-$continent") {
                    Row(
                        Modifier.padding(top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(continent, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FogText)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${list.count { it.id in visited.keys }} / ${list.size}",
                            fontSize = 11.sp, color = FogMuted,
                        )
                    }
                }
                items(list, key = { it.id }) { lm ->
                    val at = visited[lm.id]
                    val dist = here?.let { Grid.distanceM(it.lat, it.lng, lm.lat, lm.lng) }
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(lm) },
                        color = if (at != null) FogAccent.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.03f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (at != null) FogAccent.copy(alpha = 0.35f) else FogLine,
                        ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (at != null) lm.icon else "🔒", fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(lm.zh, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FogText)
                                Text("${lm.country} · ${lm.en}", fontSize = 11.sp, color = FogMuted)
                            }
                            Text(
                                if (at != null) formatDate(at) else dist?.let { formatDistance(it) } ?: "",
                                fontSize = 11.sp, color = FogMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 設定 ───────────────────────────────────────────────

@Composable
fun SettingsSheet(
    radius: Int,
    opacity: Int,
    accuracy: Int,
    interval: Int,
    follow: Boolean,
    autoUpdate: Boolean,
    hasBackground: Boolean,
    appVersion: String,
    onRadius: (Int) -> Unit,
    onOpacity: (Int) -> Unit,
    onAccuracy: (Int) -> Unit,
    onInterval: (Int) -> Unit,
    onFollow: (Boolean) -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
    onRequestBackground: () -> Unit,
    onCheckUpdate: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }

    SheetScaffold("⚙️ 設定", "版本 $appVersion", onDismiss) {
        Column(Modifier.height(520.dp)) {
            SliderRow("撥霧半徑", "走過的地方會清除多大範圍", "$radius m", radius.toFloat(), 20f..200f, 18) {
                onRadius(it.roundToInt())
            }
            SliderRow("迷霧濃度", "未探索區域的暗度", "$opacity%", opacity.toFloat(), 30f..100f, 14) {
                onOpacity(it.roundToInt())
            }
            SliderRow("GPS 精度門檻", "誤差大於此值的定位會被忽略", "$accuracy m", accuracy.toFloat(), 10f..200f, 19) {
                onAccuracy(it.roundToInt())
            }
            SliderRow("定位間隔", "拉長比較省電", "$interval 秒", interval.toFloat(), 1f..30f, 29) {
                onInterval(it.roundToInt())
            }

            SwitchRow("畫面跟隨我", "移動時地圖自動置中", follow, onFollow)
            SwitchRow("自動檢查更新", "開啟 App 時看看有沒有新版本", autoUpdate, onAutoUpdate)

            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallAction("⬇️ 檢查更新", Modifier.weight(1f), onCheckUpdate)
            }

            Spacer(Modifier.height(6.dp))
            Text("備份", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FogText)
            Text(
                "匯出的檔案與網頁版通用，可以互相匯入。重裝 App 前記得先匯出。",
                fontSize = 11.sp, color = FogMuted,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction("📤 匯出存檔", Modifier.weight(1f), onExport)
                SmallAction("📥 匯入存檔", Modifier.weight(1f), onImport)
            }

            Surface(
                color = if (hasBackground) FogTeal.copy(alpha = 0.08f) else FogAccent.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    (if (hasBackground) FogTeal else FogAccent).copy(alpha = 0.35f),
                ),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (hasBackground) "背景記錄：已開啟" else "背景記錄：未開啟",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FogText,
                        )
                        Text(
                            if (hasBackground) "螢幕關掉、切到別的 App 都會繼續記錄"
                            else "要整趟都記錄，定位權限需選「一律允許」",
                            fontSize = 11.sp, color = FogMuted,
                        )
                    }
                    if (!hasBackground) {
                        TextButton(onClick = onRequestBackground) {
                            Text("去允許", color = FogAccent, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { confirmReset = true }) {
                Text("🗑️ 清除所有紀錄", color = FogDanger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "所有位置資料只存在這台手機上，沒有後端也不會上傳。\n地圖圖磚來源 © OpenStreetMap 貢獻者。",
                fontSize = 11.sp, color = FogMuted, lineHeight = 18.sp,
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = FogPanel,
            title = { Text("清除所有紀錄？", color = FogText) },
            text = {
                Text("走過的霧會全部長回來，成就與護照也會歸零，這個動作無法復原。", color = FogMuted)
            },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset() }) {
                    Text("確定清除", color = FogDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("取消", color = FogMuted) }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FogText)
            Text(subtitle, fontSize = 11.sp, color = FogMuted)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = FogAccent),
        )
    }
}

/** 小按鈕：不用 Button，避免預設內距把中文字擠掉 */
@Composable
private fun SmallAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, FogLine),
    ) {
        Text(
            label,
            color = FogText,
            fontSize = 13.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp, horizontal = 6.dp),
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    subtitle: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FogText)
                Text(subtitle, fontSize = 11.sp, color = FogMuted)
            }
            Text(value, fontSize = 12.sp, color = FogAccent)
        }
        Slider(
            value = current,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = FogAccent,
                activeTrackColor = FogAccent,
            ),
        )
    }
}

@Composable
fun EmptyBox() {
    Box(Modifier.background(Color.Transparent))
}
