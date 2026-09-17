package edu.fudan.elearning.sync.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** “复小学”主题色：靛蓝（Indigo）系，与桌面端 styles.py 保持一致。 */
val Indigo = Color(0xFF4F46E5)
val IndigoDark = Color(0xFF312E81)
val IndigoHover = Color(0xFF4338CA)
val IndigoContainer = Color(0xFFE0E3FF)
val AppBg = Color(0xFFF6F7FC)
val AppCard = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = IndigoHover,
    background = AppBg,
    surface = AppCard
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FA8FF),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    secondary = Color(0xFFC7D2FE),
    background = Color(0xFF111118),
    surface = Color(0xFF1B1B24)
)

@Composable
fun FuXiaoXueTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
