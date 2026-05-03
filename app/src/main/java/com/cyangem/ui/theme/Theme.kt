package com.cyangem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// =============================================================================
// HC-015 — Light theme palette per approved Product Design.
//
//   - White / near-white background
//   - White cards, soft pale-blue elevated sections
//   - Dark charcoal text
//   - Cyan / blue primary accent
//   - Purple accent for Gemini Live areas (GeminiPurple)
//   - Amber / orange for warnings and Not Available
//   - Green ONLY for Connected / Passed status pills (small accents)
//   - Subtle pale-gray borders, no heavy shadow
//
// Same token names as before so other screens keep compiling, but the values
// are remapped for the light theme. Two new tokens added: BackgroundLight and
// WarningAmber (and GeminiPurple as a clarity alias for CyanTertiary).
// =============================================================================

val BackgroundLight = Color(0xFFF7F9FC)  // very pale blue-white app background
val SurfaceCard     = Color(0xFFFFFFFF)  // pure white card
val SurfaceElevated = Color(0xFFEFF3F8)  // slightly tinted gray-blue for sections
val SurfaceTint     = Color(0xFFE8F3FF)  // pale blue accent for selected chips, etc.
val OnSurface       = Color(0xFF1A2332)  // dark charcoal — primary text
val OnSurfaceMuted  = Color(0xFF5C6B7A)  // mid-gray — secondary text
val BorderSubtle    = Color(0xFFE0E6EE)  // very pale gray — card borders, dividers

val CyanPrimary     = Color(0xFF0891B2)  // primary brand cyan (deeper for light bg)
val CyanSecondary   = Color(0xFF0EA5E9)  // brighter blue for secondary accents
val CyanTertiary    = Color(0xFF7C4DFF)  // purple — Gemini Live accent
val GeminiPurple    = CyanTertiary       // alias for clarity at call sites

val WarningAmber    = Color(0xFFE65100)  // dark amber — warnings, Not Available
val SuccessColor    = Color(0xFF16A34A)  // green — Connected, Passed (small pills only)
val ErrorColor      = Color(0xFFDC2626)  // red — errors

private val LightColors = lightColorScheme(
    primary           = CyanPrimary,
    onPrimary         = Color.White,
    primaryContainer  = SurfaceTint,
    onPrimaryContainer = OnSurface,
    secondary         = CyanSecondary,
    onSecondary       = Color.White,
    tertiary          = CyanTertiary,
    onTertiary        = Color.White,
    background        = BackgroundLight,
    onBackground      = OnSurface,
    surface           = SurfaceCard,
    onSurface         = OnSurface,
    surfaceVariant    = SurfaceElevated,
    onSurfaceVariant  = OnSurfaceMuted,
    error             = ErrorColor,
    onError           = Color.White,
    outline           = BorderSubtle
)

@Composable
fun CyanGemTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}

val SurfaceCardSubtle = Color(0xFFF1F5FA)
val WarningAmberSoft = Color(0xFFB45309)
