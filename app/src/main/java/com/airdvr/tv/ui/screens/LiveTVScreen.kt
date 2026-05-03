package com.airdvr.tv.ui.screens

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.C
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
import com.airdvr.tv.data.models.GameScore
import com.airdvr.tv.data.repository.ChannelLogoRepository
import com.airdvr.tv.ui.components.LoadingSpinner
import com.airdvr.tv.ui.components.NetworkArtwork
import com.airdvr.tv.ui.components.SportsTitleArtwork
import com.airdvr.tv.ui.components.rememberPosterUrl
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.LiveTVViewModel
import com.airdvr.tv.ui.viewmodels.MultiViewNavDirection
import com.airdvr.tv.ui.viewmodels.PaneState
import com.airdvr.tv.ui.viewmodels.PipPlacement
import com.airdvr.tv.ui.viewmodels.PipSize
import com.airdvr.tv.ui.viewmodels.ScreenMode
import com.airdvr.tv.ui.viewmodels.SportsCalendarViewModel
import com.airdvr.tv.ui.viewmodels.gameKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.airdvr.tv.util.Constants
import com.airdvr.tv.util.TeamLogos
import com.airdvr.tv.util.formatGameTimeLocal
import com.airdvr.tv.util.parseIsoToEpochSec
import java.text.SimpleDateFormat
import java.util.*

// ─── Layout constants ──────────────────────────────────────────────────────
private const val SLOT_SEC = 1800L
private const val INFO_PANEL_DP = 280
private const val CH_COL_DP = 38
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

    // Record dialog state for future programs in guide
    var showRecordDialog by remember { mutableStateOf(false) }
    var recordDialogProgram by remember { mutableStateOf<EpgProgram?>(null) }
    var recordDialogChannel by remember { mutableStateOf<Channel?>(null) }

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
    // Independent player for the PiP window. Uses a separate tuner; muted by default.
    val pipPlayer = remember {
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().also {
            it.playWhenReady = true
            it.volume = 0f
        }
    }

    DisposableEffect(Unit) {
        // Main player (pane 0 / fullscreen) — drives main isTuning state
        val mainListener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) viewModel.onPlayerReady() }
            override fun onPlayerError(error: PlaybackException) { viewModel.onPlayerError(error.message ?: "Playback error") }
        }
        exoPlayers[0].addListener(mainListener)
        // MultiView panes 1..3 — each clears its own pane.isTuning when ready
        val paneListeners = (1 until exoPlayers.size).map { i ->
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) viewModel.onPanePlayerReady(i)
                }
                override fun onPlayerError(error: PlaybackException) {
                    viewModel.onPanePlayerReady(i)  // Clear spinner even on error
                }
            }
        }
        paneListeners.forEachIndexed { idx, listener -> exoPlayers[idx + 1].addListener(listener) }
        // PiP player — log state transitions for debugging
        val pipListener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                android.util.Log.d("PIP", "playbackState=$state (READY=3 BUFFERING=2 IDLE=1 ENDED=4)")
            }
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("PIP", "playerError: ${error.errorCodeName} ${error.message}")
            }
        }
        pipPlayer.addListener(pipListener)
        onDispose {
            exoPlayers.forEach { it.release() }
            pipPlayer.release()
        }
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
    // CC toggle
    LaunchedEffect(uiState.ccEnabled) {
        val params = exoPlayers[0].trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !uiState.ccEnabled)
            .build()
        exoPlayers[0].trackSelectionParameters = params
    }
    // Volume — accounts for PiP audio swap when PiP is enabled in fullscreen.
    LaunchedEffect(uiState.activePaneIndex, uiState.mode, uiState.isMuted, uiState.audioOnPip, uiState.pipChannel) {
        if (uiState.mode == ScreenMode.MULTIVIEW) {
            exoPlayers.forEachIndexed { i, p -> p.volume = if (i == uiState.activePaneIndex && !uiState.isMuted) 1f else 0f }
            pipPlayer.volume = 0f
        } else {
            val muted = uiState.isMuted
            val pipAudio = uiState.pipChannel != null && uiState.audioOnPip
            exoPlayers[0].volume = if (!muted && !pipAudio) 1f else 0f
            pipPlayer.volume = if (!muted && pipAudio) 1f else 0f
        }
    }
    // PiP stream loader: load when URL changes; stop when cleared (releases tuner).
    LaunchedEffect(uiState.pipStreamUrl) {
        val url = uiState.pipStreamUrl
        if (url.isNullOrBlank()) {
            android.util.Log.d("PIP", "stream cleared — stopping PiP player")
            pipPlayer.stop()
            pipPlayer.clearMediaItems()
        } else if (pipPlayer.currentMediaItem?.localConfiguration?.uri?.toString() != url) {
            android.util.Log.d("PIP", "Playing: $url")
            loadHls(url, pipPlayer)
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
                // Intercept guide center-press for future programs
                if (uiState.mode == ScreenMode.GUIDE && !uiState.categoriesFocused &&
                    (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)
                ) {
                    val now = System.currentTimeMillis() / 1000
                    val prog = uiState.focusedProgram
                    val ch = uiState.focusedChannel
                    if (prog != null && ch != null && prog.startEpochSec > now) {
                        recordDialogProgram = prog
                        recordDialogChannel = ch
                        showRecordDialog = true
                        return@onKeyEvent true
                    }
                }
                // Nav rail CENTER/ENTER — dispatch to the focused item
                if (uiState.navRailVisible &&
                    (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)
                ) {
                    when (uiState.navRailFocusedIndex) {
                        0 -> onNavigateHome()
                        1 -> onNavigateWhereToWatch()
                        2 -> onNavigateSportsCalendar()
                        3 -> onNavigateRecordings()
                        4 -> onNavigateCustomChannels()
                        5 -> viewModel.hideNavRail() // Live TV (already here)
                        6 -> onNavigateSettings()
                    }
                    return@onKeyEvent true
                }
                handleKey(code, uiState, viewModel, onNavigateHome)
            }
    ) {
        if (uiState.isLoading) {
            LoadingSpinner(message = "Loading channels...", modifier = Modifier.fillMaxSize())
            return@Box
        }

        // Empty guide states
        if (uiState.channels.isEmpty()) {
            Box(Modifier.fillMaxSize().background(PlexBg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.SettingsInputAntenna, "Setup", tint = PlexTextTertiary, modifier = Modifier.size(48.dp))
                    Text("Connect your HDHomeRun to get started", color = PlexTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("Download the AirDVR agent at airdvr.com/setup", color = PlexTextSecondary, fontSize = 14.sp)
                }
            }
            return@Box
        }

        // Channels exist but programs may still be loading
        if (uiState.programsByChannel.values.all { it.isEmpty() }) {
            Box(Modifier.fillMaxSize()) {
                when (uiState.mode) {
                    ScreenMode.GUIDE -> GuideLayout(uiState, exoPlayers[0], viewModel)
                    ScreenMode.FULLSCREEN -> FullscreenLayout(uiState, exoPlayers[0], pipPlayer, viewModel)
                    ScreenMode.MULTIVIEW -> MultiViewLayout(uiState, exoPlayers, viewModel)
                }
                Box(
                    Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                        .background(PlexCard.copy(alpha = 0.9f), RoundedCornerShape(0.dp))
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text("Guide data loading...", color = PlexTextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            when (uiState.mode) {
                ScreenMode.GUIDE -> GuideLayout(uiState, exoPlayers[0], viewModel)
                ScreenMode.FULLSCREEN -> FullscreenLayout(uiState, exoPlayers[0], pipPlayer, viewModel)
                ScreenMode.MULTIVIEW -> MultiViewLayout(uiState, exoPlayers, viewModel)
            }
        }

        // Toast
        uiState.toastMessage?.let { msg ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(0.dp)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(msg, color = PlexTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Record dialog for programs in guide
        if (showRecordDialog && recordDialogProgram != null && recordDialogChannel != null) {
            val prog = recordDialogProgram!!
            val ch = recordDialogChannel!!
            val tf = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
            var selectedStorage by remember { mutableStateOf(uiState.defaultStoragePreference) }
            AlertDialog(
                onDismissRequest = { showRecordDialog = false },
                containerColor = PlexCard,
                title = {
                    androidx.compose.material3.Text(
                        prog.title ?: "", color = PlexTextPrimary, fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Text(
                            "CH ${ch.guideNumber ?: ""} ${ch.guideName ?: ""}",
                            color = PlexTextSecondary, fontSize = 14.sp
                        )
                        androidx.compose.material3.Text(
                            "${tf.format(Date(prog.startEpochSec * 1000))} – ${tf.format(Date(prog.endEpochSec * 1000))}",
                            color = PlexTextSecondary, fontSize = 14.sp
                        )
                    }
                },
                confirmButton = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Watch Now — primary action
                        TextButton(onClick = {
                            viewModel.tuneToChannel(ch)
                            showRecordDialog = false
                        }) {
                            androidx.compose.material3.Text("Watch Now", color = PlexAccent, fontWeight = FontWeight.Bold)
                        }
                        // Record Once
                        TextButton(onClick = {
                            viewModel.scheduleProgramWithStorage(ch, prog, selectedStorage)
                            showRecordDialog = false
                        }) {
                            androidx.compose.material3.Text("Record Once", color = Color(0xFFEF4444))
                        }
                        // Record Series
                        TextButton(onClick = {
                            viewModel.scheduleProgramWithStorage(ch, prog, selectedStorage)
                            showRecordDialog = false
                        }) {
                            androidx.compose.material3.Text("Record Series", color = Color(0xFFEF4444))
                        }
                        // Storage indicator + toggle (below Record buttons)
                        Spacer(Modifier.height(6.dp))
                        RecordStorageIndicator(
                            selectedStorage = selectedStorage,
                            canUseCloud = uiState.userPlan.lowercase() in listOf("pro", "premium"),
                            onToggle = { next -> selectedStorage = next }
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRecordDialog = false }) {
                        androidx.compose.material3.Text("Cancel", color = PlexTextSecondary)
                    }
                }
            )
        }

        // Slim nav rail (only over fullscreen, slides in from left)
        PlexNavRail(
            visible = uiState.navRailVisible,
            userInitial = uiState.userInitial,
            focusedIndex = uiState.navRailFocusedIndex,
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
    // Nav rail visible — handle key events for the rail
    if (uiState.navRailVisible) {
        return when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> { vm.navRailUp(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { vm.navRailDown(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { vm.hideNavRail(); true }
            KeyEvent.KEYCODE_BACK -> { vm.hideNavRail(); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> true // handled in composable onKeyEvent
            else -> false
        }
    }
    // PiP channel picker
    if (uiState.pipPickerVisible) {
        return when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> { vm.navigateUp(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { vm.navigateDown(); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                uiState.focusedChannel?.let { vm.setPipChannel(it) }
                true
            }
            KeyEvent.KEYCODE_BACK -> { vm.closePipPicker(); true }
            else -> false
        }
    }
    // Game picker
    if (uiState.gamePickerVisible) {
        return when (code) {
            KeyEvent.KEYCODE_BACK -> { vm.closeGamePicker(); true }
            else -> false  // game picker uses focusable surfaces for d-pad nav
        }
    }
    // Any D-pad press in fullscreen pings the now playing bar
    when (code) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> vm.pingNowPlayingBar()
    }
    // PiP swap-confirm dialog (highest priority when visible)
    if (uiState.pipSwapConfirmVisible) {
        return when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { vm.confirmPipSwap(); true }
            KeyEvent.KEYCODE_BACK -> { vm.cancelPipSwap(); true }
            else -> false  // dialog handles its own d-pad focus
        }
    }
    // PiP options menu visible
    if (uiState.pipOptionsMenuVisible) {
        return when (code) {
            KeyEvent.KEYCODE_BACK -> { vm.closePipOptions(); true }
            else -> false  // options menu uses focusable surfaces
        }
    }
    // PiP focused — controls operate on PiP
    if (uiState.pipFocused) {
        return when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { vm.unfocusPip(); true }
            KeyEvent.KEYCODE_DPAD_UP -> { vm.pipChannelUp(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { vm.pipChannelDown(); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { vm.openPipOptions(); true }
            KeyEvent.KEYCODE_BACK -> { vm.closePip(); true }
            KeyEvent.KEYCODE_VOLUME_MUTE -> { vm.togglePipAudio(); true }
            else -> false
        }
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
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            // RIGHT focuses PiP if active, otherwise opens overlay
            if (uiState.pipEnabled) { vm.focusPip(); true }
            else { vm.showFullscreenOverlay(); true }
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { vm.showFullscreenOverlay(); true }
        KeyEvent.KEYCODE_CHANNEL_UP -> { vm.channelUp(); true }
        KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.channelDown(); true }
        KeyEvent.KEYCODE_VOLUME_MUTE -> {
            if (uiState.pipEnabled) { vm.togglePipAudio(); true } else false
        }
        KeyEvent.KEYCODE_BACK -> {
            when {
                uiState.pipEnabled -> { vm.closePip(); true }
                uiState.scorebugGames.isNotEmpty() -> { vm.clearScorebugs(); true }
                else -> { vm.enterGuide(); true }
            }
        }
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
                    .background(Color.Black.copy(alpha = 0.60f), RoundedCornerShape(0.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(LiveRedDot))
                    Text(
                        "${uiState.currentChannel.guideNumber ?: ""} ${uiState.currentChannel.guideName ?: ""}",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary
                    )
                }
            }
        }
    }
}

// ─── Left Info Panel ──────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LeftInfoPanel(
    focusedChannel: Channel?,
    focusedProgram: EpgProgram?,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val posterUrl = rememberPosterUrl(focusedProgram?.title)
    val scrollState = rememberScrollState()
    val isGenericSports = focusedProgram != null && isGenericSportsTitle(focusedProgram.title)
    val isSportsProgram = isGenericSports ||
        (focusedProgram != null && SportsCalendarViewModel.isSports(focusedProgram))
    val logoInfo = focusedChannel?.guideName?.let { ChannelLogoRepository.getLogoInfo(it) }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(start = 16.dp, top = 32.dp, end = 8.dp, bottom = 8.dp)
    ) {
        if (focusedChannel != null) {
            // Row: poster + info side by side
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Poster (110x165)
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 180.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(PlexCard)
                        .border(1.dp, PlexBorder, RoundedCornerShape(0.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isSportsProgram -> {
                            SportsTitleArtwork(
                                title = focusedProgram?.title,
                                description = focusedProgram?.summary,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        !posterUrl.isNullOrBlank() -> {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = focusedProgram?.title,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        else -> {
                            NetworkArtwork(
                                network = logoInfo?.network,
                                logoUrl = logoInfo?.logoUrl,
                                fallbackLabel = focusedChannel.guideName,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Info column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        focusedProgram?.title ?: "",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary,
                        maxLines = 3, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PlexTextSecondary)) {
                                append(focusedChannel.guideNumber ?: "")
                            }
                            append(" ")
                            withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = PlexTextSecondary)) {
                                append(focusedChannel.guideName ?: "")
                            }
                        },
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (focusedProgram != null) {
                        Spacer(Modifier.height(4.dp))
                        if (!focusedProgram.category.isNullOrBlank()) {
                            Text(
                                focusedProgram.category,
                                fontSize = 14.sp, color = PlexTextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        val s = timeFormat.format(Date(focusedProgram.startEpochSec * 1000))
                        val e = timeFormat.format(Date(focusedProgram.endEpochSec * 1000))
                        Text(
                            "$s - $e",
                            fontSize = 14.sp, color = PlexTextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Description — full width below the row
            if (focusedProgram != null && !focusedProgram.summary.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    focusedProgram.summary,
                    fontSize = 13.sp, color = PlexTextSecondary,
                    maxLines = 12, overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }
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
                            .background(if (isFocused) PlexAccent else PlexTextPrimary, RoundedCornerShape(0.dp))
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
    val guideCellBg = remember(uiState.guideColorHex, uiState.guideOpacity) {
        try {
            Color(android.graphics.Color.parseColor(uiState.guideColorHex)).copy(alpha = uiState.guideOpacity)
        } catch (_: Exception) {
            Color(0xFF21262D).copy(alpha = uiState.guideOpacity)
        }
    }

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
                    now = now, timeFormat = timeFormat,
                    guideCellBg = guideCellBg,
                    schedules = uiState.schedules
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
                            fontSize = 12.sp,
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
    now: Long, timeFormat: SimpleDateFormat,
    guideCellBg: Color,
    schedules: List<com.airdvr.tv.data.models.RecordingSchedule> = emptyList()
) {
    key(channel.guideNumber) { // Force full recomposition when channel changes during recycling
    Row(modifier = Modifier.fillMaxWidth().height(ROW_DP.dp)) {
        // Channel label
        ChannelLabel(channel, isFocusedRow, isPlayingChannel, Modifier.width(CH_COL_DP.dp).fillMaxHeight())

        // Program cells
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            val totalW = maxWidth.value
            val windowEnd = timeWindowStart + visibleDurationSec
            val sorted = programs.sortedBy { it.startEpochSec }
            // Show programs that overlap with the current viewport window
            val visible = sorted.filter { it.endEpochSec > timeWindowStart && it.startEpochSec < windowEnd }

            if (visible.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(1.dp)
                        .clipToBounds()
                        .background(guideCellBg, RoundedCornerShape(0.dp))
                        .border(0.5.dp, PlexBorder, RoundedCornerShape(0.dp)),
                    contentAlignment = Alignment.CenterStart
                ) { Text("No data", color = PlexTextTertiary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp)) }
            } else {
                visible.forEachIndexed { programIndex, prog ->
                    key("${channel.guideNumber}_${prog.programId ?: programIndex}") {
                    val isCurrentlyAiring = prog.startEpochSec <= now && now < prog.endEpochSec
                    // Clamp program start/end to viewport bounds
                    val cs = maxOf(prog.startEpochSec, timeWindowStart)
                    val ce = minOf(prog.endEpochSec, windowEnd)
                    val xOff = ((cs - timeWindowStart).toFloat() / visibleDurationSec * totalW).dp
                    val cellW = ((ce - cs).toFloat() / visibleDurationSec * totalW).dp.coerceAtLeast(36.dp)
                    val isFocused = isFocusedRow && prog.startEpochSec <= focusTimeEpoch && focusTimeEpoch < prog.endEpochSec
                    val isAiring = isCurrentlyAiring
                    val scale by animateFloatAsState(if (isFocused) 1.01f else 1f, label = "s")

                    Box(
                        modifier = Modifier
                            .offset(x = xOff).width(cellW).fillMaxHeight()
                            .padding(vertical = 2.dp, horizontal = 1.dp)
                            .clipToBounds()
                            .scale(scale)
                            .background(
                                guideCellBg,
                                RoundedCornerShape(0.dp)
                            )
                            .border(
                                if (isFocused) 2.dp else 0.5.dp,
                                if (isFocused) PlexTextPrimary else PlexBorder,
                                RoundedCornerShape(0.dp)
                            )
                    ) {
                        // Currently airing accent bar
                        if (isAiring) {
                            Box(Modifier.width(2.dp).fillMaxHeight().background(PlexTextPrimary).align(Alignment.CenterStart))
                        }
                        val hasSchedule = schedules.any { s ->
                            s.title == prog.title && s.channelNumber == channel.guideNumber
                        }
                        val isRecording = hasSchedule && isAiring
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
                                if (hasSchedule) {
                                    Box(
                                        Modifier.size(8.dp).clip(CircleShape)
                                            .then(
                                                if (isRecording) Modifier.background(Color(0xFFEF4444))
                                                else Modifier.border(1.5.dp, Color(0xFFEF4444), CircleShape)
                                            )
                                    )
                                }
                                // HD badge for .1 channels
                                val chSuffix = channel.guideNumber?.substringAfter(".", "")
                                if (chSuffix == "1" || chSuffix == "") {
                                    Text("HD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PlexTextTertiary)
                                }
                            }
                            Text(
                                "${timeFormat.format(Date(prog.startEpochSec * 1000))} - ${timeFormat.format(Date(prog.endEpochSec * 1000))}",
                                fontSize = 12.sp, color = PlexTextTertiary, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    } // key
                }
            }
        }
    }
    } // key(channel.guideNumber)
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
    val logoInfo = remember(channel.guideName) { ChannelLogoRepository.getLogoInfo(channel.guideName ?: "") }
    Column(
        modifier = modifier.padding(horizontal = 0.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(PlexSurface).border(
                1.dp,
                if (isPlayingChannel) PlexTextPrimary else PlexBorder,
                CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            if (!logoInfo?.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = logoInfo!!.logoUrl,
                    contentDescription = channel.guideName,
                    modifier = Modifier.size(28.dp).clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(abbrev, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary, textAlign = TextAlign.Center)
            }
        }
        Text(
            channel.guideNumber ?: "", fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (isFocusedRow) PlexTextPrimary else PlexTextSecondary,
            maxLines = 1, textAlign = TextAlign.Center
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
    pipPlayer: ExoPlayer,
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

        // PiP window — placement-driven, separate ExoPlayer instance
        if (uiState.pipEnabled) {
            val pipAlignment = when (uiState.pipPlacement) {
                PipPlacement.TOP_LEFT -> Alignment.TopStart
                PipPlacement.TOP_RIGHT -> Alignment.TopEnd
                PipPlacement.BOTTOM_LEFT -> Alignment.BottomStart
                PipPlacement.BOTTOM_RIGHT -> Alignment.BottomEnd
            }
            val pipPad = when (uiState.pipPlacement) {
                PipPlacement.TOP_LEFT -> PaddingValues(top = 24.dp, start = 24.dp)
                PipPlacement.TOP_RIGHT -> PaddingValues(top = 24.dp, end = 24.dp)
                PipPlacement.BOTTOM_LEFT -> PaddingValues(bottom = 64.dp, start = 24.dp)
                PipPlacement.BOTTOM_RIGHT -> PaddingValues(bottom = 64.dp, end = 24.dp)
            }
            PipOverlay(
                channel = uiState.pipChannel,
                focused = uiState.pipFocused,
                audioOnPip = uiState.audioOnPip,
                player = pipPlayer,
                size = uiState.pipSize,
                modifier = Modifier
                    .align(pipAlignment)
                    .padding(pipPad)
            )
        }

        // Scorebug column — top-right, offset below PiP if PiP is also top-right
        if (uiState.scorebugGames.isNotEmpty()) {
            val pipPushesScorebug = uiState.pipEnabled && uiState.pipPlacement == PipPlacement.TOP_RIGHT
            val topPad = if (pipPushesScorebug) (24 + uiState.pipSize.heightDp + 12).dp else 24.dp
            ScorebugOverlay(
                games = uiState.scorebugGames,
                channels = uiState.channels,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topPad, end = 24.dp)
            )
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
                programs = uiState.programsByChannel[uiState.currentChannel?.guideNumber ?: ""],
                isMuted = uiState.isMuted,
                ccEnabled = uiState.ccEnabled,
                selectedIndex = uiState.actionButtonIndex,
                schedules = uiState.schedules,
                sportsScore = uiState.currentSportsScore,
                streamMode = uiState.streamMode,
                pipActive = uiState.pipEnabled,
                showBettingLines = uiState.showBettingLines
            )
        }

        // PiP channel picker overlay
        if (uiState.pipPickerVisible) {
            PipChannelPicker(
                channels = uiState.filteredChannels,
                currentMain = uiState.currentChannel,
                programsByChannel = uiState.programsByChannel,
                onPick = { viewModel.setPipChannel(it) },
                onCancel = { viewModel.closePipPicker() }
            )
        }

        // PiP options menu — Size / Placement / Make Main / Close
        if (uiState.pipOptionsMenuVisible && uiState.pipEnabled) {
            PipOptionsMenu(
                pipChannel = uiState.pipChannel,
                currentSize = uiState.pipSize,
                currentPlacement = uiState.pipPlacement,
                onSize = { viewModel.setPipSize(it) },
                onPlacement = { viewModel.setPipPlacement(it) },
                onMakeMain = { viewModel.requestPipSwap() },
                onClosePip = { viewModel.closePip() },
                onDismiss = { viewModel.closePipOptions() }
            )
        }

        // PiP make-main confirmation dialog
        if (uiState.pipSwapConfirmVisible) {
            PipSwapConfirmDialog(
                pipChannelLabel = uiState.pipChannel?.let {
                    "${it.guideNumber ?: ""} ${it.guideName ?: ""}".trim()
                } ?: "",
                onConfirm = { viewModel.confirmPipSwap() },
                onCancel = { viewModel.cancelPipSwap() }
            )
        }

        // Game picker overlay
        if (uiState.gamePickerVisible) {
            GamePickerOverlay(
                games = uiState.availableGames,
                trackedKeys = uiState.trackedGameKeys,
                leagueFilter = uiState.pickerLeagueFilter,
                onLeagueFilter = { viewModel.setPickerLeagueFilter(it) },
                onPick = { viewModel.pickGame(it) },
                onClose = { viewModel.closeGamePicker() }
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

    val logoInfo = remember(channel.guideName) { ChannelLogoRepository.getLogoInfo(channel.guideName ?: "") }

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
            if (!logoInfo?.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = logoInfo!!.logoUrl,
                    contentDescription = channel.guideName,
                    modifier = Modifier.size(18.dp).clip(RoundedCornerShape(0.dp)),
                    contentScale = ContentScale.Fit
                )
            }
            Text(
                "${channel.guideNumber ?: ""} ${channel.guideName ?: ""}",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = PlexTextPrimary
            )
            Text("|", color = PlexTextTertiary, fontSize = 12.sp)
            Text(
                program?.title ?: "",
                fontSize = 12.sp, color = PlexTextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (program != null) {
                val s = timeFormat.format(Date(program.startEpochSec * 1000))
                val e = timeFormat.format(Date(program.endEpochSec * 1000))
                Text("$s - $e", fontSize = 12.sp, color = PlexTextTertiary)
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
    programs: List<EpgProgram>? = null,
    isMuted: Boolean,
    ccEnabled: Boolean,
    selectedIndex: Int,
    schedules: List<com.airdvr.tv.data.models.RecordingSchedule> = emptyList(),
    sportsScore: GameScore? = null,
    streamMode: com.airdvr.tv.data.stream.StreamMode = com.airdvr.tv.data.stream.StreamMode.TUNNEL,
    pipActive: Boolean = false,
    showBettingLines: Boolean = false
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
                    val isRecording = program != null && schedules.any { s ->
                        s.title == program.title && s.channelNumber == channel?.guideNumber
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OverlayActionBtn(Icons.Filled.GridView, "MultiView", selectedIndex == 0)
                        OverlayActionBtn(
                            Icons.Filled.FiberManualRecord,
                            if (isRecording) "Recording" else "Record",
                            selectedIndex == 1,
                            iconTint = if (isRecording) Color(0xFFEF4444) else null
                        )
                        OverlayActionBtn(
                            if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            if (isMuted) "Unmute" else "Mute", selectedIndex == 2
                        )
                        OverlayActionBtn(Icons.Filled.Settings, "Quality", selectedIndex == 3)
                        OverlayActionBtn(Icons.Filled.ClosedCaption, if (ccEnabled) "CC On" else "CC Off", selectedIndex == 4)
                        OverlayActionBtn(
                            Icons.Filled.PictureInPictureAlt,
                            if (pipActive) "PiP On" else "PiP",
                            selectedIndex == 5,
                            iconTint = if (pipActive) PlexAccent else null
                        )
                        OverlayActionBtn(Icons.Filled.SportsScore, "Scores", selectedIndex == 6)
                        Spacer(Modifier.weight(1f))
                        StreamModeBadge(streamMode)
                    }

                    // Live sports scoreboard takes the place of the title block when matched.
                    if (sportsScore != null && program != null) {
                        SportsScorePanel(
                            score = sportsScore,
                            program = program,
                            channel = channel,
                            showBetting = showBettingLines
                        )
                    } else {
                        // Show title
                        Text(
                            program?.title ?: "${channel.guideNumber ?: ""} ${channel.guideName ?: ""}",
                            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (program != null) {
                        val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                        // Time + category metadata row — show only when no live score
                        // (the SportsScorePanel already renders matchup time / period).
                        if (sportsScore == null) {
                            val parts = mutableListOf<String>()
                            program.episodeTitle?.let { if (it.isNotBlank()) parts.add(it) }
                            val s = timeFormat.format(Date(program.startEpochSec * 1000))
                            val e = timeFormat.format(Date(program.endEpochSec * 1000))
                            parts.add("$s - $e")
                            program.category?.let { if (it.isNotBlank()) parts.add(it) }
                            val chSuffix = channel?.guideNumber?.substringAfter(".", "")
                            if (chSuffix == "1" || chSuffix == "") parts.add("HD")
                            Text(parts.joinToString("  ·  "), fontSize = 13.sp, color = PlexTextSecondary)
                        }
                        // EPG description — same source as the guide side panel.
                        // Always shown when present, including for sports programs.
                        if (!program.summary.isNullOrBlank()) {
                            Text(
                                program.summary,
                                fontSize = 14.sp, color = PlexTextSecondary,
                                maxLines = 3, overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Up Next — non-sports programs only.
                        if (sportsScore == null && channel != null) {
                            val nextProg = programs?.firstOrNull { it.startEpochSec >= program.endEpochSec }
                            if (nextProg != null) {
                                val nextStart = timeFormat.format(Date(nextProg.startEpochSec * 1000))
                                val nextEnd = timeFormat.format(Date(nextProg.endEpochSec * 1000))
                                Text(
                                    "Up Next: ${nextProg.title ?: ""} · $nextStart–$nextEnd",
                                    fontSize = 13.sp, color = PlexTextTertiary
                                )
                            }
                        }
                    }
                }

                // Right column: poster artwork / channel logo / fallback.
                // For sports, never use TMDB — generic titles like "NBA Basketball" return wrong art.
                Spacer(Modifier.width(24.dp))
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(0.dp))
                        .background(PlexCard)
                        .border(1.dp, PlexBorder, RoundedCornerShape(0.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val isGenericSports = program != null && isGenericSportsTitle(program.title)
                    val isSportsProgram = sportsScore != null || isGenericSports ||
                        (program != null && SportsCalendarViewModel.isSports(program))
                    val logoInfo = remember(channel.guideName) {
                        ChannelLogoRepository.getLogoInfo(channel.guideName ?: "")
                    }

                    if (isSportsProgram) {
                        SportsArtwork(
                            score = sportsScore,
                            program = program,
                            channelLogoUrl = logoInfo?.logoUrl,
                            channelName = channel.guideName
                        )
                    } else {
                        val posterUrl = rememberPosterUrl(program?.title)
                        if (!posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = program?.title,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else if (!logoInfo?.logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = logoInfo!!.logoUrl,
                                contentDescription = channel.guideName,
                                modifier = Modifier.size(64.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            val abbrev = (channel.guideName ?: "").take(3).uppercase()
                            Text(abbrev, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PlexTextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StreamModeBadge(mode: com.airdvr.tv.data.stream.StreamMode) {
    val (label, color) = when (mode) {
        com.airdvr.tv.data.stream.StreamMode.LOCAL -> "Local" to Color(0xFF22C55E)
        com.airdvr.tv.data.stream.StreamMode.REMOTE -> "Remote" to Color(0xFF3B82F6)
        com.airdvr.tv.data.stream.StreamMode.TUNNEL -> "Relay" to Color(0xFFF59E0B)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(0.dp))
            .border(0.5.dp, PlexBorder, RoundedCornerShape(0.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(
            label,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = PlexTextPrimary, letterSpacing = 0.3.sp
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OverlayActionBtn(icon: ImageVector, label: String, isSelected: Boolean, iconTint: Color? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                if (isSelected) PlexCard else Color.Transparent,
                RoundedCornerShape(0.dp)
            )
            .border(
                if (isSelected) 2.dp else 0.dp,
                if (isSelected) PlexTextPrimary else Color.Transparent,
                RoundedCornerShape(0.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            icon, label,
            tint = iconTint ?: if (isSelected) PlexTextPrimary else PlexTextSecondary,
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
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(0.dp))
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
    focusedIndex: Int = 0,
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

            // Main items — index 0..4
            NavRailIcon(Icons.Filled.Home, "Home", onHome, isActive = false, isFocusedByIndex = focusedIndex == 0)
            NavRailIcon(Icons.Filled.Search, "Where to Watch", onWhereToWatch, isActive = false, isFocusedByIndex = focusedIndex == 1)
            NavRailIcon(Icons.Filled.CalendarMonth, "Sports", onSportsCalendar, isActive = false, isFocusedByIndex = focusedIndex == 2)
            NavRailIcon(Icons.Filled.VideoLibrary, "Recordings", onRecordings, isActive = false, isFocusedByIndex = focusedIndex == 3)
            NavRailIcon(Icons.AutoMirrored.Filled.PlaylistPlay, "Custom", onCustomChannels, isActive = false, isFocusedByIndex = focusedIndex == 4)

            Spacer(Modifier.height(8.dp))
            DividerLine()
            Spacer(Modifier.height(8.dp))

            NavRailIcon(Icons.Filled.LiveTv, "Live TV", onLiveTV, isActive = true, isFocusedByIndex = focusedIndex == 5)

            Spacer(Modifier.weight(1f))

            NavRailIcon(Icons.Filled.Settings, "Settings", onSettings, isActive = false, isFocusedByIndex = focusedIndex == 6)
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
    isActive: Boolean,
    isFocusedByIndex: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val highlight = focused || isFocusedByIndex
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .size(width = 48.dp, height = 40.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (highlight || isActive) PlexCard else Color.Transparent,
            focusedContainerColor = PlexCard
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (isFocusedByIndex) androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.5.dp, PlexTextPrimary)
            ) else androidx.tv.material3.Border.None,
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.5.dp, PlexTextPrimary)
            )
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
                    highlight -> PlexTextPrimary
                    isActive -> PlexAccent
                    else -> PlexTextTertiary
                },
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun StorageChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (isSelected) PlexAccent.copy(alpha = 0.2f) else PlexSurface,
                RoundedCornerShape(0.dp)
            )
            .border(
                1.dp,
                if (isSelected) PlexAccent else PlexBorder,
                RoundedCornerShape(0.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        androidx.compose.material3.Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) PlexAccent else PlexTextSecondary
        )
    }
}

/**
 * Compact destination indicator shown below the Record buttons in the guide
 * record dialog. Displays the resolved storage target with a per-recording
 * toggle to swap between Local and Cloud (global preference is not affected).
 */
@Composable
private fun RecordStorageIndicator(
    selectedStorage: String,
    canUseCloud: Boolean,
    onToggle: (String) -> Unit
) {
    val isCloud = selectedStorage.lowercase() == "cloud"
    val destinationLabel = if (isCloud) "Cloud" else "Local (Mac Mini)"
    val next = if (isCloud) "local" else "cloud"
    val toggleLabel = if (isCloud) "Use Local instead" else "Use Cloud instead"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PlexSurface, RoundedCornerShape(0.dp))
            .border(0.5.dp, PlexBorder, RoundedCornerShape(0.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .background(
                        if (isCloud) Color(0xFF22C55E).copy(alpha = 0.85f) else Color(0xFF6B7280).copy(alpha = 0.85f),
                        RoundedCornerShape(0.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                androidx.compose.material3.Text(
                    if (isCloud) "CLOUD" else "LOCAL",
                    fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, letterSpacing = 0.4.sp
                )
            }
            androidx.compose.material3.Text(
                "Will save to: $destinationLabel",
                fontSize = 12.sp, color = PlexTextSecondary
            )
        }
        if (!isCloud && !canUseCloud) {
            androidx.compose.material3.Text(
                "Cloud requires Pro subscription",
                fontSize = 11.sp, color = PlexTextTertiary
            )
        } else {
            Box(
                modifier = Modifier
                    .background(Color.Transparent, RoundedCornerShape(0.dp))
                    .border(0.5.dp, PlexBorder, RoundedCornerShape(0.dp))
                    .clickable { onToggle(next) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                androidx.compose.material3.Text(
                    toggleLabel,
                    fontSize = 11.sp, color = PlexAccent,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// LIVE SPORTS SCORE PANEL (in-overlay)
// ═══════════════════════════════════════════════════════════════════════════

/** Generic EPG sports titles where TMDB returns unrelated artwork ("The Queen of Basketball" etc.). */
private val GENERIC_SPORTS_TITLES = listOf(
    "NBA Basketball", "NFL Football", "MLB Baseball", "NHL Hockey",
    "NCAA Football", "NCAA Basketball", "College Football", "College Basketball",
    "PGA Golf", "MLS Soccer", "Premier League Soccer", "WNBA Basketball"
)

private fun isGenericSportsTitle(title: String?): Boolean {
    if (title.isNullOrBlank()) return false
    return GENERIC_SPORTS_TITLES.any { title.contains(it, ignoreCase = true) }
}

private fun isInProgress(status: String?): Boolean {
    val s = (status ?: "").lowercase()
    return s.contains("progress") || s.contains("live") || s == "in progress" || s == "inprogress"
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SportsScorePanel(
    score: GameScore,
    program: EpgProgram,
    channel: Channel?,
    showBetting: Boolean = false
) {
    val homeFull = score.homeTeam?.takeIf { it.isNotBlank() }
    val awayFull = score.awayTeam?.takeIf { it.isNotBlank() }
    val live = isInProgress(score.status)
    val isFinal = (score.status ?: "").lowercase().contains("final")
    val homePts = score.homeScore
    val awayPts = score.awayScore
    val showScores = (live || isFinal) && homePts != null && awayPts != null
    val statusRaw = (score.status ?: "").lowercase().replace(" ", "")
    val isJunkStatus = statusRaw.contains("notnecessary") ||
        statusRaw.contains("postponed") || statusRaw.contains("canceled") ||
        statusRaw.contains("cancelled") || statusRaw.contains("suspended") ||
        statusRaw.contains("delayed") || statusRaw.isBlank()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Title: full team names — "Boston Celtics vs Philadelphia 76ers"
        val matchupTitle = when {
            !awayFull.isNullOrBlank() && !homeFull.isNullOrBlank() -> "$awayFull vs $homeFull"
            else -> program.title ?: ""
        }
        Text(
            matchupTitle,
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )

        // Score line — large bold "56 - 57" (only when started)
        if (showScores) {
            Text(
                "$awayPts - $homePts",
                fontSize = 32.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary
            )
        }

        // Status line — LIVE · Q3 · 8:46  /  FINAL  /  TIP OFF 7:30 PM
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (live) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(LiveRedDot))
                Text("LIVE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LiveRedDot)
            }
            val q = score.quarter?.takeIf { it.isNotBlank() }
            val tr = score.timeRemaining?.takeIf { it.isNotBlank() }
            val tipOff = formatGameTimeLocal(score.startTime).ifBlank {
                formatGameTimeLocal(program.startTime)
            }
            val statusLine = when {
                live && q != null && tr != null -> "$q · $tr"
                live && q != null -> q
                live && tr != null -> tr
                isFinal -> "FINAL"
                isJunkStatus -> "TIP OFF $tipOff"
                else -> "TIP OFF $tipOff"
            }
            Text(statusLine, fontSize = 14.sp, color = PlexTextSecondary, fontWeight = FontWeight.Medium)
        }

        // Live win-probability bar — InProgress games only
        if (live && score.homeWinProbability != null && score.awayWinProbability != null) {
            WinProbabilityBar(score = score)
        }

        // Betting lines — only when toggle ON and at least one field non-null
        if (showBetting) {
            BettingLinesBlock(score = score)
        }

        // Channel info — "Ch 4.1 · NBC"
        val chParts = mutableListOf<String>()
        channel?.guideNumber?.takeIf { it.isNotBlank() }?.let { chParts.add("Ch $it") }
        channel?.guideName?.takeIf { it.isNotBlank() }?.let { chParts.add(it) }
        if (chParts.isNotEmpty()) {
            Text(
                chParts.joinToString(" · "),
                fontSize = 12.sp, color = PlexTextTertiary
            )
        }
    }
}

/**
 * Real betting lines from SportsDataIO — point spread, over/under, money line.
 * Only the lines whose fields are non-null are shown; renders nothing if all are null.
 * Negative spread = home team favored.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BettingLinesBlock(score: GameScore) {
    val spread = score.pointSpread
    val ou = score.overUnder
    val homeML = score.homeMoneyLine
    val awayML = score.awayMoneyLine
    val series = score.seriesInfo
    val hasSeries = series != null && (series.homeWins != null || series.awayWins != null)
    if (spread == null && ou == null && homeML == null && awayML == null && !hasSeries) return

    val league = (score.league ?: "").lowercase()
    val homeAbbr = TeamLogos.abbrev(league, score.homeTeam).ifBlank { "HOME" }
    val awayAbbr = TeamLogos.abbrev(league, score.awayTeam).ifBlank { "AWAY" }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (spread != null) {
            val spreadLine = when {
                spread < 0 -> "$homeAbbr ${formatSpread(spread)}"
                spread > 0 -> "$awayAbbr ${formatSpread(-spread)}"
                else -> "PK"
            }
            Text(
                spreadLine,
                fontSize = 12.sp, color = PlexTextTertiary,
                fontFamily = FontFamily.Monospace
            )
        }
        if (ou != null) {
            Text(
                "O/U ${formatOu(ou)}",
                fontSize = 12.sp, color = PlexTextTertiary,
                fontFamily = FontFamily.Monospace
            )
        }
        if (homeML != null || awayML != null) {
            val parts = mutableListOf<String>()
            homeML?.let { parts.add("$homeAbbr ${formatMoneyLine(it)}") }
            awayML?.let { parts.add("$awayAbbr ${formatMoneyLine(it)}") }
            Text(
                "ML: ${parts.joinToString(" / ")}",
                fontSize = 12.sp, color = PlexTextTertiary,
                fontFamily = FontFamily.Monospace
            )
        }
        if (hasSeries) {
            val hw = series!!.homeWins ?: 0
            val aw = series.awayWins ?: 0
            val gn = series.gameNumber
            val ml = series.maxLength
            val gameLabel = when {
                gn != null && ml != null -> "Game $gn of $ml"
                gn != null -> "Game $gn"
                else -> null
            }
            val seriesLine = listOfNotNull(
                "Series: $awayAbbr $aw–$homeAbbr $hw",
                gameLabel
            ).joinToString(" · ")
            Text(
                seriesLine,
                fontSize = 12.sp, color = PlexTextTertiary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Live win-probability bar. Renders "AWAY xx% [bar] yy% HOME" with the bar width
 * proportional to home/away probability. Probabilities are floats in [0, 1].
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WinProbabilityBar(score: GameScore) {
    val home = score.homeWinProbability?.coerceIn(0f, 1f) ?: return
    val away = score.awayWinProbability?.coerceIn(0f, 1f) ?: return
    val total = (home + away).takeIf { it > 0f } ?: return
    val awayPct = away / total
    val homePct = home / total
    val league = (score.league ?: "").lowercase()
    val homeAbbr = TeamLogos.abbrev(league, score.homeTeam).ifBlank { "HOME" }
    val awayAbbr = TeamLogos.abbrev(league, score.awayTeam).ifBlank { "AWAY" }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "$awayAbbr ${(awayPct * 100).toInt()}%",
                fontSize = 11.sp, color = PlexTextSecondary, fontWeight = FontWeight.Bold
            )
            Text(
                "${(homePct * 100).toInt()}% $homeAbbr",
                fontSize = 11.sp, color = PlexTextSecondary, fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PlexBorder)
        ) {
            Box(
                modifier = Modifier
                    .weight(awayPct.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(Color(0xFFE63946))
            )
            Box(
                modifier = Modifier
                    .weight(homePct.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(PlexAccent)
            )
        }
    }
}

private fun formatSpread(s: Double): String =
    if (s == s.toInt().toDouble()) "${s.toInt()}" else "%.1f".format(s)

private fun formatOu(o: Double): String =
    if (o == o.toInt().toDouble()) "${o.toInt()}" else "%.1f".format(o)

private fun formatMoneyLine(ml: Int): String = if (ml > 0) "+$ml" else "$ml"

/**
 * Right-side overlay artwork for sports programs:
 *   - both team logos side-by-side when matched
 *   - league logo when known but teams aren't matched
 *   - channel logo / abbreviation as last fallback (still no TMDB)
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SportsArtwork(
    score: GameScore?,
    program: EpgProgram?,
    channelLogoUrl: String?,
    channelName: String?
) {
    val league = (score?.league?.lowercase()?.takeIf { it.isNotBlank() }
        ?: program?.let { SportsCalendarViewModel.detectLeague(it.title ?: "") }
        ?: "")
    val awayLogo = TeamLogos.urlFor(league, score?.awayTeam)
    val homeLogo = TeamLogos.urlFor(league, score?.homeTeam)

    when {
        awayLogo != null && homeLogo != null -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AsyncImage(
                    model = awayLogo,
                    contentDescription = score?.awayTeam,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(56.dp)
                )
                AsyncImage(
                    model = homeLogo,
                    contentDescription = score?.homeTeam,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        else -> {
            val leagueLogo = TeamLogos.leagueUrl(league)
            if (leagueLogo != null) {
                AsyncImage(
                    model = leagueLogo,
                    contentDescription = league.uppercase(),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(80.dp)
                )
            } else if (!channelLogoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channelLogoUrl,
                    contentDescription = channelName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(64.dp)
                )
            } else {
                val abbrev = (channelName ?: "").take(3).uppercase()
                Text(abbrev, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PlexTextSecondary)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PICTURE-IN-PICTURE OVERLAY
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
private fun PipOverlay(
    channel: Channel?,
    focused: Boolean,
    audioOnPip: Boolean,
    player: ExoPlayer,
    size: PipSize = PipSize.MEDIUM,
    modifier: Modifier = Modifier
) {
    if (channel == null) return
    Box(
        modifier = modifier
            .size(width = size.widthDp.dp, height = size.heightDp.dp)
            .clip(RoundedCornerShape(0.dp))
            .background(Color.Black)
            .border(
                if (focused) 3.dp else 2.dp,
                if (focused) PlexAccent else Color.White.copy(alpha = 0.2f),
                RoundedCornerShape(0.dp)
            )
    ) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        // Channel label bottom-left
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(0.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                "${channel.guideNumber ?: ""} ${channel.guideName ?: ""}",
                color = PlexTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
            )
        }
        // Audio indicator top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                .padding(4.dp)
        ) {
            Icon(
                if (audioOnPip) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (audioOnPip) "Audio on PiP" else "Audio on Main",
                tint = if (audioOnPip) PlexAccent else PlexTextTertiary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PIP CHANNEL PICKER
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PipChannelPicker(
    channels: List<Channel>,
    currentMain: Channel?,
    programsByChannel: Map<String, List<EpgProgram>>,
    onPick: (Channel) -> Unit,
    onCancel: () -> Unit
) {
    val nowSec = remember { System.currentTimeMillis() / 1000 }
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(channels.size) {
        if (channels.isNotEmpty()) {
            try { firstRowFocus.requestFocus() } catch (_: Exception) {}
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .heightIn(max = 480.dp)
                .background(Color.Black)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                "Pick a channel for PiP",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Plays alongside the main video",
                fontSize = 12.sp, color = PlexTextTertiary
            )
            Spacer(Modifier.height(12.dp))
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(channels) { idx, ch ->
                    val isMain = ch.guideNumber == currentMain?.guideNumber
                    val logoInfo = ChannelLogoRepository.getLogoInfo(ch.guideName ?: "")
                    val nowProgram = programsByChannel[ch.guideNumber ?: ""]?.firstOrNull {
                        it.startEpochSec <= nowSec && nowSec < it.endEpochSec
                    }
                    var isFocused by remember { mutableStateOf(false) }
                    val rowMod = (if (idx == 0) Modifier.focusRequester(firstRowFocus) else Modifier)
                        .onFocusChanged {
                            isFocused = it.isFocused
                            if (it.isFocused) {
                                coroutineScope.launch {
                                    val info = listState.layoutInfo
                                    val visible = info.visibleItemsInfo
                                    val first = visible.firstOrNull()?.index ?: 0
                                    val last = visible.lastOrNull()?.index ?: 0
                                    if (idx <= first || idx >= last) {
                                        listState.animateScrollToItem(
                                            (idx - 2).coerceAtLeast(0)
                                        )
                                    }
                                }
                            }
                        }
                    Surface(
                        onClick = { if (!isMain) onPick(ch) },
                        modifier = rowMod
                            .fillMaxWidth()
                            .height(64.dp)
                            .focusable(),
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Black,
                            focusedContainerColor = Color(0xFF0E1A2B)
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = androidx.tv.material3.Border.None,
                            focusedBorder = androidx.tv.material3.Border.None
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(
                                        when {
                                            isFocused -> PlexAccent
                                            isMain -> PlexAccent.copy(alpha = 0.4f)
                                            else -> Color.Transparent
                                        }
                                    )
                            )
                            Spacer(Modifier.width(12.dp))
                            Box(
                                Modifier.size(36.dp).background(Color(0xFF0A0A0A)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!logoInfo?.logoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = logoInfo!!.logoUrl,
                                        contentDescription = ch.guideName,
                                        modifier = Modifier.size(32.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Text(
                                        (ch.guideName ?: "").take(2).uppercase(),
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PlexTextSecondary
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Line 1 — show title, bold + larger
                                val titleLine = nowProgram?.title?.takeIf { it.isNotBlank() }
                                    ?: ch.guideName ?: ""
                                Text(
                                    titleLine,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMain) PlexTextTertiary else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // Line 2 — channel # + network, muted
                                val chLabel = listOfNotNull(
                                    ch.guideNumber?.takeIf { it.isNotBlank() },
                                    ch.guideName?.takeIf { it.isNotBlank() }
                                ).joinToString(" ")
                                if (chLabel.isNotBlank()) {
                                    Text(
                                        chLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = PlexTextTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isMain) {
                                Text(
                                    "ON MAIN",
                                    fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                    color = PlexTextTertiary,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PIP OPTIONS MENU + SWAP CONFIRM DIALOG
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PipOptionsMenu(
    pipChannel: Channel?,
    currentSize: PipSize,
    currentPlacement: PipPlacement,
    onSize: (PipSize) -> Unit,
    onPlacement: (PipPlacement) -> Unit,
    onMakeMain: () -> Unit,
    onClosePip: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { firstFocus.requestFocus() } catch (_: Exception) {} }

    val align = when (currentPlacement) {
        PipPlacement.TOP_LEFT -> Alignment.TopStart
        PipPlacement.TOP_RIGHT -> Alignment.TopEnd
        PipPlacement.BOTTOM_LEFT -> Alignment.BottomStart
        PipPlacement.BOTTOM_RIGHT -> Alignment.BottomEnd
    }
    val pad = when (currentPlacement) {
        PipPlacement.TOP_LEFT -> PaddingValues(top = 24.dp, start = 24.dp + currentSize.widthDp.dp + 8.dp)
        PipPlacement.TOP_RIGHT -> PaddingValues(top = 24.dp, end = 24.dp + currentSize.widthDp.dp + 8.dp)
        PipPlacement.BOTTOM_LEFT -> PaddingValues(bottom = 64.dp, start = 24.dp + currentSize.widthDp.dp + 8.dp)
        PipPlacement.BOTTOM_RIGHT -> PaddingValues(bottom = 64.dp, end = 24.dp + currentSize.widthDp.dp + 8.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .align(align)
                .padding(pad)
                .width(220.dp)
                .background(PlexBg)
                .border(1.dp, PlexBorder)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "Size",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PlexTextTertiary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            PipSize.values().forEachIndexed { idx, s ->
                PipMenuRow(
                    label = "${s.label} (${s.widthDp}×${s.heightDp})",
                    selected = s == currentSize,
                    focusRequester = if (idx == 0) firstFocus else null,
                    onClick = { onSize(s) }
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Placement",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PlexTextTertiary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            PipPlacement.values().forEach { p ->
                PipMenuRow(
                    label = p.label,
                    selected = p == currentPlacement,
                    onClick = { onPlacement(p) }
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PlexBorder)
            )
            Spacer(Modifier.height(6.dp))
            PipMenuRow(label = "Make Main Channel", selected = false, onClick = onMakeMain)
            PipMenuRow(label = "Close PiP", selected = false, onClick = onClosePip)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PipMenuRow(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val mod = (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
        .onFocusChanged { isFocused = it.isFocused }
    Surface(
        onClick = onClick,
        modifier = mod
            .fillMaxWidth()
            .height(36.dp)
            .focusable(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = PlexAccent.copy(alpha = 0.3f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border.None,
            focusedBorder = androidx.tv.material3.Border.None
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isFocused -> Color.White
                    selected -> PlexAccent
                    else -> PlexTextSecondary
                },
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PlexAccent)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PipSwapConfirmDialog(
    pipChannelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { confirmFocus.requestFocus() } catch (_: Exception) {} }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .background(PlexBg)
                .border(1.dp, PlexBorder)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Switch to PiP channel?",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary
            )
            Text(
                if (pipChannelLabel.isNotBlank()) "Switch $pipChannelLabel to main view?"
                else "Switch the PiP channel to main view?",
                fontSize = 13.sp, color = PlexTextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                Surface(
                    onClick = onCancel,
                    modifier = Modifier.height(40.dp).focusable(),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = PlexCard,
                        focusedContainerColor = PlexBorder
                    )
                ) {
                    Text(
                        "Cancel",
                        fontSize = 13.sp, color = PlexTextPrimary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
                Surface(
                    onClick = onConfirm,
                    modifier = Modifier
                        .focusRequester(confirmFocus)
                        .height(40.dp)
                        .focusable(),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = PlexAccent,
                        focusedContainerColor = PlexAccent
                    )
                ) {
                    Text(
                        "Confirm",
                        fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// GAME PICKER OVERLAY
// ═══════════════════════════════════════════════════════════════════════════

private val PICKER_LEAGUES = listOf("ALL", "NBA", "NFL", "MLB", "NHL")

private fun leaguePillColor(league: String?): Color = when ((league ?: "").uppercase()) {
    "NBA" -> Color(0xFFC8102E)        // NBA red
    "NFL" -> Color(0xFF013369)        // NFL navy
    "MLB" -> Color(0xFF002D72)        // MLB blue
    "NHL" -> Color(0xFF111111)        // NHL black
    else -> Color(0xFF2A2A2A)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GamePickerOverlay(
    games: List<GameScore>,
    trackedKeys: Set<String>,
    leagueFilter: String,
    onLeagueFilter: (String) -> Unit,
    onPick: (GameScore) -> Unit,
    onClose: () -> Unit
) {
    val displayedGames = remember(games, leagueFilter) {
        if (leagueFilter == "ALL") games
        else games.filter { (it.league ?: "").equals(leagueFilter, ignoreCase = true) }
    }
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(displayedGames.size) {
        if (displayedGames.isNotEmpty()) {
            try { firstRowFocus.requestFocus() } catch (_: Exception) {}
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(820.dp)
                .fillMaxHeight(0.88f)
                .background(Color.Black)
                .padding(28.dp)
        ) {
            Text(
                "Live & upcoming games",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "SELECT adds a scorebug · BACK closes · UP/DOWN to scroll",
                fontSize = 12.sp, color = PlexTextTertiary
            )
            Spacer(Modifier.height(16.dp))

            // League filter tabs
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PICKER_LEAGUES.forEach { league ->
                    LeagueFilterTab(
                        league = league,
                        selected = leagueFilter.equals(league, ignoreCase = true),
                        onClick = { onLeagueFilter(league) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (displayedGames.isEmpty()) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.SportsScore, "No games",
                            tint = PlexTextTertiary, modifier = Modifier.size(40.dp)
                        )
                        Text(
                            if (leagueFilter == "ALL")
                                "No games in progress or starting in the next 24 hours"
                            else "No $leagueFilter games right now",
                            color = PlexTextSecondary, fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(displayedGames) { idx, game ->
                        val key = gameKey(game)
                        val isTracked = key in trackedKeys
                        GamePickerCard(
                            game = game,
                            isTracked = isTracked,
                            onPick = { onPick(game) },
                            modifier = if (idx == 0) Modifier.focusRequester(firstRowFocus) else Modifier
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LeagueFilterTab(
    league: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val pillColor = leaguePillColor(league)
    Surface(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) pillColor else Color(0xFF1A1A1A),
            focusedContainerColor = if (selected) pillColor else Color(0xFF2A2A2A)
        ),
        border = ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border.None,
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, PlexAccent)
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                league,
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else PlexTextSecondary
            )
        }
    }
}

/** Format start time as e.g. "8:00 PM" in the user's local timezone. */
private fun gameStartLabel(game: GameScore): String =
    formatGameTimeLocal(game.startTime)

/** Short period text for live games: "Q4 5:32" / "P2 12:08" / "T9" / "LIVE". */
private fun gamePeriodLabel(game: GameScore): String {
    val q = (game.quarter ?: "").trim()
    val tr = (game.timeRemaining ?: "").trim()
    return when {
        q.isNotEmpty() && tr.isNotEmpty() -> "$q $tr"
        q.isNotEmpty() -> q
        tr.isNotEmpty() -> tr
        else -> "LIVE"
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GamePickerCard(
    game: GameScore,
    isTracked: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val league = (game.league ?: "").lowercase()
    val leagueLabel = league.uppercase().ifBlank { "—" }
    val live = isInProgress(game.status)
    val awayAbbr = TeamLogos.abbrev(league, game.awayTeam)
    val homeAbbr = TeamLogos.abbrev(league, game.homeTeam)
    val awayLogo = TeamLogos.urlFor(league, game.awayTeam)
    val homeLogo = TeamLogos.urlFor(league, game.homeTeam)
    val pillColor = leaguePillColor(leagueLabel)

    val cardGradient = Brush.horizontalGradient(
        listOf(Color(0xFF111111), Color(0xFF0A0A0A))
    )

    Surface(
        onClick = onPick,
        modifier = modifier.fillMaxWidth().height(108.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (isTracked) androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, PlexAccent.copy(alpha = 0.5f))
            ) else androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F1F1F))
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, PlexAccent)
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cardGradient)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // League pill — top-left corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(pillColor, RoundedCornerShape(0.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    leagueLabel,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
            }

            // Tracked check — top-right
            if (isTracked) {
                Icon(
                    Icons.Filled.Check, "Tracked",
                    tint = PlexAccent,
                    modifier = Modifier.size(16.dp).align(Alignment.TopEnd)
                )
            }

            // Main row: logo + abbrev — vs/score — abbrev + logo
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TeamCell(logoUrl = awayLogo, abbrev = awayAbbr)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (live) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(LiveRedDot))
                            Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LiveRedDot)
                        }
                        Text(
                            "${game.awayScore ?: 0}  -  ${game.homeScore ?: 0}",
                            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White
                        )
                    } else {
                        Text(
                            "vs",
                            fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = PlexTextSecondary
                        )
                    }
                }
                TeamCell(logoUrl = homeLogo, abbrev = homeAbbr)
            }

            // Bottom: time / period
            Text(
                if (live) "LIVE · ${gamePeriodLabel(game)}" else gameStartLabel(game).ifBlank { "—" },
                fontSize = 11.sp, fontWeight = FontWeight.Medium,
                color = if (live) LiveRedDot else PlexTextTertiary,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun TeamCell(logoUrl: String?, abbrev: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(96.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = abbrev,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    abbrev.take(3),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = PlexTextSecondary
                )
            }
        }
        Text(
            abbrev,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = Color.White, maxLines = 1
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SCOREBUG OVERLAY
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScorebugOverlay(
    games: List<GameScore>,
    channels: List<Channel>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        games.take(3).forEach { game ->
            ScorebugRow(game = game, channels = channels)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScorebugRow(
    game: GameScore,
    channels: List<Channel>
) {
    val league = game.league?.lowercase() ?: ""
    val awayAbbr = TeamLogos.abbrev(league, game.awayTeam)
    val homeAbbr = TeamLogos.abbrev(league, game.homeTeam)
    val awayLogo = TeamLogos.urlFor(league, game.awayTeam)
    val homeLogo = TeamLogos.urlFor(league, game.homeTeam)
    val live = isInProgress(game.status)
    val isFinal = (game.status ?: "").lowercase().contains("final")
    val hasScores = game.awayScore != null && game.homeScore != null

    Box(
        modifier = Modifier
            .size(width = 280.dp, height = 44.dp)
            .background(Color(0xE6000000))
            .padding(horizontal = 10.dp)
    ) {
        if (live || (isFinal && hasScores)) {
            // [logo] [abbrev] [awayScore] | [homeScore] [abbrev] [logo]   [Period]
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScorebugLogo(awayLogo)
                Text(awayAbbr, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    "${game.awayScore ?: 0}",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
                Text("|", fontSize = 14.sp, color = PlexTextTertiary)
                Text(
                    "${game.homeScore ?: 0}",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
                Text(homeAbbr, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                ScorebugLogo(homeLogo)
                Spacer(Modifier.weight(1f))
                Text(
                    if (isFinal) "FINAL" else gamePeriodLabel(game),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFinal) PlexTextSecondary else LiveRedDot,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            // Scheduled: [logo] [abbrev] vs [abbrev] [logo]   TIP OFF h:mm a
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScorebugLogo(awayLogo)
                Text(awayAbbr, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("vs", fontSize = 12.sp, color = PlexTextSecondary)
                Text(homeAbbr, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                ScorebugLogo(homeLogo)
                Spacer(Modifier.weight(1f))
                Text(
                    "TIP OFF ${gameStartLabel(game).ifBlank { "—" }}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PlexTextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ScorebugLogo(url: String?) {
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(Modifier.size(20.dp))
    }
}
