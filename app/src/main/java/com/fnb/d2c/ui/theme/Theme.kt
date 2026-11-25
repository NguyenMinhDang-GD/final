package com.fnb.d2c.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrothRed = Color(0xFFC83E2B)
val BrothRedDark = Color(0xFF962819)
val BrothAmber = Color(0xFFE67E22)
val HerbGreen = Color(0xFF2E7D32)
val CreamBackground = Color(0xFFFFFBF8)
val WarmSurface = Color(0xFFFFFFFF)
val SoftWarmVariant = Color(0xFFFBECE7)
val WarningNoteBg = Color(0xFFFFF3CD)
val WarningNoteBorder = Color(0xFFD97706)
val DangerAlertBg = Color(0xFFFEE2E2)
val DangerAlertText = Color(0xFFB91C1C)

private val LightColors = lightColorScheme(
    primary = BrothRed,
    onPrimary = Color.White,
    primaryContainer = SoftWarmVariant,
    onPrimaryContainer = BrothRedDark,
    secondary = HerbGreen,
    onSecondary = Color.White,
    tertiary = BrothAmber,
    background = CreamBackground,
    surface = WarmSurface,
    surfaceVariant = Color(0xFFF6EFEA),
    onSurfaceVariant = Color(0xFF5C4B45)
)

@Composable
fun CoBaFnbTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
