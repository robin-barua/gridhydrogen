package com.gridsim.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFF1565C0)
private val Secondary = Color(0xFF2E7D32)
private val Tertiary = Color(0xFFEF6C00)

private val LightColors = lightColorScheme(primary = Primary, secondary = Secondary, tertiary = Tertiary)
private val DarkColors = darkColorScheme(primary = Primary, secondary = Secondary, tertiary = Tertiary)

@Composable
fun GridHydrogenSimTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
