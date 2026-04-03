package com.cyangem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CyanPrimary     = Color(0xFF00E5C8)
val CyanSecondary   = Color(0xFF0097A7)
val CyanTertiary    = Color(0xFF7C4DFF)
val SurfaceDark     = Color(0xFF0D1117)
val SurfaceCard     = Color(0xFF161B22)
val SurfaceElevated = Color(0xFF1E2530)
val OnSurface       = Color(0xFFE6EDF3)
val OnSurfaceMuted  = Color(0xFF8B949E)
val ErrorColor      = Color(0xFFFF6B6B)
val SuccessColor    = Color(0xFF3FB950)

private val DarkColors = darkColorScheme(
    primary          = CyanPrimary,
    onPrimary        = Color(0xFF003731),
    primaryContainer = Color(0xFF004D43),
    secondary        = CyanSecondary,
    onSecondary      = Color(0xFF00363D),
    tertiary         = CyanTertiary,
    background       = SurfaceDark,
    surface          = SurfaceCard,
    surfaceVariant   = SurfaceElevated,
    onBackground     = OnSurface,
    onSurface        = OnSurface,
    onSurfaceVariant = OnSurfaceMuted,
    error            = ErrorColor,
    outline          = Color(0xFF30363D)
)

@Composable
fun CyanGemTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
