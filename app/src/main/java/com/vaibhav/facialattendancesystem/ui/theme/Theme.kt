package com.vaibhav.facialattendancesystem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary          = PrimaryCyan,
    secondary        = SuccessGreen,
    tertiary         = WarningAmber,
    background       = DarkBackground,
    surface          = SurfaceDark,
    surfaceVariant   = SurfaceCard,
    onPrimary        = Color.White,
    onSecondary      = Color.White,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error            = ErrorRose,
    onError          = Color.White,
    outline          = Color(0x1AFFFFFF),
    outlineVariant   = Color(0x14FFFFFF)
)

private val LightColorScheme = lightColorScheme(
    primary          = PrimaryCyan,
    secondary        = SuccessGreen,
    tertiary         = WarningAmber,
    background       = LightBackground,
    surface          = SurfaceLight,
    surfaceVariant   = LightCard,
    onPrimary        = Color.White,
    onSecondary      = Color.White,
    onBackground     = TextPrimaryLight,
    onSurface        = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    error            = ErrorRose,
    onError          = Color.White,
    outline          = Color(0x3864748B),
    outlineVariant   = Color(0x2064748B)
)

@Composable
fun FacialAttendanceSystemTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}