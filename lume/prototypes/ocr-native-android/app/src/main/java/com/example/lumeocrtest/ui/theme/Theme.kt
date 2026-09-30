package com.example.lumeocrtest.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Todos os tons de superfície são definidos: sem isso o Material aplica um lilás padrão nos cartões.
private val LumeColors = lightColorScheme(
    primary = LumeDarkGreen, onPrimary = Color.White,
    primaryContainer = LumeLightGreen, onPrimaryContainer = LumeDarkGreen,
    secondary = InkSoft, onSecondary = Color.White,
    secondaryContainer = LumeLightGreen, onSecondaryContainer = LumeDarkGreen,
    tertiary = LumeGreen, onTertiary = Color.White,
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = LumeMist, onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Sheet, surfaceContainerLow = Sheet, surfaceContainer = Sheet,
    surfaceContainerHigh = Color(0xFFF3F5EE), surfaceContainerHighest = Color(0xFFF3F5EE),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF8A9386), outlineVariant = Hairline,
    error = ErrorRed, onError = Color.White,
)

private val LumeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LumeOCRTestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LumeColors, typography = Typography, shapes = LumeShapes, content = content)
}
