package com.airdvr.tv.ui.screens

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.airdvr.tv.AirDVRApp
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.models.EpgProgram
import com.airdvr.tv.ui.components.LoadingSpinner
import com.airdvr.tv.ui.components.rememberPosterUrl
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.LiveTVViewModel
import com.airdvr.tv.ui.viewmodels.MultiViewNavDirection
import com.airdvr.tv.ui.viewmodels.PaneState
import com.airdvr.tv.ui.viewmodels.ScreenMode
import com.airdvr.tv.util.Constants
import java.text.SimpleDateFormat
import java.util.*

// ─── Layout constants ──────────────────────────────────────────────────────
private const val SLOT_SEC = 1800L
private const val INFO_PANEL_DP = 280
private const val CH_COL_DP = 60
private const val ROW_DP = 56

@OptIn(UnstableApi::class)
@Composable
fun LiveTVScreen(
    onNavigateHome: () -> Unit,
    onNavigateWhereToWatch: () -> Unit = {},
    onNavigateSportsCalendar: () -> Unit = {},
    onNavigateRecordings: () -> Unit = {},
    onNavigateCustomChannels: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    viewModel: LiveTVViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokenManager = remember { AirDVRApp.instance.tokenManager }
    val focusRequester = remember { FocusRequester() }

    // ── ExoPlayer setup ────────────────────────────────────────────────────
    val loadControl = remember {
        DefaultLoadControl.Builder().setBufferDurationsMs(
            Constants.MIN_BUFFER_MS, Constants.MAX_BUFFER_MS,
            Constants.BUFFER_FOR_PLAYBACK_MS, Constants.BUFFER_FOR_PLAYBACK_MS
        ).build()
    }
    val exoPlayers = remember {
        List(4) { ExoPlayer.Builder(context).setLoadControl(loadControl).build().also { it.playWhenReady = true } }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) viewModel.onPlayerReady() }
            override fun onPlayerError(error: PlaybackException) { viewModel.onPlayerError(error.message ?: "Playback error") }
        }
        exoPlayers[0].addListener(listener)
        onDispose { exoPlayers.forEach { it.release() } }
    }

    fun loadHls(url: String, player: ExoPlayer) {
        val token = tokenManager.getAccessToken()
        val headers = if (token != null) mapOf("Authorization" to "Bearer $token") else emptyMap()
        val factory = DefaultHttpDataSource.Factory().apply { if (headers.isNotEmpty()) setDefaultRequestProperties(headers) }
        val src = HlsMediaSource.Factory(factory).setAllowChunklessPreparation(true).createMediaSource(MediaItem.fromUri(url))
        player.stop(); player.setMediaSource(src); player.prepare(); player.playWhenReady = true
    }

    // Main player
    LaunchedEffect(uiState.streamUrl, uiState.mode) {
        if (uiState.mode == ScreenMode.MULTIVIEW) return@LaunchedEffect
        val url = uiState.streamUrl ?: return@LaunchedEffect
        if (exoPlayers[0].currentMediaItem?.localConfiguration?.uri?.toString() != url) loadHls(url, exoPlayers[0])
    }
    // MultiView pane streams
    LaunchedEffect(uiState.multiViewPanes, uiState.mode) {
        if (uiState.mode != ScreenMode.MULTIVIEW) return@LaunchedEffect
        uiState.multiViewPanes.forEachIndexed { i, pane ->
            if (i < exoPlayers.size) {
                val url = pane.streamUrl
                if (url != null && exoPlayers[i].currentMediaItem?.localConfiguration?.uri?.toString() != url)
                    loadHls(url, exoPlayers[i])
                if (url == null) exoPlayers[i].stop()
            }
        }
    }
    // Volume
    LaunchedEffect(uiState.activePaneIndex, uiState.mode, uiState.isMuted) {
        if (uiState.mode == ScreenMode.MULTIVIEW) {
            exoPlayers.forEachIndexed { i, p -> p.volume = if (i == uiState.activePaneIndex && !uiState.isMuted) 1f else 0f }
        } else {
            exoPlayers[0].volume = if (uiState.isMuted) 0f else 1f
        }
    }
    // Restore main player leaving multiview
    LaunchedEffect(uiState.mode) {
        if (uiState.mode != ScreenMode.MULTIVIEW) {
            val url = uiState.streamUrl ?: return@LaunchedEffect
            if (exoPlayers[0].currentMediaItem?.localConfiguration?.uri?.toString() != url) loadHls(url, exoPlayers[0])
            else exoPlayers[0].play()
            (1 until 4).forEach { exoPlayers[it].stop() }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize().background(PlexBg)
            .focusRequester(focusRequester).focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                val code = keyEvent.nativeKeyEvent.keyCode
                handleKey(code, uiState, viewModel, onNavigateHome)
            }
    ) {
        if (uiState.isLoading) {
            LoadingSpinner(message = "Loading channels...", modifier = Modifier.fillMaxSize())
            return@Box
        }

        when (uiState.mode) {
            ScreenMode.GUIDE -> GuideLayout(uiState, exoPlayers[0], viewModel)
            ScreenMode.FULLSCREEN -> FullscreenLayout(uiState, exoPlayers[0], viewModel)
            ScreenMode.MULTIVIEW -> MultiViewLayout(uiState, exoPlayers, viewModel)
        }

        // Toast
        uiState.toastMessage?.let { msg ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(msg, color = PlexTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Slim nav rail (only over fullscreen, slides in from left)
        PlexNavRail(
            visible = uiState.navRailVisible,
            userInitial = uiState.userInitial,
            onHome = onNavigateHome,
            onWhereToWatch = onNavigateWhereToWatch,
            onSportsCalendar = onNavigateSportsCalendar,
            onRecordings = onNavigateRecordings,
            onCustomChannels = onNavigateCustomChannels,
            onLiveTV = { viewModel.hideNavRail() },
            onSettings = onNavigateSettings,
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// KEY HANDLER
// ═══════════════════════════════════════════════════════════════════════════

private fun handleKey(
    code: Int,
    uiState: com.airdvr.tv.ui.viewmodels.LiveTVUiState,
    vm: LiveTVViewModel,
    onHome: () -> Unit
): Boolean {
    return when (uiState.mode) {
        ScreenMode.GUIDE -> handleGuideKey(code, uiState, vm)
        ScreenMode.FULLSCREEN -> handleFullscreenKey(code, uiState, vm, onHome)
        ScreenMode.MULTIVIEW -> handleMultiViewKey(code, uiState, vm)
    }
}

private fun handleGuideKey(
    code: Int,
    uiState: com.airdvr.tv.ui.viewmodels.LiveTVUiState,
    vm: LiveTVViewModel
): Boolean {
    if (uiState.categoriesFocused) {
        return when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { vm.categoryNavigateLeft(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { vm.categoryNavigateRight(); true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                vm.unfocusCategories(); true
            }
            KeyEvent.KEYCODE_BACK -> { vm.unfocusCategories(); vm.enterFullScreen(); true }
            else -> false
        }
    }
    return when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> {
            val moved = vm.navigateUp()
            if (!moved) vm.focusCategories()
            true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> { vm.navigateDown(); true }
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            // If at leftmost, switch to fullscreen
            val moved = vm.navigateLeft()
            if (!moved) vm.enterFullScreen()
            true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { vm.navigateRight(); true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { vm.selectFocused(); true }
        KeyEvent.KEYCODE_CHANNEL_UP -> { vm.channelUp(); true }
        KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.channelDown(); true }
        KeyEvent.KEYCODE_BACK -> { vm.enterFullScreen(); true }
        else -> false
    }
}

private fun handleFullscreenKey(
    code: Int,
    uiState: com.airdvr.tv.ui.viewmodels.LiveTVUiState,
    vm: LiveTVViewModel,
    onHome: () -> Unit
): Boolean {
    // Nav rail visible — handle key events for the rail (BACK exits)
    if (uiState.navRailVisible) {
        return when (code) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> { vm.hideNavRail(); true }
            KeyEvent.KEYCODE_BACK -> { vm.hideNavRail(); true }
            else -> false
        }
    }
    // Any D-pad press in fullscreen pings the now playing bar
    when (code) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> vm.pingNowPlayingBar()
    }
    // Fullscreen overlay (action buttons) visible
    if (uiState.showFullscreenOverlay) {
        return when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { vm.overlayNavigateLeft(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { vm.overlayNavigateRight(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { vm.hideFullscreenOverlay(); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { vm.activateOverlayButton(); true }
            KeyEvent.KEYCODE_BACK -> { vm.hideFullscreenOverlay(); true }
            else -> false
        }
    }
    return when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> { vm.showFullscreenOverlay(); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { vm.enterGuide(); true }
        KeyEvent.KEYCODE_DPAD_LEFT -> { vm.showNavRail(); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { vm.showFullscreenOverlay(); true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { vm.showFullscreenOverlay(); true }
        KeyEvent.KEYCODE_CHANNEL_UP -> { vm.channelUp(); true }
        KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.channelDown(); true }
        KeyEvent.KEYCODE_BACK -> { vm.enterGuide(); true }
        else -> false
    }
}

private fun handleMultiViewKey(
    code: Int,
    uiState: com.airdvr.tv.ui.viewmodels.LiveTVUiState,
    vm: LiveTVViewModel
): Boolean {
    if (uiState.guideOverlayVisible) {
        return when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> { vm.navigateUp(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { vm.navigateDown(); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { vm.navigateLeft(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { vm.navigateRight(); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { vm.tuneActivePaneToFocused(); true }
            KeyEvent.KEYCODE_BACK -> { vm.exitMultiView(); true }
            else -> false
        }
    }
    return when (code) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { vm.switchActivePane(MultiViewNavDirection.LEFT); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { vm.switchActivePane(MultiViewNavDirection.RIGHT); true }
        KeyEvent.KEYCODE_DPAD_UP -> { vm.switchActivePane(MultiViewNavDirection.UP); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { vm.switchActivePane(MultiViewNavDirection.DOWN); true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { vm.addPaneForPick(); true }
        KeyEvent.KEYCODE_CHANNEL_UP -> { vm.channelUp(); true }
        KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.channelDown(); true }
        KeyEvent.KEYCODE_BACK -> { vm.exitMultiView(); true }
        else -> false
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// GUIDE LAYOUT
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
private fun GuideLayout(
    uiState: com.airdvr.tv.ui.viewmodels.LiveTVUiState,
    exoPlayer: ExoPlayer,
    viewModel: LiveTVViewModel
) {
    val now = remember { System.currentTimeMillis() / 1000 }

    Box(modifier = Modifier.fillMaxSize()) {
        // LAYER 0 — Video background
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM } },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )
        // Subtle dim
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        // Left gradient over the info panel area
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Black.copy(alpha = 0.85f),
                        0.30f to Color.Black.copy(alpha = 0.30f),
                        0.45f to Color.Transparent
                    )
                )
            )
        )

        // Tuning spinner
        if (uiState.isTuning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PlexTextPrimary, modifier = Modifier.size(40.dp))
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // LAYER 1 — Left info panel (top half is info, bottom half empty)
            LeftInfoPanel(
                focusedChannel = uiState.focusedChannel,
                focusedProgram = uiState.focusedProgram,
                modifier = Modifier.width(INFO_PANEL_DP.dp).fillMaxHeight()
            )

            // LAYER 2 — Guide grid
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                CategoryTabs(
                    categories = uiState.categories,
                    selectedIndex = uiState.selectedCategoryIndex,
                    isFocused = uiState.categoriesFocused,
                    modifier = Modifier.fillMaxWidth()
                )
                GuideGrid(uiState = uiState, now = now, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }

        // NOW PLAYING badge top-right
        if (uiState.currentChannel != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.60f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(LiveRedDot))
                    Text(
                        "${uiState.currentChannel.guideNumber ?: ""} ${uiState.currentChannel.guideName ?: ""}",
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary
                    )
                }
            }
        }
    }
}

// ─── Left Info Panel (top half info, bottom half empty) ────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LeftInfoPanel(
    focusedChannel: Channel?,
    focusedProgram: EpgProgram?,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val posterUrl = rememberPosterUrl(focusedProgram?.title)

    Column(modifier = modifier) {
        // TOP HALF
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .padding(start = 24.dp, top = 32.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (focusedChannel != null) {
                // Poster (80x120) — falls back to channel logo abbrev when missing
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 120.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(PlexCard)
                        .border(1.dp, PlexBorder, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = focusedProgram?.title,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                        )
                    } else {
                        Text(
                            (focusedChannel.guideName ?: "").take(3).uppercase(),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = PlexTextPrimary, textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Channel number + name
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        focusedChannel.guideNumber ?: "",
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary
                    )
                    Text(
                        focusedChannel.guideName ?: "",
                        fontSize = 16.sp, color = PlexTextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }

                if (focusedProgram != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        focusedProgram.title ?: "Unknown",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    // Episode info — own line, 13sp, 1 line
                    val episode = focusedProgram.episodeTitle?.takeIf { it.isNotBlank() }
                    if (episode != null) {
                        Text(
                            episode,
                            fontSize = 13.sp, color = PlexTextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Category · time — 12sp, 1 line
                    val catTimeParts = mutableListOf<String>()
                    focusedProgram.category?.let { if (it.isNotBlank()) catTimeParts.add(it) }
                    val s = timeFormat.format(Date(focusedProgram.startEpochSec * 1000))
                    val e = timeFormat.format(Date(focusedProgram.endEpochSec * 1000))
                    catTimeParts.add("$s - $e")
                    if (focusedProgram.isNew) catTimeParts.add("NEW")
                    Text(
                        catTimeParts.joinToString("  ·  "),
                        fontSize = 12.sp, color = PlexTextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    // Description — 12sp, 5 lines
                    if (!focusedProgram.summary.isNullOrBlank()) {
                        Text(
                            focusedProgram.summary,
                            fontSize = 12.sp, color = PlexTextSecondary,
                            maxLines = 5, overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text("No program data", fontSize = 13.sp, color = PlexTextTertiary)
                }
            }
        }

        // BOTTOM HALF — empty / transparent so video gradient shows
        Box(modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

// ─── Category Tabs ─────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryTabs(
    categories: List<String>,
    selectedIndex: Int,
    isFocused: Boolean = false,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(categories) { index, category ->
            val isSelected = index == selectedIndex
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    category, fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSelected && isFocused -> PlexAccent
                        isSelected -> PlexTextPrimary
                        else -> PlexTextTertiary
                    }
                )
                if (isSelected) {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier.width(28.dp).height(2.dp)
                            .background(if (isFocused) PlexAccent else PlexTextPrimary, RoundedCornerShape(1.dp))
                    )
                }
            }
        }
    }
}

// ─── Guide Grid ────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GuideGrid(
    uiState: com.airdvr.tv.ui.viewmodels.LiveTVUiState,
    now: Long,
    modifier: Modifier = Modifier
) {
    val filtered = uiState.filteredChannels
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.focusedRow) {
        if (uiState.focusedRow in filtered.indices) listState.animateScrollToItem(uiState.focusedRow.coerceAtLeast(0))
    }

    if (filtered.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "No channels in this category",
                fontSize = 14.sp, color = PlexTextTertiary
            )
        }
        return
    }

    Column(modifier = modifier) {
        TimeHeader(
            timeWindowStart = uiState.timeWindowStartEpoch,
            visibleDurationSec = uiState.visibleDurationSec,
            now = now, timeFormat = timeFormat
        )
        // Box wraps LazyColumn so the NOW indicator line can be overlaid
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(filtered) { rowIndex, channel ->
                    GuideRow(
                        channel = channel,
                        programs = uiState.programsByChannel[channel.guideNumber ?: ""] ?: emptyList(),
                        isFocusedRow = rowIndex == uiState.focusedRow,
                        isPlayingChannel = channel.guideNumber == uiState.currentChannel?.guideNumber,
                        focusTimeEpoch = uiState.focusTimeEpoch,
                        timeWindowStart = uiState.timeWindowStartEpoch,
                        visibleDurationSec = uiState.visibleDurationSec,
                        now = now, timeFormat = timeFormat
                    )
                }
            }
            // NOW indicator: vertical line at actual current time, offset by channel column width
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val totalW = maxWidth - CH_COL_DP.dp
                val nowFrac = ((now - uiState.timeWindowStartEpoch).toFloat()
                    / uiState.visibleDurationSec).coerceIn(0f, 1f)
                val nowOffset = CH_COL_DP.dp + totalW * nowFrac
                Box(
                    modifier = Modifier
                        .offset(x = nowOffset)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(LiveRedDot)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TimeHeader(
    timeWindowStart: Long, visibleDurationSec: Long,
    now: Long, timeFormat: SimpleDateFormat
) {
    Row(modifier = Modifier.fillMaxWidth().height(28.dp).padding(bottom = 2.dp)) {
        Spacer(Modifier.width(CH_COL_DP.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val totalW = maxWidth
            val slotCount = (visibleDurationSec / SLOT_SEC).toInt()
            val slotW = totalW / slotCount
            Row(Modifier.fillMaxSize()) {
                for (i in 0 until slotCount) {
                    val slotStart = timeWindowStart + i * SLOT_SEC
                    Box(Modifier.width(slotW).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        Text(
                            timeFormat.format(Date(slotStart * 1000)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = PlexTextTertiary,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GuideRow(
    channel: Channel, programs: List<EpgProgram>,
    isFocusedRow: Boolean, isPlayingChannel: Boolean,
    focusTimeEpoch: Long, timeWindowStart: Long, visibleDurationSec: Long,
    now: Long, timeFormat: SimpleDateFormat
) {
    Row(modifier = Modifier.fillMaxWidth().height(ROW_DP.dp)) {
        // Channel label
        ChannelLabel(channel, isFocusedRow, isPlayingChannel, Modifier.width(CH_COL_DP.dp).fillMaxHeight())

        // Program cells
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val totalW = maxWidth.value
            val windowEnd = timeWindowStart + visibleDurationSec
            val sorted = programs.sortedBy { it.startEpochSec }
            // Trim past shows: only programs that end after NOW
            val visible = sorted.filter { it.endEpochSec > now && it.startEpochSec < windowEnd }

            if (visible.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(1.dp)
                        .background(PlexCard.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .border(0.5.dp, PlexBorder, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.CenterStart
                ) { Text("No data", color = PlexTextTertiary, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp)) }
            } else {
                visible.forEach { prog ->
                    // Clip start to NOW so past portion is hidden
                    val cs = maxOf(prog.startEpochSec, timeWindowStart)
                    val ce = minOf(prog.endEpochSec, windowEnd)
                    val xOff = ((cs - timeWindowStart).toFloat() / visibleDurationSec * totalW).dp
                    val cellW = ((ce - cs).toFloat() / visibleDurationSec * totalW).dp.coerceAtLeast(36.dp)
                    val isFocused = isFocusedRow && prog.startEpochSec <= focusTimeEpoch && focusTimeEpoch < prog.endEpochSec
                    val isAiring = prog.startEpochSec <= now && now < prog.endEpochSec
                    val scale by animateFloatAsState(if (isFocused) 1.01f else 1f, label = "s")

                    Box(
                        modifier = Modifier
                            .offset(x = xOff).width(cellW).fillMaxHeight()
                            .padding(vertical = 2.dp, horizontal = 1.dp).scale(scale)
                            .background(
                                PlexCard.copy(alpha = 0.70f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                if (isFocused) 2.dp else 0.5.dp,
                                if (isFocused) PlexTextPrimary else PlexBorder,
                                RoundedCornerShape(4.dp)
                            )
                    ) {
                        // Currently airing accent bar
                        if (isAiring) {
                            Box(Modifier.width(2.dp).fillMaxHeight().background(PlexTextPrimary).align(Alignment.CenterStart))
                        }
                        Column(
                            Modifier.fillMaxSize().padding(start = if (isAiring) 6.dp else 4.dp, end = 4.dp, top = 4.dp, bottom = 3.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    prog.title ?: "", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    color = PlexTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (prog.isNew) {
                                    Box(Modifier.background(PlexTextPrimary, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                        Text("NEW", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PlexBg)
                                    }
                                }
                            }
                            Text(
                                "${timeFormat.format(Date(prog.startEpochSec * 1000))} - ${timeFormat.format(Date(prog.endEpochSec * 1000))}",
                                fontSize = 11.sp, color = PlexTextTertiary, maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelLabel(
    channel: Channel,
    isFocusedRow: Boolean,
    isPlayingChannel: Boolean,
    modifier: Modifier = Modifier
) {
    val abbrev = (channel.guideName ?: "").take(3).uppercase()
    Column(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(PlexSurface).border(
                1.dp,
                if (isPlayingChannel) PlexTextPrimary else PlexBorder,
                CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(abbrev, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary, textAlign = TextAlign.Center)
        }
        Text(
            channel.guideNumber ?: "", fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = if (isFocusedRow) PlexTextPrimary else PlexTextSecondary, textAlign = TextAlign.Center
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// FULLSCREEN LAYOUT
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
private fun FullscreenLayout(
    uiState: com.airdvr.tv.ui.viewmodels.LiveTVUiState,
    exoPlayer: ExoPlayer,
    viewModel: LiveTVViewModel
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        if (uiState.isTuning) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PlexTextPrimary, modifier = Modifier.size(40.dp))
            }
        }

        // Slim bottom bar (32dp) — auto-hides after 5s
        AnimatedVisibility(
            visible = uiState.nowPlayingBarVisible,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SlimNowPlayingBar(
                channel = uiState.currentChannel,
                program = uiState.currentProgram
            )
        }

        // UP overlay — translucent bottom 40% with action buttons + show details + artwork
        AnimatedVisibility(
            visible = uiState.showFullscreenOverlay,
            enter = slideInVertically(tween(220)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FullscreenActionOverlay(
                channel = uiState.currentChannel,
                program = uiState.currentProgram,
                isMuted = uiState.isMuted,
                selectedIndex = uiState.actionButtonIndex
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SlimNowPlayingBar(
    channel: Channel?,
    program: EpgProgram?,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    if (channel == null) return

    val now = System.currentTimeMillis() / 1000
    val progress = if (program != null) {
        val dur = (program.endEpochSec - program.startEpochSec).coerceAtLeast(1)
        ((now - program.startEpochSec).toFloat() / dur).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(Color.Black.copy(alpha = 0.78f))
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${channel.guideNumber ?: ""} ${channel.guideName ?: ""}",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = PlexTextPrimary
            )
            Text("|", color = PlexTextTertiary, fontSize = 12.sp)
            Text(
                program?.title ?: "No data",
                fontSize = 12.sp, color = PlexTextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (program != null) {
                val s = timeFormat.format(Date(program.startEpochSec * 1000))
                val e = timeFormat.format(Date(program.endEpochSec * 1000))
                Text("$s - $e", fontSize = 11.sp, color = PlexTextTertiary)
            }
        }
        if (program != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = PlexTextPrimary,
                trackColor = PlexTextTertiary.copy(alpha = 0.25f)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FullscreenActionOverlay(
    channel: Channel?,
    program: EpgProgram?,
    isMuted: Boolean,
    selectedIndex: Int
) {
    if (channel == null) return
    Box(
        modifier = Modifier.fillMaxWidth().height(280.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.80f))
                .padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 48.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left column: actions + show info
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Action buttons row
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        OverlayActionBtn(Icons.Filled.GridView, "MultiView", selectedIndex == 0)
                        OverlayActionBtn(Icons.Filled.FiberManualRecord, "Record", selectedIndex == 1)
                        OverlayActionBtn(
                            if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            if (isMuted) "Unmute" else "Mute", selectedIndex == 2
                        )
                        OverlayActionBtn(Icons.Filled.Settings, "Quality", selectedIndex == 3)
                    }

                    // Show title
                    Text(
                        program?.title ?: "${channel.guideNumber ?: ""} ${channel.guideName ?: ""}",
                        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )

                    if (program != null) {
                        val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                        val parts = mutableListOf<String>()
                        program.episodeTitle?.let { if (it.isNotBlank()) parts.add(it) }
                        val s = timeFormat.format(Date(program.startEpochSec * 1000))
                        val e = timeFormat.format(Date(program.endEpochSec * 1000))
                        parts.add("$s - $e")
                        program.category?.let { if (it.isNotBlank()) parts.add(it) }
                        if (program.isNew) parts.add("NEW")
                        Text(parts.joinToString("  ·  "), fontSize = 13.sp, color = PlexTextSecondary)
                        if (!program.summary.isNullOrBlank()) {
                            Text(
                                program.summary,
                                fontSize = 14.sp, color = PlexTextSecondary,
                                maxLines = 3, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Right column: artwork / network logo
                Spacer(Modifier.width(24.dp))
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(PlexCard)
                        .border(1.dp, PlexBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val abbrev = (channel.guideName ?: "").take(3).uppercase()
                    Text(abbrev, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PlexTextSecondary)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OverlayActionBtn(icon: ImageVector, label: String, isSelected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                if (isSelected) PlexCard else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .border(
                if (isSelected) 2.dp else 0.dp,
                if (isSelected) PlexTextPrimary else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            icon, label,
            tint = if (isSelected) PlexTextPrimary else PlexTextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label, fontSize = 11.sp,
            color = if (isSelected) PlexTextPrimary else PlexTextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// MULTIVIEW LAYOUT
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
private fun MultiViewLayout(
    uiState: com.airdvr.tv.ui.viewmodels.LiveTVUiState,
    exoPlayers: List<ExoPlayer>,
    viewModel: LiveTVViewModel
) {
    val now = remember { System.currentTimeMillis() / 1000 }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val panes = uiState.multiViewPanes
        when (panes.size) {
            2 -> Row(Modifier.fillMaxSize()) {
                repeat(2) { i ->
                    MultiViewPane(
                        pane = panes[i], exoPlayer = exoPlayers[i],
                        isActive = uiState.activePaneIndex == i, index = i,
                        showPickerOverlay = uiState.guideOverlayVisible && uiState.activePaneIndex == i,
                        uiState = uiState, now = now,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
            3 -> Row(Modifier.fillMaxSize()) {
                MultiViewPane(
                    pane = panes[0], exoPlayer = exoPlayers[0],
                    isActive = uiState.activePaneIndex == 0, index = 0,
                    showPickerOverlay = uiState.guideOverlayVisible && uiState.activePaneIndex == 0,
                    uiState = uiState, now = now,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    MultiViewPane(
                        pane = panes[1], exoPlayer = exoPlayers[1],
                        isActive = uiState.activePaneIndex == 1, index = 1,
                        showPickerOverlay = uiState.guideOverlayVisible && uiState.activePaneIndex == 1,
                        uiState = uiState, now = now,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    MultiViewPane(
                        pane = panes[2], exoPlayer = exoPlayers[2],
                        isActive = uiState.activePaneIndex == 2, index = 2,
                        showPickerOverlay = uiState.guideOverlayVisible && uiState.activePaneIndex == 2,
                        uiState = uiState, now = now,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
            4 -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    MultiViewPane(
                        pane = panes[0], exoPlayer = exoPlayers[0],
                        isActive = uiState.activePaneIndex == 0, index = 0,
                        showPickerOverlay = uiState.guideOverlayVisible && uiState.activePaneIndex == 0,
                        uiState = uiState, now = now,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    MultiViewPane(
                        pane = panes[1], exoPlayer = exoPlayers[1],
                        isActive = uiState.activePaneIndex == 1, index = 1,
                        showPickerOverlay = uiState.guideOverlayVisible && uiState.activePaneIndex == 1,
                        uiState = uiState, now = now,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Row(Modifier.weight(1f)) {
                    MultiViewPane(
                        pane = panes[2], exoPlayer = exoPlayers[2],
                        isActive = uiState.activePaneIndex == 2, index = 2,
                        showPickerOverlay = uiState.guideOverlayVisible && uiState.activePaneIndex == 2,
                        uiState = uiState, now = now,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    MultiViewPane(
                        pane = panes[3], exoPlayer = exoPlayers[3],
                        isActive = uiState.activePaneIndex == 3, index = 3,
                        showPickerOverlay = uiState.guideOverlayVisible && uiState.activePaneIndex == 3,
                        uiState = uiState, now = now,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
            else -> {}
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
private fun MultiViewPane(
    pane: PaneState,
    exoPlayer: ExoPlayer,
    isActive: Boolean,
    index: Int,
    showPickerOverlay: Boolean,
    uiState: com.airdvr.tv.ui.viewmodels.LiveTVUiState,
    now: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(1.dp)
            .border(if (isActive) 2.dp else 0.dp, if (isActive) PlexTextPrimary else Color.Transparent)
            .background(Color.Black)
    ) {
        if (pane.channel != null) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
                update = { it.player = exoPlayer },
                modifier = Modifier.fillMaxSize()
            )
            if (pane.isTuning) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PlexTextPrimary, modifier = Modifier.size(28.dp))
                }
            }
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("${pane.channel.guideNumber ?: ""} ${pane.channel.guideName ?: ""}", color = PlexTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Box(
                Modifier.fillMaxSize().background(PlexSurface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Add, null, tint = PlexBorder, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Pick a channel", color = PlexTextTertiary, fontSize = 13.sp)
                }
            }
        }

        // Pane-local picker overlay: dark scrim + "Select a channel" header + guide grid at bottom
        if (showPickerOverlay) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))) {
                // Header text — top
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Select a channel",
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary
                    )
                }
                // Guide grid + categories — bottom 60%
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.65f)
                ) {
                    CategoryTabs(
                        categories = uiState.categories,
                        selectedIndex = uiState.selectedCategoryIndex,
                        isFocused = uiState.categoriesFocused,
                        modifier = Modifier.fillMaxWidth()
                    )
                    GuideGrid(
                        uiState = uiState, now = now,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PLEX NAV RAIL — slim 56dp left rail
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlexNavRail(
    visible: Boolean,
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
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(220)) { -it } + fadeIn(tween(220)),
        exit = slideOutHorizontally(tween(200)) { -it } + fadeOut(tween(200)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Profile circle
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(PlexCard).border(1.dp, PlexBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    userInitial, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = PlexTextPrimary
                )
            }
            Spacer(Modifier.height(8.dp))
            DividerLine()
            Spacer(Modifier.height(8.dp))

            // Main items
            NavRailIcon(Icons.Filled.Home, "Home", onHome, isActive = false)
            NavRailIcon(Icons.Filled.Search, "Where to Watch", onWhereToWatch, isActive = false)
            NavRailIcon(Icons.Filled.CalendarMonth, "Sports", onSportsCalendar, isActive = false)
            NavRailIcon(Icons.Filled.VideoLibrary, "Recordings", onRecordings, isActive = false)
            NavRailIcon(Icons.AutoMirrored.Filled.PlaylistPlay, "Custom", onCustomChannels, isActive = false)

            Spacer(Modifier.height(8.dp))
            DividerLine()
            Spacer(Modifier.height(8.dp))

            NavRailIcon(Icons.Filled.LiveTv, "Live TV", onLiveTV, isActive = true)

            Spacer(Modifier.weight(1f))

            NavRailIcon(Icons.Filled.Settings, "Settings", onSettings, isActive = false)
        }
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
