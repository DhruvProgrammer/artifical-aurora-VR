package com.auroraar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AuroraGreen = Color(0xFF6EF3B0)
val AuroraViolet = Color(0xFF8B7BFF)
val AuroraTeal = Color(0xFF3DD9C0)
val NightBase = Color(0xFF05060B)
val NightSurface = Color(0xFF0E1119)
val NightSurfaceHigh = Color(0xFF161B26)
val TextPrimary = Color(0xFFF2F4F8)
val TextMuted = Color(0xFF98A2B3)
val WarmAmber = Color(0xFFF5C26B)

private val AuroraColorScheme = darkColorScheme(
    primary = AuroraGreen,
    onPrimary = Color(0xFF04231A),
    primaryContainer = Color(0xFF113A2A),
    onPrimaryContainer = AuroraGreen,
    secondary = AuroraViolet,
    onSecondary = Color(0xFF140B33),
    background = NightBase,
    onBackground = TextPrimary,
    surface = NightSurface,
    onSurface = TextPrimary,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = TextMuted,
    outline = Color(0xFF2A3140),
    error = Color(0xFFFF8A80),
)

private val AuroraTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AuroraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuroraColorScheme,
        typography = AuroraTypography,
        content = content,
    )
}
