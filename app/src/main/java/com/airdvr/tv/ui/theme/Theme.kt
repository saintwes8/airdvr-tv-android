package com.airdvr.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val AirDVRNavy = Color(0xFF0A0E17)
val AirDVRCard = Color(0xFF111827)
val AirDVRBlue = Color(0xFF1A6EBD)
val AirDVROrange = Color(0xFFE85D26)
val AirDVRTextPrimary = Color(0xFFFFFFFF)
val AirDVRTextSecondary = Color(0xFF9CA3AF)
val AirDVRFocusRing = Color(0xFF2E90FA)
val AirDVRGreen = Color(0xFF22C55E)
val AirDVRRed = Color(0xFFEF4444)
val AirDVRPurple = Color(0xFFA855F7)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AirDVRTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = AirDVRBlue,
        secondary = AirDVROrange,
        background = AirDVRNavy,
        surface = AirDVRCard,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = AirDVRTextPrimary,
        onSurface = AirDVRTextPrimary,
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
