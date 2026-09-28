package com.example.lumeocrtest.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LumeColors = lightColorScheme(
    primary = LumeDarkGreen, onPrimary = Color.White,
    secondary = Color(0xFF697264), onSecondary = Color.White,
    secondaryContainer = LumeLightGreen, onSecondaryContainer = LumeDarkGreen,
    tertiary = Color(0xFF53775B),
    background = Color(0xFFFAFAF5), onBackground = Color(0xFF1D241D),
    surface = Color(0xFFFAFAF5), onSurface = Color(0xFF1D241D),
    surfaceVariant = Color(0xFFEEF2E8), onSurfaceVariant = Color(0xFF697264),
    outline = Color(0xFF8A9386)
)
@Composable
fun LumeOCRTestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LumeColors, typography = Typography, content = content)
}
