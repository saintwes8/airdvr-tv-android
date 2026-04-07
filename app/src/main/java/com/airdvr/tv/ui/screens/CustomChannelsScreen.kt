package com.airdvr.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.airdvr.tv.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CustomChannelsScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(PlexBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = "Custom Channels",
                tint = PlexBorder,
                modifier = Modifier.size(64.dp)
            )
            Text(
                "Custom Channels",
                fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                color = PlexTextPrimary, textAlign = TextAlign.Center
            )
            Text(
                "Coming soon — create channels from recordings and IPTV sources",
                fontSize = 14.sp, color = PlexTextTertiary, textAlign = TextAlign.Center
            )
        }
    }
}
