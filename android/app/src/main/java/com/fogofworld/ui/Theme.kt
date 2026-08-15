package com.fogofworld.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FogAccent = Color(0xFFF5C86B)      // 霧中的燈火
val FogTeal = Color(0xFF5FD0C5)        // 已探索的冷光
val FogBg = Color(0xFF0B0F14)
val FogPanel = Color(0xFF101620)
val FogMuted = Color(0xFF93A3B6)
val FogText = Color(0xFFE8EEF6)
val FogLine = Color(0x1AFFFFFF)
val FogDanger = Color(0xFFFF6B6B)

private val Scheme = darkColorScheme(
    primary = FogAccent,
    onPrimary = Color(0xFF10161F),
    secondary = FogTeal,
    onSecondary = Color(0xFF10161F),
    background = FogBg,
    onBackground = FogText,
    surface = FogPanel,
    onSurface = FogText,
    surfaceVariant = Color(0xFF18202C),
    onSurfaceVariant = FogMuted,
    error = FogDanger,
)

/** 這個 App 只有一種樣子：戰後的暗色迷霧，不跟隨系統的淺色模式 */
@Composable
fun FogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
