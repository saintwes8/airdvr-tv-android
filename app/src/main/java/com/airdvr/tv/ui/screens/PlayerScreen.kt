package com.airdvr.tv.ui.screens

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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.airdvr.tv.AirDVRApp
import com.airdvr.tv.ui.components.LoadingSpinner
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.PlayerViewModel
import com.airdvr.tv.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    recordingId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokenManager = remember { AirDVRApp.instance.tokenManager }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Initialize recording
    LaunchedEffect(recordingId) {
        viewModel.loadRecording(recordingId)
    }

    // Build ExoPlayer
    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                Constants.MIN_BUFFER_MS,
                Constants.MAX_BUFFER_MS,
                Constants.BUFFER_FOR_PLAYBACK_MS,
                Constants.BUFFER_FOR_PLAYBACK_MS
            )
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .also { it.playWhenReady = true }
    }

    // Player listener
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
            // Save resume position before releasing
            val posSec = (exoPlayer.currentPosition / 1000).toInt()
            if (posSec > 0) viewModel.updateResumePosition(posSec)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Load stream when URL is available
    LaunchedEffect(uiState.streamUrl) {
        val url = uiState.streamUrl ?: return@LaunchedEffect
        val token = tokenManager.getAccessToken()
        val headers = if (token != null) mapOf("Authorization" to "Bearer $token") else emptyMap()
        val dsFactory = DefaultHttpDataSource.Factory()
            .apply { if (headers.isNotEmpty()) setDefaultRequestProperties(headers) }
        val source = HlsMediaSource.Factory(dsFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(MediaItem.fromUri(url))
        exoPlayer.stop()
        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
        // Seek to resume position
        if (uiState.currentPositionMs > 0) {
            exoPlayer.seekTo(uiState.currentPositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    // Sync playback speed from VM
    LaunchedEffect(uiState.playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(uiState.playbackSpeed)
    }

    // Sync play/pause from VM
    LaunchedEffect(uiState.isPlaying) {
        exoPlayer.playWhenReady = uiState.isPlaying
    }

    // Auto-hide controls
    LaunchedEffect(uiState.showControls) {
        if (uiState.showControls) {
            delay(Constants.CHANNEL_OVERLAY_HIDE_DELAY_MS)
            viewModel.hideControls()
        }
    }

    // Position tracker
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration.coerceAtLeast(0)
            viewModel.updatePosition(pos, dur)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    viewModel.showControls()
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                            viewModel.togglePlayPause()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_DPAD_LEFT -> {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 15_000).coerceAtLeast(0))
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val dur = exoPlayer.duration
                            exoPlayer.seekTo((exoPlayer.currentPosition + 30_000).coerceAtMost(dur))
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            val posSec = (exoPlayer.currentPosition / 1000).toInt()
                            if (posSec > 0) viewModel.updateResumePosition(posSec)
                            onBack()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Full-screen video
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading spinner
        if (uiState.isLoading) {
            LoadingSpinner(
                message = "Loading...",
                modifier = Modifier.fillMaxSize()
            )
        }

        // Error overlay
        if (uiState.error != null && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AirDVRNavy.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = Color.Red,
                    fontSize = 16.sp
                )
            }
        }

        // Player controls overlay
        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn(),
            exit = fadeOut(),
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
                onRewind = {
                    exoPlayer.seekTo((exoPlayer.currentPosition - 15_000).coerceAtLeast(0))
                },
                onFastForward = {
                    val dur = exoPlayer.duration
                    exoPlayer.seekTo((exoPlayer.currentPosition + 30_000).coerceAtMost(dur))
                },
                onSeek = { fraction ->
                    val target = (fraction * uiState.durationMs).toLong()
                    exoPlayer.seekTo(target)
                },
                onSpeedSelected = { speed -> viewModel.setPlaybackSpeed(speed) },
                onCCToggle = { viewModel.toggleCC() }
            )
        }
    }

    // Request focus on mount
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun PlayerControls(
    title: String,
    episodeTitle: String?,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    ccEnabled: Boolean,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onCCToggle: () -> Unit
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
    val progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.6f),
                    0.3f to Color.Transparent,
                    0.7f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.8f)
                )
            )
    ) {
        // Top: title
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
        ) {
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!episodeTitle.isNullOrBlank()) {
                Text(
                    text = episodeTitle,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Top right: CC + Speed
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // CC Toggle
            IconButton(onClick = onCCToggle) {
                Icon(
                    imageVector = Icons.Filled.ClosedCaption,
                    contentDescription = "Closed Captions",
                    tint = if (ccEnabled) AirDVRBlue else Color.White.copy(alpha = 0.6f)
                )
            }

            // Speed selector
            Box {
                TextButton(onClick = { showSpeedMenu = !showSpeedMenu }) {
                    Text(
                        text = "${playbackSpeed}x",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { showSpeedMenu = false },
                    modifier = Modifier.background(AirDVRCard)
                ) {
                    speeds.forEach { speed ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${speed}x",
                                    color = if (playbackSpeed == speed) AirDVRBlue else AirDVRTextPrimary
                                )
                            },
                            onClick = {
                                onSpeedSelected(speed)
                                showSpeedMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Progress bar (seekable)
            Slider(
                value = progress,
                onValueChange = { onSeek(it) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = AirDVROrange,
                    activeTrackColor = AirDVROrange,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )

            // Time labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPositionMs),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = formatDuration(durationMs),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Playback controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rewind 15s
                IconButton(
                    onClick = onRewind,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Replay,
                        contentDescription = "Rewind 15s",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                // Play/Pause
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(64.dp)
                        .background(AirDVRBlue, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                // Fast Forward 30s
                IconButton(
                    onClick = onFastForward,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forward30,
                        contentDescription = "Forward 30s",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
