package com.airdvr.tv.ui.screens

import android.util.Log
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.airdvr.tv.AirDVRApp
import com.airdvr.tv.ui.components.LoadingSpinner
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.PlayerViewModel
import com.airdvr.tv.util.Constants
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    recordingId: String,
    streamUrl: String? = null,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokenManager = remember { AirDVRApp.instance.tokenManager }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(recordingId) { viewModel.loadRecording(recordingId, streamUrl) }

    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                Constants.MIN_BUFFER_MS, Constants.MAX_BUFFER_MS,
                Constants.BUFFER_FOR_PLAYBACK_MS, Constants.BUFFER_FOR_PLAYBACK_MS
            ).build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build()
            .also { it.playWhenReady = true }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) viewModel.onPlayerReady()
            }
            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlayerError(error.message ?: "Playback error")
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            val pos = (exoPlayer.currentPosition / 1000).toInt()
            if (pos > 0) viewModel.updateResumePosition(pos)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(uiState.streamUrl) {
        val url = uiState.streamUrl ?: return@LaunchedEffect
        Log.d("PLAYBACK", "Loading URL: $url")
        val token = tokenManager.getAccessToken()
        val headers = if (token != null) mapOf("Authorization" to "Bearer $token") else emptyMap()
        val factory = DefaultHttpDataSource.Factory()
            .apply { if (headers.isNotEmpty()) setDefaultRequestProperties(headers) }
        val isHls = url.contains(".m3u8")
        Log.d("PLAYBACK", "Source type: ${if (isHls) "HLS" else "Progressive"}")
        val src = if (isHls) {
            HlsMediaSource.Factory(factory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(MediaItem.fromUri(url))
        } else {
            ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri(url))
        }
        exoPlayer.stop()
        exoPlayer.setMediaSource(src)
        exoPlayer.prepare()
        if (uiState.currentPositionMs > 0) exoPlayer.seekTo(uiState.currentPositionMs)
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(uiState.playbackSpeed) { exoPlayer.playbackParameters = PlaybackParameters(uiState.playbackSpeed) }
    LaunchedEffect(uiState.isPlaying) { exoPlayer.playWhenReady = uiState.isPlaying }

    LaunchedEffect(uiState.showControls) {
        if (uiState.showControls) {
            delay(Constants.CHANNEL_OVERLAY_HIDE_DELAY_MS)
            viewModel.hideControls()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            viewModel.updatePosition(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize().background(Color.Black)
            .focusRequester(focusRequester).focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    viewModel.showControls()
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                            viewModel.togglePlayPause(); true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_DPAD_LEFT -> {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 15_000).coerceAtLeast(0)); true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            exoPlayer.seekTo((exoPlayer.currentPosition + 30_000).coerceAtMost(exoPlayer.duration)); true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            val pos = (exoPlayer.currentPosition / 1000).toInt()
                            if (pos > 0) viewModel.updateResumePosition(pos)
                            onBack(); true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        if (uiState.isLoading) {
            LoadingSpinner(message = "Loading...", modifier = Modifier.fillMaxSize())
        }

        if (uiState.error != null && !uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(PlexBg.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) { Text(uiState.error ?: "Something went wrong", color = PlexTextSecondary, fontSize = 16.sp) }
        }

        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControls(
                title = uiState.recording?.title ?: "",
                episodeTitle = uiState.recording?.episodeTitle,
                currentPositionMs = uiState.currentPositionMs,
                durationMs = uiState.durationMs,
                isPlaying = uiState.isPlaying,
                playbackSpeed = uiState.playbackSpeed,
                ccEnabled = uiState.ccEnabled,
                onPlayPause = { viewModel.togglePlayPause() },
                onRewind = { exoPlayer.seekTo((exoPlayer.currentPosition - 15_000).coerceAtLeast(0)) },
                onFastForward = { exoPlayer.seekTo((exoPlayer.currentPosition + 30_000).coerceAtMost(exoPlayer.duration)) },
                onSeek = { exoPlayer.seekTo((it * uiState.durationMs).toLong()) },
                onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
                onCCToggle = { viewModel.toggleCC() }
            )
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun PlayerControls(
    title: String, episodeTitle: String?,
    currentPositionMs: Long, durationMs: Long,
    isPlaying: Boolean, playbackSpeed: Float, ccEnabled: Boolean,
    onPlayPause: () -> Unit, onRewind: () -> Unit, onFastForward: () -> Unit,
    onSeek: (Float) -> Unit, onSpeedSelected: (Float) -> Unit, onCCToggle: () -> Unit
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
    val progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.6f),
                0.3f to Color.Transparent,
                0.7f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.8f)
            )
        )
    ) {
        // Top: title
        Column(modifier = Modifier.align(Alignment.TopStart).padding(24.dp)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!episodeTitle.isNullOrBlank()) {
                Text(episodeTitle, fontSize = 16.sp, color = PlexTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        // Top right: CC + Speed
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCCToggle) {
                Icon(Icons.Filled.ClosedCaption, "CC", tint = if (ccEnabled) PlexTextPrimary else PlexTextTertiary)
            }
            Box {
                TextButton(onClick = { showSpeedMenu = !showSpeedMenu }) {
                    Text("${playbackSpeed}x", color = PlexTextPrimary, fontSize = 16.sp)
                }
                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { showSpeedMenu = false },
                    modifier = Modifier.background(PlexCard)
                ) {
                    speeds.forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}x", color = if (playbackSpeed == speed) PlexTextPrimary else PlexTextSecondary) },
                            onClick = { onSpeedSelected(speed); showSpeedMenu = false }
                        )
                    }
                }
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Slider(
                value = progress, onValueChange = { onSeek(it) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = PlexTextPrimary,
                    activeTrackColor = PlexTextPrimary,
                    inactiveTrackColor = PlexTextTertiary.copy(alpha = 0.3f)
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(currentPositionMs), fontSize = 13.sp, color = PlexTextSecondary)
                Text(formatDuration(durationMs), fontSize = 13.sp, color = PlexTextSecondary)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRewind, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Replay, "Rewind 15s", tint = PlexTextPrimary, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(32.dp))
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(64.dp).background(PlexTextPrimary.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        tint = PlexTextPrimary, modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(Modifier.width(32.dp))
                IconButton(onClick = onFastForward, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Forward30, "Forward 30s", tint = PlexTextPrimary, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
