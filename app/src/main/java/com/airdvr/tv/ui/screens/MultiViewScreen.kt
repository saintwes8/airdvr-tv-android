package com.airdvr.tv.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.airdvr.tv.AirDVRApp
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.ui.components.LoadingSpinner
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.MultiViewLayout
import com.airdvr.tv.ui.viewmodels.MultiViewViewModel
import com.airdvr.tv.ui.viewmodels.ViewPane

@OptIn(androidx.media3.common.util.UnstableApi::class)
@androidx.compose.runtime.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MultiViewScreen(
    onBack: () -> Unit,
    viewModel: MultiViewViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokenManager = remember { AirDVRApp.instance.tokenManager }

    // Create and cache ExoPlayers for each pane
    val exoPlayers = remember {
        List(4) { ExoPlayer.Builder(context).build().also { it.playWhenReady = true } }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayers.forEach { it.release() }
        }
    }

    // Load streams when URLs change
    uiState.panes.forEachIndexed { index, pane ->
        LaunchedEffect(pane.streamUrl) {
            val url = pane.streamUrl ?: run {
                exoPlayers[index].stop()
                exoPlayers[index].clearMediaItems()
                return@LaunchedEffect
            }
            val token = tokenManager.getAccessToken()
            val headers = if (token != null) mapOf("Authorization" to "Bearer $token") else emptyMap()
            val dsFactory = DefaultHttpDataSource.Factory()
                .apply { if (headers.isNotEmpty()) setDefaultRequestProperties(headers) }
            val source = HlsMediaSource.Factory(dsFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(MediaItem.fromUri(url))
            exoPlayers[index].stop()
            exoPlayers[index].setMediaSource(source)
            exoPlayers[index].prepare()
            exoPlayers[index].playWhenReady = true
        }
    }

    // Channel picker dialog
    if (uiState.showChannelPicker) {
        ChannelPickerDialog(
            channels = uiState.channels,
            onChannelSelected = { channel ->
                viewModel.assignChannelToPane(uiState.pickerTargetPane, channel)
            },
            onDismiss = { viewModel.hideChannelPicker() }
        )
    }

    // Tuner limit warning dialog
    if (uiState.tunerLimitWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissTunerWarning() },
            title = { Text("Tuner Limit Reached", color = AirDVRTextPrimary) },
            text = {
                Text(
                    "You can only stream 2 channels simultaneously (tuner limit). Remove a channel first.",
                    color = AirDVRTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissTunerWarning() }) {
                    Text("OK", color = AirDVRBlue)
                }
            },
            containerColor = AirDVRCard
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AirDVRNavy)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AirDVRCard)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = AirDVRTextPrimary)
            }
            Text(
                text = "MultiView",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AirDVRTextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            // Toggle layout button
            IconButton(onClick = { viewModel.toggleLayout() }) {
                Icon(
                    imageVector = if (uiState.layout == MultiViewLayout.TWO_UP) Icons.Filled.GridView else Icons.Filled.ViewStream,
                    contentDescription = "Toggle layout",
                    tint = AirDVRBlue
                )
            }
            Text(
                text = if (uiState.layout == MultiViewLayout.TWO_UP) "2-Up" else "4-Up",
                color = AirDVRBlue,
                fontSize = 14.sp
            )
        }

        // Grid layout
        val panesToShow = if (uiState.layout == MultiViewLayout.TWO_UP) 2 else 4

        if (uiState.layout == MultiViewLayout.TWO_UP) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.panes.take(2).forEach { pane ->
                    VideoPane(
                        pane = pane,
                        exoPlayer = exoPlayers[pane.index],
                        isFocused = uiState.focusedPane == pane.index,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = {
                            viewModel.setFocusedPane(pane.index)
                            if (pane.channel == null) {
                                viewModel.showChannelPickerForPane(pane.index)
                            }
                        },
                        onLongClick = {
                            viewModel.showChannelPickerForPane(pane.index)
                        }
                    )
                }
            }
        } else {
            // 4-up: 2x2 grid
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.panes.take(2).forEach { pane ->
                        VideoPane(
                            pane = pane,
                            exoPlayer = exoPlayers[pane.index],
                            isFocused = uiState.focusedPane == pane.index,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = {
                                viewModel.setFocusedPane(pane.index)
                                if (pane.channel == null) {
                                    viewModel.showChannelPickerForPane(pane.index)
                                }
                            },
                            onLongClick = {
                                viewModel.showChannelPickerForPane(pane.index)
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.panes.drop(2).take(2).forEach { pane ->
                        VideoPane(
                            pane = pane,
                            exoPlayer = exoPlayers[pane.index],
                            isFocused = uiState.focusedPane == pane.index,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = {
                                viewModel.setFocusedPane(pane.index)
                                if (pane.channel == null) {
                                    viewModel.showChannelPickerForPane(pane.index)
                                }
                            },
                            onLongClick = {
                                viewModel.showChannelPickerForPane(pane.index)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@ExperimentalTvMaterial3Api
@Composable
private fun VideoPane(
    pane: ViewPane,
    exoPlayer: ExoPlayer,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val borderColor = when {
        pane.isActive -> AirDVROrange
        isFocused -> AirDVRFocusRing
        else -> Color.White.copy(alpha = 0.15f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(Color.Black)
    ) {
        if (pane.channel != null) {
            // Video player
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

            if (pane.isTuning) {
                LoadingSpinner(
                    message = "Tuning...",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Channel label badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(AirDVRNavy.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${pane.channel.guideNumber} ${pane.channel.guideName}",
                    fontSize = 12.sp,
                    color = AirDVRTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Focus overlay to handle clicks
            androidx.tv.material3.Surface(
                onClick = onClick,
                modifier = Modifier.fillMaxSize(),
                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.White.copy(alpha = 0.05f)
                )
            ) {}
        } else {
            // Empty pane placeholder
            androidx.tv.material3.Surface(
                onClick = onClick,
                modifier = Modifier.fillMaxSize(),
                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                    containerColor = AirDVRNavy,
                    focusedContainerColor = AirDVRCard
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Pane ${pane.index + 1}",
                            fontSize = 16.sp,
                            color = AirDVRTextSecondary
                        )
                        Text(
                            text = "Press OK to add channel",
                            fontSize = 13.sp,
                            color = AirDVRTextSecondary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelPickerDialog(
    channels: List<Channel>,
    onChannelSelected: (Channel) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(360.dp)
                .height(480.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = AirDVRCard)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select Channel",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AirDVRTextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(channels) { channel ->
                        androidx.tv.material3.Surface(
                            onClick = { onChannelSelected(channel) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
                                RoundedCornerShape(6.dp)
                            ),
                            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                                containerColor = AirDVRNavy,
                                focusedContainerColor = AirDVRBlue.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = channel.guideNumber,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AirDVROrange,
                                    modifier = Modifier.width(36.dp)
                                )
                                Text(
                                    text = channel.guideName,
                                    fontSize = 14.sp,
                                    color = AirDVRTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = AirDVRTextSecondary)
                }
            }
        }
    }
}
