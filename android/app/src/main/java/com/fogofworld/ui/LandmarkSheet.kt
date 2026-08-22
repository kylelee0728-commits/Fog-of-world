package com.fogofworld.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fogofworld.R
import com.fogofworld.data.FogStore
import com.fogofworld.data.Format
import com.fogofworld.data.Grid
import com.fogofworld.data.Landmark

/**
 * 單一地標的詳情：介紹、距離方位、蓋章狀態，以及想去清單。
 *
 * 介紹文字跟名稱一樣走中／英雙語，由 Format.landmarkDesc 依語系挑。
 */
@Composable
fun LandmarkSheet(
    landmark: Landmark,
    onShowOnMap: () -> Unit,
    onWishChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val visitedAt = FogStore.visitedLandmarkIds()[landmark.id]
    var wished by remember(landmark.id) { mutableStateOf(FogStore.isWished(landmark.id)) }
    val here = FogStore.lastFix

    SheetScaffold(
        landmark.icon,
        Format.landmarkName(context, landmark),
        "${Format.country(context, landmark)} · ${Format.continent(context, landmark.continent)}",
        onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {

            // 蓋章狀態
            Surface(
                color = if (visitedAt != null) FogAccent.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (visitedAt != null) FogAccent.copy(alpha = 0.4f) else FogLine,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FogIcon(
                        if (visitedAt != null) R.drawable.ic_check else R.drawable.ic_lock,
                        size = 18.dp,
                        tint = if (visitedAt != null) FogAccent else FogMuted,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (visitedAt != null) stringResource(R.string.lm_visited, formatDate(visitedAt))
                        else stringResource(R.string.lm_not_visited),
                        fontSize = 12.sp,
                        color = if (visitedAt != null) FogText else FogMuted,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 距離與方位
            val away = here?.let { Grid.distanceM(it.lat, it.lng, landmark.lat, landmark.lng) }
            Text(
                when {
                    away == null -> stringResource(R.string.lm_no_fix)
                    // 站在原地時方位是雜訊，不如不說
                    away < 50 -> stringResource(R.string.lm_distance_here)
                    else -> stringResource(
                        R.string.lm_distance_away,
                        Format.distance(context, away),
                        Format.compass(context, Grid.bearing(here!!.lat, here.lng, landmark.lat, landmark.lng)),
                    )
                },
                fontSize = 12.sp, color = FogMuted,
            )

            Spacer(Modifier.height(16.dp))

            // 介紹
            Text(
                stringResource(R.string.lm_intro),
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FogText,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                Format.landmarkDesc(context, landmark),
                fontSize = 13.sp, color = FogMuted, lineHeight = 22.sp,
            )

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LandmarkAction(
                    R.drawable.ic_target,
                    stringResource(R.string.lm_show_on_map),
                    Modifier.weight(1f),
                    onClick = onShowOnMap,
                )
                LandmarkAction(
                    R.drawable.ic_flame,
                    stringResource(if (wished) R.string.lm_wish_remove else R.string.lm_wish_add),
                    Modifier.weight(1f),
                    tint = if (wished) FogTeal else FogAccent,
                ) {
                    wished = FogStore.toggleWish(landmark.id)
                    onWishChanged()
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LandmarkAction(
    @androidx.annotation.DrawableRes icon: Int,
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
            Modifier.fillMaxWidth().padding(vertical = 11.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FogIcon(icon, size = 16.dp, tint = tint)
            Spacer(Modifier.width(6.dp))
            Text(label, color = FogText, fontSize = 12.sp, maxLines = 1)
        }
    }
}
