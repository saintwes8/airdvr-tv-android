package com.airdvr.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// ── Pure-black palette (background uses #000000 across all screens) ──
val PlexBg = Color(0xFF000000)
val PlexSurface = Color(0xFF0A0A0A)
val PlexCard = Color(0xFF141414)
val PlexBorder = Color(0xFF2A2A2A)

val PlexTextPrimary = Color(0xFFE6EDF3)
val PlexTextSecondary = Color(0xFF8B949E)
val PlexTextTertiary = Color(0xFF484F58)

val PlexAccent = Color(0xFF1A6EBD) // Nav rail focused icons ONLY
val LiveRedDot = Color(0xFFEF4444)  // LIVE indicator dot ONLY

// ── Legacy exports (PlayerScreen compat) ────────────────────────────────
val AirDVRNavy = PlexBg
val AirDVRBlue = PlexTextPrimary
val AirDVROrange = PlexTextPrimary
val AirDVRCard = PlexCard
val AirDVRTextPrimary = PlexTextPrimary
val AirDVRFocusRing = PlexTextPrimary

// ── Theme wrapper ───────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AirDVRTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = PlexTextPrimary,
        onPrimary = PlexBg,
        secondary = PlexTextSecondary,
        onSecondary = PlexBg,
        background = PlexBg,
        onBackground = PlexTextPrimary,
        surface = PlexSurface,
        onSurface = PlexTextPrimary,
        surfaceVariant = PlexCard,
        onSurfaceVariant = PlexTextSecondary,
        border = PlexBorder
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
