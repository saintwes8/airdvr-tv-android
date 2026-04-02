package com.airdvr.tv.ui.screens

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.airdvr.tv.AirDVRApp
import com.airdvr.tv.ui.components.ChannelCard
import com.airdvr.tv.ui.components.LoadingSpinner
import com.airdvr.tv.ui.components.NowPlayingBar
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.LiveTVViewModel
import com.airdvr.tv.util.Constants

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveTVScreen(
    onNavigateHome: () -> Unit,
    onNavigateGuide: () -> Unit,
    viewModel: LiveTVViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokenManager = remember { AirDVRApp.instance.tokenManager }

    val listState = rememberLazyListState()
    val playerFocusRequester = remember { FocusRequester() }

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
            .also { player ->
                player.playWhenReady = true
            }
    }

    // Attach player listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> viewModel.onPlayerReady()
                    Player.STATE_BUFFERING -> { /* keep isTuning from tune call */ }
                    else -> {}
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlayerError(error.message ?: "Playback error")
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Load new stream when URL changes
    LaunchedEffect(uiState.streamUrl) {
        val url = uiState.streamUrl ?: return@LaunchedEffect
        val token = tokenManager.getAccessToken()
        val headers = if (token != null) mapOf("Authorization" to "Bearer $token") else emptyMap()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .apply {
                if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
            }

        val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(MediaItem.fromUri(url))

        exoPlayer.stop()
        exoPlayer.setMediaSource(hlsSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Scroll channel list to selected channel
    LaunchedEffect(uiState.selectedChannel, uiState.channels) {
        val idx = uiState.channels.indexOf(uiState.selectedChannel)
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                            viewModel.channelUp()
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            viewModel.channelDown()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            viewModel.showOverlay()
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            onNavigateHome()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Channel list sidebar (25% width)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.25f)
                    .background(AirDVRNavy.copy(alpha = 0.9f))
            ) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingSpinner(message = "Loading channels...")
                    }
                } else if (uiState.channels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No channels",
                            color = AirDVRTextSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.channels) { channel ->
                            ChannelCard(
                                channel = channel,
                                isSelected = channel == uiState.selectedChannel,
                                onClick = { viewModel.tuneToChannel(channel) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Video player area (75% width)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .focusRequester(playerFocusRequester)
                    .focusable()
            ) {
                // PlayerView
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

                // Tuning overlay
                if (uiState.isTuning) {
                    LoadingSpinner(
                        message = "Tuning...",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Error message
                if (uiState.error != null && !uiState.isTuning) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AirDVRNavy.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error!!,
                            color = Color.Red,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Now Playing bar overlay at the bottom
                uiState.selectedChannel?.let { channel ->
                    NowPlayingBar(
                        channel = channel,
                        currentProgram = null, // EPG integration can be added
                        visible = uiState.showOverlay,
                        onHide = { viewModel.hideOverlay() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}
