package com.airdvr.tv.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.models.EpgProgram
import com.airdvr.tv.data.models.Recording
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.HomeViewModel
import com.airdvr.tv.ui.viewmodels.LiveChannelEntry
import com.airdvr.tv.ui.viewmodels.UpcomingEntry
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateLiveTV: () -> Unit,
    onNavigateWhereToWatch: () -> Unit,
    onNavigateSportsCalendar: () -> Unit,
    onNavigateRecordings: () -> Unit,
    onNavigateCustomChannels: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigatePlayer: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Row(modifier = Modifier.fillMaxSize().background(PlexBg)) {
        // Slim nav rail
        HomeNavRail(
            userInitial = uiState.userInitial,
            onHome = {},
            onWhereToWatch = onNavigateWhereToWatch,
            onSportsCalendar = onNavigateSportsCalendar,
            onRecordings = onNavigateRecordings,
            onCustomChannels = onNavigateCustomChannels,
            onLiveTV = onNavigateLiveTV,
            onSettings = onNavigateSettings,
            modifier = Modifier.width(56.dp).fillMaxHeight()
        )

        // Main content
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (uiState.isLoading) {
                ShimmerContent()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item {
                        HeroBanner(
                            channel = uiState.heroChannel,
                            program = uiState.heroProgram
                        )
                    }

                    if (uiState.liveNow.isNotEmpty()) {
                        item {
                            RowSection(title = "Live Now") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    items(uiState.liveNow) { entry ->
                                        LiveNowCard(entry = entry, onClick = onNavigateLiveTV)
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.recordings.isNotEmpty()) {
                        item {
                            RowSection(title = "Recordings") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    items(uiState.recordings) { recording ->
                                        RecordingPosterCard(
                                            recording = recording,
                                            onClick = { onNavigatePlayer(recording.id ?: "") }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.upcoming.isNotEmpty()) {
                        item {
                            RowSection(title = "Upcoming") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    items(uiState.upcoming) { entry ->
                                        UpcomingCard(entry = entry, onClick = onNavigateLiveTV)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Hero banner ────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroBanner(channel: Channel?, program: EpgProgram?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        // Background — solid card color, no poster yet
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PlexSurface)
        )
        // Bottom-up gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.55f to PlexBg.copy(alpha = 0.40f),
                            1.0f to PlexBg
                        )
                    )
                )
        )
        // Text overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, end = 32.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (channel != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(LiveRedDot))
                    Text(
                        "${channel.guideNumber ?: ""} ${channel.guideName ?: ""}",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PlexTextSecondary
                    )
                }
            }
            Text(
                program?.title ?: channel?.guideName ?: "AirDVR",
                fontSize = 36.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (program?.summary?.isNotBlank() == true) {
                Text(
                    program.summary,
                    fontSize = 14.sp, color = PlexTextSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─── Row section ────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RowSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        content()
    }
}

// ─── Live Now card ──────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LiveNowCard(entry: LiveChannelEntry, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "lns")
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .height(120.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PlexCard,
            focusedContainerColor = PlexCard
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Logo abbrev
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(PlexSurface).border(1.dp, PlexBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (entry.channel.guideName ?: "").take(2).uppercase(),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary
                    )
                }
                Text(
                    "${entry.channel.guideNumber ?: ""} ${entry.channel.guideName ?: ""}",
                    fontSize = 11.sp, color = PlexTextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                entry.program?.title ?: "No data",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.program != null) {
                    val s = timeFormat.format(Date(entry.program.startEpochSec * 1000))
                    val e = timeFormat.format(Date(entry.program.endEpochSec * 1000))
                    Text("$s - $e", fontSize = 10.sp, color = PlexTextTertiary)
                }
                if (!entry.program?.category.isNullOrBlank()) {
                    Box(
                        Modifier.background(PlexSurface, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(entry.program?.category ?: "", fontSize = 9.sp, color = PlexTextSecondary)
                    }
                }
            }
        }
    }
}

// ─── Recording poster card (2:3) ────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RecordingPosterCard(recording: Recording, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "rps")
    val isUnwatched = recording.resumePositionSec == 0

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(140.dp)
            .height(210.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PlexSurface,
            focusedContainerColor = PlexSurface
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Bottom gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth().fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )
            // Title
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    recording.title ?: "Untitled",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            if (isUnwatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd).padding(8.dp)
                        .size(8.dp).clip(CircleShape).background(PlexTextPrimary)
                )
            }
        }
    }
}

// ─── Upcoming card ──────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UpcomingCard(entry: UpcomingEntry, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "ucc")
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .height(110.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PlexCard,
            focusedContainerColor = PlexCard
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                entry.program.title ?: "Untitled",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            val s = timeFormat.format(Date(entry.program.startEpochSec * 1000))
            Text(
                "$s · ${entry.channel.guideNumber ?: ""} ${entry.channel.guideName ?: ""}",
                fontSize = 11.sp, color = PlexTextTertiary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Nav Rail ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeNavRail(
    userInitial: String,
    onHome: () -> Unit,
    onWhereToWatch: () -> Unit,
    onSportsCalendar: () -> Unit,
    onRecordings: () -> Unit,
    onCustomChannels: () -> Unit,
    onLiveTV: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Profile circle
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(PlexCard).border(1.dp, PlexBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(userInitial, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        DividerLine()
        Spacer(Modifier.height(8.dp))

        NavRailIcon(Icons.Filled.Home, "Home", onHome, isActive = true)
        NavRailIcon(Icons.Filled.Search, "Where to Watch", onWhereToWatch, isActive = false)
        NavRailIcon(Icons.Filled.CalendarMonth, "Sports", onSportsCalendar, isActive = false)
        NavRailIcon(Icons.Filled.VideoLibrary, "Recordings", onRecordings, isActive = false)
        NavRailIcon(Icons.AutoMirrored.Filled.PlaylistPlay, "Custom", onCustomChannels, isActive = false)

        Spacer(Modifier.height(8.dp))
        DividerLine()
        Spacer(Modifier.height(8.dp))

        NavRailIcon(Icons.Filled.LiveTv, "Live TV", onLiveTV, isActive = false)

        Spacer(Modifier.weight(1f))

        NavRailIcon(Icons.Filled.Settings, "Settings", onSettings, isActive = false)
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(1.dp)
            .background(PlexBorder)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavRailIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isActive: Boolean
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .size(width = 48.dp, height = 40.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon, label,
                tint = when {
                    focused || isActive -> PlexAccent
                    else -> PlexTextTertiary
                },
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ─── Shimmer ────────────────────────────────────────────────────────────────

@Composable
private fun ShimmerContent() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "shimmerAlpha"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth().height(280.dp)
                .background(PlexCard.copy(alpha = alpha))
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .width(120.dp).height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PlexCard.copy(alpha = alpha))
        )
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .width(220.dp).height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PlexCard.copy(alpha = alpha))
                )
            }
        }
    }
}
