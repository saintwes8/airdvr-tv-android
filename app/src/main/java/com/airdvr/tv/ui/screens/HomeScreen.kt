package com.airdvr.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import com.airdvr.tv.ui.components.ChannelCard
import com.airdvr.tv.ui.components.RecordingCard
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.HomeViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateLiveTV: () -> Unit,
    onNavigateGuide: () -> Unit,
    onNavigateMultiView: () -> Unit,
    onNavigateRecordings: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigatePlayer: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(AirDVRNavy)
    ) {
        // Left navigation rail
        NavigationRail(
            modifier = Modifier
                .width(80.dp)
                .fillMaxHeight()
                .background(AirDVRCard)
        )

        // Main content
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AirDVRBlue)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 24.dp, top = 24.dp, end = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    item {
                        // Header
                        Text(
                            text = "AirDVR",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = AirDVRBlue,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Error banner
                    if (uiState.error != null) {
                        item {
                            Text(
                                text = uiState.error!!,
                                color = Color.Red,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            )
                        }
                    }

                    // Continue Watching row
                    if (uiState.continueWatching.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Continue Watching")
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.continueWatching) { recording ->
                                    RecordingCard(
                                        recording = recording,
                                        onClick = { onNavigatePlayer(recording.id) }
                                    )
                                }
                            }
                        }
                    }

                    // Live Now row
                    if (uiState.liveNow.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Live Now")
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.liveNow) { channel ->
                                    ChannelCard(
                                        channel = channel,
                                        isSelected = false,
                                        onClick = { onNavigateLiveTV() }
                                    )
                                }
                            }
                        }
                    }

                    // Recent Recordings row
                    if (uiState.recentRecordings.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Recent Recordings")
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.recentRecordings) { recording ->
                                    RecordingCard(
                                        recording = recording,
                                        onClick = { onNavigatePlayer(recording.id) }
                                    )
                                }
                            }
                        }
                    }

                    // Recommended placeholder
                    item {
                        SectionHeader(title = "Recommended")
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(AirDVRCard, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Recommendations coming soon",
                                color = AirDVRTextSecondary,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    // Overlay the nav rail content on top (separate pass to ensure focus works)
    Box(modifier = Modifier.fillMaxSize()) {
        NavRailContent(
            onNavigateLiveTV = onNavigateLiveTV,
            onNavigateGuide = onNavigateGuide,
            onNavigateMultiView = onNavigateMultiView,
            onNavigateRecordings = onNavigateRecordings,
            onNavigateSettings = onNavigateSettings,
            modifier = Modifier
                .width(80.dp)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = AirDVRTextPrimary
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NavRailContent(
    onNavigateLiveTV: () -> Unit,
    onNavigateGuide: () -> Unit,
    onNavigateMultiView: () -> Unit,
    onNavigateRecordings: () -> Unit,
    onNavigateSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    data class NavItem(val icon: ImageVector, val label: String, val action: () -> Unit)

    val items = listOf(
        NavItem(Icons.Filled.Home, "Home") {},
        NavItem(Icons.Filled.LiveTv, "Live TV", onNavigateLiveTV),
        NavItem(Icons.Filled.CalendarMonth, "Guide", onNavigateGuide),
        NavItem(Icons.Filled.GridView, "Multi", onNavigateMultiView),
        NavItem(Icons.Filled.VideoLibrary, "Recordings", onNavigateRecordings),
        NavItem(Icons.Filled.Settings, "Settings", onNavigateSettings)
    )

    Column(
        modifier = modifier
            .background(AirDVRCard)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            NavRailItem(icon = item.icon, label = item.label, onClick = item.action)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavRailItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(64.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isFocused) AirDVRBlue.copy(alpha = 0.3f) else Color.Transparent,
            focusedContainerColor = AirDVRBlue.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isFocused) AirDVRBlue else AirDVRTextSecondary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = if (isFocused) AirDVRBlue else AirDVRTextSecondary
            )
        }
    }
}
