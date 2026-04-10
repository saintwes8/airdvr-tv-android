package com.airdvr.tv.ui.screens

import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.models.EpgProgram
import com.airdvr.tv.data.models.Recording
import com.airdvr.tv.data.repository.ChannelLogoRepository
import com.airdvr.tv.ui.components.rememberBackdropUrl
import com.airdvr.tv.ui.components.rememberPosterUrl
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.HomeViewModel
import com.airdvr.tv.ui.viewmodels.LiveChannelEntry
import com.airdvr.tv.ui.viewmodels.UpcomingEntry
import kotlinx.coroutines.delay
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
                        if (uiState.heroEntries.isNotEmpty()) {
                            HeroCycling(entries = uiState.heroEntries)
                        } else {
                            HeroBanner(
                                channel = uiState.heroChannel,
                                program = uiState.heroProgram
                            )
                        }
                    }

                    if (uiState.liveNow.isNotEmpty()) {
                        item {
                            RowSection(title = "Live Now") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp)
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
                                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp)
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
                                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp)
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
    val backdropUrl = rememberBackdropUrl(program?.title)
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        // Background — backdrop image if available, otherwise solid card color
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().background(PlexSurface),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PlexSurface)
            )
        }
        // Bottom-up gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.45f to PlexBg.copy(alpha = 0.50f),
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (program?.summary?.isNotBlank() == true) {
                Text(
                    program.summary,
                    fontSize = 14.sp, color = PlexTextSecondary,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
            if (program != null) {
                val parts = mutableListOf<String>()
                program.category?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
                val s = timeFormat.format(Date(program.startEpochSec * 1000))
                val e = timeFormat.format(Date(program.endEpochSec * 1000))
                parts.add("$s - $e")
                Text(
                    parts.joinToString("  \u00B7  "),
                    fontSize = 12.sp, color = PlexTextSecondary
                )
            }
        }
    }
}

// ─── Hero cycling ──────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroCycling(entries: List<LiveChannelEntry>) {
    var heroIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(entries.size) {
        if (entries.size <= 1) return@LaunchedEffect
        while (true) {
            delay(10000L)
            heroIndex = (heroIndex + 1) % entries.size
        }
    }
    val current = entries.getOrNull(heroIndex) ?: return
    Crossfade(
        targetState = heroIndex,
        animationSpec = tween(500),
        label = "heroCycle"
    ) { idx ->
        val entry = entries.getOrNull(idx) ?: return@Crossfade
        HeroBanner(channel = entry.channel, program = entry.program)
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
    ChannelCard(
        channel = entry.channel,
        program = entry.program,
        onClick = onClick,
        label = "lns"
    )
}

/** Shared card layout for Live Now and Upcoming rows */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelCard(
    channel: Channel,
    program: EpgProgram?,
    onClick: () -> Unit,
    label: String
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = label)
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val backdropUrl = rememberBackdropUrl(program?.title)
    val logoInfo = remember(channel.guideName) { ChannelLogoRepository.getLogoInfo(channel.guideName ?: "") }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .height(130.dp)
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
        Box(modifier = Modifier.fillMaxSize()) {
            // Backdrop background
            if (!backdropUrl.isNullOrBlank()) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )
            }
            // Channel logo — top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(PlexSurface.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                if (!logoInfo?.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = logoInfo!!.logoUrl,
                        contentDescription = channel.guideName,
                        modifier = Modifier.size(16.dp).clip(CircleShape),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        (channel.guideName ?: "").take(2).uppercase(),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary
                    )
                }
            }
            // Content overlay at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    program?.title ?: "",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (program != null) {
                    val s = timeFormat.format(Date(program.startEpochSec * 1000))
                    val e = timeFormat.format(Date(program.endEpochSec * 1000))
                    Text("$s - $e", fontSize = 12.sp, color = PlexTextSecondary)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!program?.category.isNullOrBlank()) {
                        Box(
                            Modifier.background(PlexSurface.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(program?.category ?: "", fontSize = 12.sp, color = PlexTextSecondary)
                        }
                    }
                    Text(
                        "${channel.guideNumber ?: ""} ${channel.guideName ?: ""}",
                        fontSize = 12.sp, color = PlexTextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
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
                    recording.title ?: "",
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
    ChannelCard(
        channel = entry.channel,
        program = entry.program,
        onClick = onClick,
        label = "ucc"
    )
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
