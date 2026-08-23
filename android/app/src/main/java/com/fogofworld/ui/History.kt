package com.fogofworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fogofworld.R
import com.fogofworld.data.Format
import com.fogofworld.data.Outing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 熱度月曆顯示幾週 */
private const val WEEKS = 26

/**
 * 半年份的熱度月曆：一欄一週、一列一個星期幾，顏色深淺代表當天走了多遠。
 *
 * 資料就是既有的每日里程，不需要額外儲存任何東西。
 */
@Composable
fun DayCalendar(daily: Map<String, Double>) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val max = daily.values.maxOrNull() ?: 0.0

    // 從「這一週的星期日」往回推 WEEKS 週，讓最後一欄是本週
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -(get(Calendar.DAY_OF_WEEK) - 1))
        add(Calendar.WEEK_OF_YEAR, -(WEEKS - 1))
    }

    val weeks = ArrayList<List<Double>>(WEEKS)
    repeat(WEEKS) {
        val week = ArrayList<Double>(7)
        repeat(7) {
            week.add(daily[fmt.format(cal.time)] ?: 0.0)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        weeks.add(week)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { metres ->
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(heatColor(metres, max)),
                    )
                }
            }
        }
    }
}

/** 0 是幾乎看不見的底色，越接近當期最大值越亮 */
private fun heatColor(metres: Double, max: Double): Color {
    if (metres <= 0.0 || max <= 0.0) return Color.White.copy(alpha = 0.06f)
    // 開根號讓中低里程的差異看得出來，不會被一次長程走完全壓平
    val t = kotlin.math.sqrt((metres / max).coerceIn(0.0, 1.0)).toFloat()
    return Color(0xFFF5C86B).copy(alpha = 0.22f + 0.78f * t)
}

/** 一次外出的紀錄列 */
@Composable
fun OutingRow(outing: Outing) {
    val context = LocalContext.current
    val started = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(outing.startTime))
    val minutes = (outing.durationMs / 60_000L).toInt()
    val duration = if (minutes >= 60) {
        stringResource(R.string.duration_hm, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.duration_m, minutes)
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FogIcon(R.drawable.ic_walk, size = 15.dp, tint = FogTeal)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.outing_summary, started, duration),
                color = FogText, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.outing_detail, outing.cells, outing.landmarks),
                color = FogMuted, fontSize = 11.sp,
            )
        }
        Text(
            Format.distance(context, outing.distanceM),
            color = FogAccent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
        )
    }
}
