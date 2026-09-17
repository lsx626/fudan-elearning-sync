package edu.fudan.elearning.sync.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 复旦蓝主题色。 */
val FudanBlue = Color(0xFF1F4FA3)
val FudanBlueDark = Color(0xFF163B7A)
val FudanRed = Color(0xFFA41E25)
val FudanBg = Color(0xFFF3F5FA)
val FudanCard = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = FudanBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    secondary = FudanRed,
    background = FudanBg,
    surface = FudanCard
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FBAF5),
    secondary = Color(0xFFE5737A)
)

@Composable
fun FudanSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
