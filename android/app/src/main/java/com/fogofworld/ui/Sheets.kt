package com.fogofworld.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fogofworld.R
import com.fogofworld.data.Achievements
import com.fogofworld.data.AppLanguage
import com.fogofworld.data.FogStore
import com.fogofworld.data.Format
import com.fogofworld.data.Grid
import com.fogofworld.data.Landmark
import com.fogofworld.data.Landmarks
import com.fogofworld.data.Stats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

internal fun formatDate(ts: Long): String =
    SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(ts))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetScaffold(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    body: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FogPanel,
        contentColor = FogText,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FogIcon(icon, size = 20.dp)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FogText)
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, fontSize = 12.sp, color = FogMuted)
                    }
                }
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
    SheetScaffold(
        R.drawable.ic_award,
        stringResource(R.string.sheet_achievements),
        stringResource(R.string.sheet_achievements_sub, unlocked.size, Achievements.COUNT),
        onDismiss,
    ) {
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
                    border = BorderStroke(1.dp, if (at != null) FogAccent.copy(alpha = 0.4f) else FogLine),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        FogIcon(
                            if (at != null) a.icon else R.drawable.ic_lock,
                            size = 22.dp,
                            tint = if (at != null) FogAccent else FogMuted.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(a.nameRes), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (at != null) FogText else FogText.copy(alpha = 0.55f),
                            )
                            Text(stringResource(a.descRes), fontSize = 11.sp, color = FogMuted)
                            Spacer(Modifier.height(5.dp))
                            if (at != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FogIcon(R.drawable.ic_check, size = 11.dp)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        stringResource(R.string.unlocked_on, formatDate(at)),
                                        fontSize = 10.sp, color = FogAccent,
                                    )
                                }
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

    SheetScaffold(
        R.drawable.ic_passport,
        stringResource(R.string.sheet_passport),
        stringResource(R.string.sheet_passport_sub, visited.size, all.size, continents.size),
        onDismiss,
    ) {
        // 最近的未造訪地標：距離與方位都先算好，避免在 lambda 裡再處理可空的定位
        val nearest: Triple<Landmark, Double, String>? = here?.let { fix ->
            all.filter { it.id !in visited.keys }
                .minByOrNull { Grid.distanceM(fix.lat, fix.lng, it.lat, it.lng) }
                ?.let { lm ->
                    Triple(
                        lm,
                        Grid.distanceM(fix.lat, fix.lng, lm.lat, lm.lng),
                        Format.compass(context, Grid.bearing(fix.lat, fix.lng, lm.lat, lm.lng)),
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
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            FogIcon(lm.icon, size = 20.dp, tint = FogTeal)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.nearest_title, Format.landmarkName(context, lm)),
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FogText,
                                )
                                Text(
                                    stringResource(R.string.nearest_sub, dir, Format.distance(context, dist)),
                                    fontSize = 12.sp, color = FogMuted,
                                )
                            }
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
                        Text(
                            Format.continent(context, continent),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FogText,
                        )
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
                        border = BorderStroke(1.dp, if (at != null) FogAccent.copy(alpha = 0.35f) else FogLine),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FogIcon(
                                if (at != null) lm.icon else R.drawable.ic_lock,
                                size = 18.dp,
                                tint = if (at != null) FogAccent else FogMuted.copy(alpha = 0.6f),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    Format.landmarkName(context, lm),
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FogText,
                                )
                                Text(
                                    "${lm.country} · ${Format.landmarkSecondary(context, lm)}",
                                    fontSize = 11.sp, color = FogMuted,
                                )
                            }
                            Text(
                                if (at != null) formatDate(at)
                                else dist?.let { Format.distance(context, it) } ?: "",
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
    imperial: Boolean,
    dailyGoalM: Int,
    language: AppLanguage,
    hasBackground: Boolean,
    appVersion: String,
    onRadius: (Int) -> Unit,
    onOpacity: (Int) -> Unit,
    onAccuracy: (Int) -> Unit,
    onInterval: (Int) -> Unit,
    onFollow: (Boolean) -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
    onImperial: (Boolean) -> Unit,
    onDailyGoal: (Int) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onRequestBackground: () -> Unit,
    onCheckUpdate: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var pickLanguage by remember { mutableStateOf(false) }

    SheetScaffold(
        R.drawable.ic_settings,
        stringResource(R.string.sheet_settings),
        stringResource(R.string.version_label, appVersion),
        onDismiss,
    ) {
        Column(
            Modifier
                .height(520.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // 語言與單位
            ValueRow(
                stringResource(R.string.set_language),
                if (language == AppLanguage.SYSTEM) stringResource(R.string.lang_system) else language.label,
            ) { pickLanguage = true }

            SwitchRow(
                stringResource(R.string.set_units),
                if (imperial) stringResource(R.string.units_imperial) else stringResource(R.string.units_metric),
                imperial,
                onImperial,
            )

            // 每日目標：0 表示關閉
            SliderRow(
                stringResource(R.string.set_goal),
                stringResource(R.string.set_goal_sub),
                if (dailyGoalM == 0) stringResource(R.string.goal_off)
                else Format.distance(context, dailyGoalM.toDouble()),
                (dailyGoalM / 500).toFloat(),
                0f..40f,
                39,
            ) { onDailyGoal((it.roundToInt()) * 500) }

            SliderRow(
                stringResource(R.string.set_radius), stringResource(R.string.set_radius_sub),
                "$radius m", radius.toFloat(), 20f..200f, 17,
            ) { onRadius(it.roundToInt()) }

            SliderRow(
                stringResource(R.string.set_opacity), stringResource(R.string.set_opacity_sub),
                "$opacity%", opacity.toFloat(), 30f..100f, 13,
            ) { onOpacity(it.roundToInt()) }

            SliderRow(
                stringResource(R.string.set_accuracy), stringResource(R.string.set_accuracy_sub),
                "$accuracy m", accuracy.toFloat(), 10f..200f, 18,
            ) { onAccuracy(it.roundToInt()) }

            SliderRow(
                stringResource(R.string.set_interval), stringResource(R.string.set_interval_sub),
                "$interval s", interval.toFloat(), 1f..30f, 28,
            ) { onInterval(it.roundToInt()) }

            SwitchRow(
                stringResource(R.string.set_follow), stringResource(R.string.set_follow_sub), follow, onFollow,
            )
            SwitchRow(
                stringResource(R.string.set_autoupdate), stringResource(R.string.set_autoupdate_sub),
                autoUpdate, onAutoUpdate,
            )

            SmallAction(R.drawable.ic_download, stringResource(R.string.action_check_update), Modifier.fillMaxWidth(), onClick = onCheckUpdate)

            Spacer(Modifier.height(12.dp))
            Surface(
                color = (if (hasBackground) FogTeal else FogAccent).copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, (if (hasBackground) FogTeal else FogAccent).copy(alpha = 0.35f)),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(if (hasBackground) R.string.bg_on else R.string.bg_off),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FogText,
                        )
                        Text(
                            stringResource(if (hasBackground) R.string.bg_on_sub else R.string.bg_off_sub),
                            fontSize = 11.sp, color = FogMuted,
                        )
                    }
                    if (!hasBackground) {
                        TextButton(onClick = onRequestBackground) {
                            Text(stringResource(R.string.bg_hint_action), color = FogAccent, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.backup_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FogText)
            Text(stringResource(R.string.backup_sub), fontSize = 11.sp, color = FogMuted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(R.drawable.ic_export, stringResource(R.string.action_export), Modifier.weight(1f), onClick = onExport)
                SmallAction(R.drawable.ic_import, stringResource(R.string.action_import), Modifier.weight(1f), onClick = onImport)
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { confirmReset = true }) {
                FogIcon(R.drawable.ic_trash, size = 16.dp, tint = FogDanger)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_reset), color = FogDanger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.privacy_note), fontSize = 11.sp, color = FogMuted, lineHeight = 18.sp)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickLanguage) {
        AlertDialog(
            onDismissRequest = { pickLanguage = false },
            containerColor = FogPanel,
            title = { Text(stringResource(R.string.set_language), color = FogText) },
            text = {
                Column {
                    AppLanguage.entries.forEach { lang ->
                        val label = if (lang == AppLanguage.SYSTEM) stringResource(R.string.lang_system) else lang.label
                        Text(
                            (if (lang == language) "● " else "○ ") + label,
                            color = if (lang == language) FogAccent else FogText,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pickLanguage = false; onLanguage(lang) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickLanguage = false }) {
                    Text(stringResource(R.string.cancel), color = FogMuted)
                }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = FogPanel,
            title = { Text(stringResource(R.string.reset_title), color = FogText) },
            text = { Text(stringResource(R.string.reset_text), color = FogMuted) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset() }) {
                    Text(stringResource(R.string.action_confirm_erase), color = FogDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.cancel), color = FogMuted)
                }
            },
        )
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FogText, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = FogAccent)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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

/** 小按鈕：不用 Button，避免預設內距把文字擠掉 */
@Composable
private fun SmallAction(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = FogAccent,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, FogLine),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FogIcon(icon, size = 16.dp, tint = tint)
            Spacer(Modifier.width(6.dp))
            Text(label, color = FogText, fontSize = 13.sp, maxLines = 1, textAlign = TextAlign.Center)
        }
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
            colors = SliderDefaults.colors(thumbColor = FogAccent, activeTrackColor = FogAccent),
        )
    }
}
