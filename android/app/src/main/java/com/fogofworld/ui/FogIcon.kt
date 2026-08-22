package com.fogofworld.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全 App 統一的圖示畫法：手繪向量圖 + 指定顏色。
 * contentDescription 一律留空 —— 這些圖示旁邊都有文字說明，讀螢幕時重複唸沒有意義。
 */
@Composable
fun FogIcon(
    @DrawableRes icon: Int,
    size: Dp = 22.dp,
    tint: Color = FogAccent,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}
