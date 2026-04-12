package com.airdvr.tv.ui.screens

import android.view.KeyEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.airdvr.tv.data.models.Recording
import com.airdvr.tv.ui.components.rememberPosterUrl
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.RecordingCategory
import com.airdvr.tv.ui.viewmodels.RecordingsViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RecordingsScreen(
    onNavigatePlayer: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RecordingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Recording?>(null) }
    val tabs = listOf(
        "All" to RecordingCategory.ALL,
        "TV Shows" to RecordingCategory.TV_SHOWS,
        "Movies" to RecordingCategory.MOVIES,
        "Sports" to RecordingCategory.SPORTS
    )

    Column(
        modifier = Modifier.fillMaxSize().background(PlexBg)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PlexTextPrimary)
            }
            Text("Recordings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary)
        }

        // Filter tabs
        LazyRow(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            itemsIndexed(tabs) { _, (label, category) ->
                val isSelected = uiState.selectedCategory == category
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) PlexTextPrimary else PlexTextTertiary
                    )
                    if (isSelected) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(28.dp).height(2.dp)
                                .background(PlexTextPrimary, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> RecordingsShimmer()
                uiState.filteredRecordings.isEmpty() -> {
                    Text(
                        "No recordings yet",
                        color = PlexTextTertiary, fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(uiState.filteredRecordings) { recording ->
                            RecordingPosterCard(
                                recording = recording,
                                onClick = { onNavigatePlayer(recording.id ?: "") },
                                onLongPress = { deleteTarget = recording }
                            )
                        }
                    }
                }
            }

            // Toast
            if (uiState.toastMessage != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .background(PlexCard, RoundedCornerShape(8.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(uiState.toastMessage ?: "", fontSize = 14.sp, color = PlexTextPrimary)
                }
            }
        }
    }

    // Delete confirmation dialog
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = PlexCard,
            title = {
                androidx.compose.material3.Text(
                    "Delete Recording",
                    color = PlexTextPrimary, fontWeight = FontWeight.Bold
                )
            },
            text = {
                androidx.compose.material3.Text(
                    "Delete \"${deleteTarget?.title ?: ""}\"? This cannot be undone.",
                    color = PlexTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget?.id?.let { viewModel.deleteRecording(it) }
                    deleteTarget = null
                }) {
                    androidx.compose.material3.Text("Delete", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    androidx.compose.material3.Text("Cancel", color = PlexTextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RecordingPosterCard(
    recording: Recording,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val posterUrl = rememberPosterUrl(recording.title)
    val progress = if (recording.duration > 0 && recording.resumePositionSec > 0) {
        (recording.resumePositionSec.toFloat() / recording.duration).coerceIn(0f, 1f)
    } else 0f
    val isActive = recording.status?.lowercase() == "recording" || recording.status?.lowercase() == "active"

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU
                ) {
                    onLongPress(); true
                } else false
            },
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = PlexCard.copy(alpha = 0.80f),
            focusedContainerColor = PlexCard
        ),
        border = CardDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Poster artwork
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = recording.title,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else if (!recording.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = recording.posterUrl,
                    contentDescription = recording.title,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // Dark gradient at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth().fillMaxHeight(0.55f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // Content at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    recording.title ?: "",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = PlexTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (!recording.episodeTitle.isNullOrBlank()) {
                    Text(
                        recording.episodeTitle,
                        fontSize = 12.sp, color = PlexTextSecondary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                // Status badge
                if (isActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            Modifier.size(6.dp).clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Text("Recording...", fontSize = 12.sp, color = Color(0xFFEF4444))
                    }
                } else if (recording.status?.lowercase() == "completed") {
                    Text("Completed", fontSize = 12.sp, color = PlexTextTertiary)
                }
                // Progress bar
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = PlexTextPrimary,
                        trackColor = PlexTextTertiary.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingsShimmer() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "a"
    )
    Row(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .width(140.dp).height(210.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PlexCard.copy(alpha = alpha))
            )
        }
    }
}
