package com.airdvr.tv.ui.screens

import android.view.KeyEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.airdvr.tv.data.models.RecordingSchedule
import com.airdvr.tv.data.models.StorageWarning
import com.airdvr.tv.ui.components.rememberPosterUrl
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.RecordingCategory
import com.airdvr.tv.ui.viewmodels.RecordingGroup
import com.airdvr.tv.ui.viewmodels.RecordingsTab
import com.airdvr.tv.ui.viewmodels.RecordingsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RecordingsScreen(
    onNavigatePlayer: (recordingId: String, streamUrl: String?) -> Unit,
    onBack: () -> Unit,
    viewModel: RecordingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Recording?>(null) }
    var cancelTarget by remember { mutableStateOf<RecordingSchedule?>(null) }
    var optionsTarget by remember { mutableStateOf<Recording?>(null) }
    var retentionTarget by remember { mutableStateOf<Recording?>(null) }
    var deleteCloudTarget by remember { mutableStateOf<Recording?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(PlexBg)
    ) {
        // Storage warnings banner
        uiState.storageWarnings.firstOrNull()?.let { warning ->
            StorageWarningBanner(warning)
        }

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = onBack,
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = PlexCard
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = PlexTextPrimary,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Text("Recordings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary)
        }

        // Section 1: Top toggle — Recordings | Upcoming
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TabPill(
                label = "Recordings",
                isSelected = uiState.selectedTab == RecordingsTab.RECORDINGS,
                onClick = { viewModel.setTab(RecordingsTab.RECORDINGS) }
            )
            TabPill(
                label = "Upcoming",
                isSelected = uiState.selectedTab == RecordingsTab.UPCOMING,
                onClick = { viewModel.setTab(RecordingsTab.UPCOMING) }
            )
        }

        // Section 2: Category filter chips (only for Recordings tab)
        if (uiState.selectedTab == RecordingsTab.RECORDINGS) {
            val categories = listOf(
                "All" to RecordingCategory.ALL,
                "TV Shows" to RecordingCategory.TV_SHOWS,
                "Movies" to RecordingCategory.MOVIES,
                "Sports" to RecordingCategory.SPORTS
            )
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                categories.forEach { (label, category) ->
                    FilterChip(
                        label = label,
                        isSelected = uiState.selectedCategory == category,
                        onClick = { viewModel.setCategory(category) }
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                RecordingsShimmer()
            } else if (uiState.selectedTab == RecordingsTab.UPCOMING) {
                // Upcoming tab — show schedules
                if (uiState.schedules.isEmpty()) {
                    Text(
                        "No upcoming recordings",
                        color = PlexTextTertiary, fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(uiState.schedules, key = { it.id ?: "" }) { schedule ->
                            ScheduleCard(
                                schedule = schedule,
                                onCancel = { cancelTarget = schedule }
                            )
                        }
                    }
                }
            } else {
                // Recordings tab
                val expandedKey = uiState.expandedGroupKey
                val expandedGroup = expandedKey?.let { k -> uiState.groups.find { it.key == k } }
                if (uiState.groups.isEmpty()) {
                    Text(
                        "No recordings yet",
                        color = PlexTextTertiary, fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (expandedGroup != null) {
                    // Expanded view — episodes of a single show
                    ExpandedGroupView(
                        group = expandedGroup,
                        onClose = { viewModel.closeExpandedGroup() },
                        onEpisodeClick = { recording ->
                            viewModel.playRecording(recording) { url ->
                                onNavigatePlayer(recording.id ?: "", url)
                            }
                        },
                        onEpisodeLongPress = { optionsTarget = it },
                        loadingPlaybackId = uiState.loadingPlaybackId,
                        viewModel = viewModel
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(uiState.groups, key = { it.key }) { group ->
                            if (group.isShow) {
                                ShowGroupCard(
                                    group = group,
                                    onClick = { viewModel.toggleGroupExpanded(group.key) }
                                )
                            } else {
                                val rec = group.recordings.first()
                                RecordingPosterCard(
                                    recording = rec,
                                    isLoadingPlayback = uiState.loadingPlaybackId == rec.id,
                                    effectiveStatus = viewModel.effectiveStatus(rec),
                                    onClick = {
                                        viewModel.playRecording(rec) { url ->
                                            onNavigatePlayer(rec.id ?: "", url)
                                        }
                                    },
                                    onLongPress = { optionsTarget = rec }
                                )
                            }
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

    // Delete recording dialog
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

    // Cancel schedule dialog
    if (cancelTarget != null) {
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            containerColor = PlexCard,
            title = {
                androidx.compose.material3.Text(
                    "Cancel Schedule",
                    color = PlexTextPrimary, fontWeight = FontWeight.Bold
                )
            },
            text = {
                androidx.compose.material3.Text(
                    "Cancel recording of \"${cancelTarget?.title ?: ""}\"?",
                    color = PlexTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    cancelTarget?.id?.let { viewModel.cancelSchedule(it) }
                    cancelTarget = null
                }) {
                    androidx.compose.material3.Text("Cancel Schedule", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelTarget = null }) {
                    androidx.compose.material3.Text("Keep", color = PlexTextSecondary)
                }
            }
        )
    }

    // Recording options dialog (long-press)
    if (optionsTarget != null) {
        val rec = optionsTarget!!
        val isCloud = rec.storageType?.lowercase() == "cloud"
        AlertDialog(
            onDismissRequest = { optionsTarget = null },
            containerColor = PlexCard,
            title = {
                androidx.compose.material3.Text(
                    rec.title ?: "Recording",
                    color = PlexTextPrimary, fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    androidx.compose.material3.Text(
                        if (isCloud) "Storage: Cloud" else "Storage: Local",
                        color = PlexTextSecondary, fontSize = 13.sp
                    )
                    if (!isCloud && !rec.deviceName.isNullOrBlank()) {
                        androidx.compose.material3.Text(
                            "Device: ${rec.deviceName}",
                            color = PlexTextSecondary, fontSize = 13.sp
                        )
                    }
                    rec.fileSizeMb?.let {
                        androidx.compose.material3.Text(
                            "Size: ${formatFileSize(it)}",
                            color = PlexTextSecondary, fontSize = 13.sp
                        )
                    }
                    if (rec.duration > 0) {
                        androidx.compose.material3.Text(
                            "Duration: ${formatDurationMinutes(rec.duration)}",
                            color = PlexTextSecondary, fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (isCloud) {
                        TextButton(onClick = {
                            retentionTarget = rec
                            optionsTarget = null
                        }) {
                            androidx.compose.material3.Text("Change Retention", color = PlexTextPrimary)
                        }
                        TextButton(onClick = {
                            deleteCloudTarget = rec
                            optionsTarget = null
                        }) {
                            androidx.compose.material3.Text("Delete from Cloud", color = Color(0xFFF59E0B))
                        }
                        TextButton(onClick = {
                            deleteTarget = rec
                            optionsTarget = null
                        }) {
                            androidx.compose.material3.Text("Delete Everywhere", color = Color(0xFFEF4444))
                        }
                    } else {
                        TextButton(onClick = {
                            viewModel.showToast("Coming soon")
                            optionsTarget = null
                        }) {
                            androidx.compose.material3.Text("Upload to Cloud", color = PlexTextPrimary)
                        }
                        TextButton(onClick = {
                            deleteTarget = rec
                            optionsTarget = null
                        }) {
                            androidx.compose.material3.Text("Delete", color = Color(0xFFEF4444))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { optionsTarget = null }) {
                    androidx.compose.material3.Text("Cancel", color = PlexTextSecondary)
                }
            }
        )
    }

    // Retention picker dialog
    if (retentionTarget != null) {
        val rec = retentionTarget!!
        val options = listOf<Pair<String, Int?>>(
            "7 days" to 7,
            "14 days" to 14,
            "30 days" to 30,
            "60 days" to 60,
            "90 days" to 90,
            "Never" to null
        )
        AlertDialog(
            onDismissRequest = { retentionTarget = null },
            containerColor = PlexCard,
            title = {
                androidx.compose.material3.Text(
                    "Auto-delete after",
                    color = PlexTextPrimary, fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    options.forEach { (label, days) ->
                        TextButton(onClick = {
                            rec.id?.let { viewModel.setRecordingRetention(it, days) }
                            retentionTarget = null
                        }) {
                            androidx.compose.material3.Text(label, color = PlexTextPrimary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { retentionTarget = null }) {
                    androidx.compose.material3.Text("Cancel", color = PlexTextSecondary)
                }
            }
        )
    }

    // Delete-from-cloud confirmation dialog
    if (deleteCloudTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteCloudTarget = null },
            containerColor = PlexCard,
            title = {
                androidx.compose.material3.Text(
                    "Delete from Cloud",
                    color = PlexTextPrimary, fontWeight = FontWeight.Bold
                )
            },
            text = {
                androidx.compose.material3.Text(
                    "Remove \"${deleteCloudTarget?.title ?: ""}\" from cloud storage? Local copy (if any) will be kept.",
                    color = PlexTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteCloudTarget?.id?.let { viewModel.deleteRecordingCloud(it) }
                    deleteCloudTarget = null
                }) {
                    androidx.compose.material3.Text("Delete", color = Color(0xFFF59E0B))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCloudTarget = null }) {
                    androidx.compose.material3.Text("Cancel", color = PlexTextSecondary)
                }
            }
        )
    }
}

// ─── Storage warning banner ─────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StorageWarningBanner(warning: StorageWarning) {
    val (bg, fg) = when (warning.level?.lowercase()) {
        "critical" -> Color(0xFFEF4444).copy(alpha = 0.18f) to Color(0xFFEF4444)
        "warning" -> Color(0xFFF59E0B).copy(alpha = 0.18f) to Color(0xFFF59E0B)
        else -> Color(0xFF3B82F6).copy(alpha = 0.18f) to Color(0xFF3B82F6)
    }
    val borderColor = fg.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(fg)
        )
        Text(
            warning.message ?: "",
            fontSize = 13.sp, color = fg,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─── Tab pill (segmented control) ───────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TabPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) PlexTextPrimary else PlexCard,
            focusedContainerColor = if (isSelected) PlexTextPrimary else PlexSurface
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(
                1.dp, if (isSelected) PlexTextPrimary else PlexBorder
            )),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        ),
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) PlexBg else PlexTextSecondary
            )
        }
    }
}

// ─── Filter chip ────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) PlexCard else Color.Transparent,
            focusedContainerColor = PlexCard
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(
                if (isSelected) 1.dp else 0.5.dp,
                if (isSelected) PlexTextPrimary else PlexBorder
            )),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(1.5.dp, PlexTextPrimary))
        ),
        modifier = Modifier.height(30.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = if (isSelected) PlexTextPrimary else PlexTextTertiary
            )
        }
    }
}

// ─── Schedule card ──────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScheduleCard(
    schedule: RecordingSchedule,
    onCancel: () -> Unit
) {
    val isRecording = schedule.status?.lowercase() == "recording" || schedule.status?.lowercase() == "active"
    val timeText = formatScheduleTime(schedule.startTime)

    Card(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU
                ) {
                    onCancel(); true
                } else false
            },
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = PlexCard,
            focusedContainerColor = PlexCard
        ),
        border = CardDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    schedule.title ?: "",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = PlexTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!schedule.channelNumber.isNullOrBlank()) {
                        Text("CH ${schedule.channelNumber}", fontSize = 12.sp, color = PlexTextTertiary)
                    }
                    if (timeText.isNotBlank()) {
                        Text(timeText, fontSize = 12.sp, color = PlexTextSecondary)
                    }
                }
            }
            if (isRecording) {
                val transition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by transition.animateFloat(
                    initialValue = 1f, targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = "pa"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = pulseAlpha))
                    )
                    Text("Recording", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFEF4444))
                }
            } else {
                Box(
                    Modifier
                        .background(Color(0xFFF59E0B).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("Scheduled", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF59E0B))
                }
            }
        }
    }
}

private fun formatScheduleTime(isoTime: String?): String {
    if (isoTime.isNullOrBlank()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val cleaned = isoTime.replace("Z", "+00:00").substringBefore("+").substringBefore(".")
        val date = parser.parse(cleaned) ?: return ""
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = date }
        val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)

        when {
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) &&
                now.get(Calendar.YEAR) == target.get(Calendar.YEAR) -> "Today $timeStr"
            now.get(Calendar.DAY_OF_YEAR) + 1 == target.get(Calendar.DAY_OF_YEAR) &&
                now.get(Calendar.YEAR) == target.get(Calendar.YEAR) -> "Tomorrow $timeStr"
            else -> SimpleDateFormat("EEE MMM d h:mm a", Locale.getDefault()).format(date)
        }
    } catch (_: Exception) { "" }
}

// ─── Show group card (multiple episodes) ────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ShowGroupCard(
    group: RecordingGroup,
    onClick: () -> Unit
) {
    val posterUrl = rememberPosterUrl(group.title)
    val episodeCount = group.recordings.size
    val totalSize = group.totalSizeMb

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f),
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
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = group.title,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else if (!group.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = group.posterUrl,
                    contentDescription = group.title,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            // Stack effect (show it's a group) — layered edges on top-right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .background(Color(0xFF1A6EBD).copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("$episodeCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            // Dark gradient at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth().fillMaxHeight(0.55f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    group.title,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = PlexTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$episodeCount episodes",
                    fontSize = 11.sp, color = PlexTextSecondary
                )
                if (totalSize > 0f) {
                    Text(
                        formatFileSize(totalSize),
                        fontSize = 11.sp, color = PlexTextTertiary
                    )
                }
            }
        }
    }
}

// ─── Expanded group view ────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ExpandedGroupView(
    group: RecordingGroup,
    onClose: () -> Unit,
    onEpisodeClick: (Recording) -> Unit,
    onEpisodeLongPress: (Recording) -> Unit,
    loadingPlaybackId: String?,
    viewModel: RecordingsViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = onClose,
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = PlexCard
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Back to groups",
                    tint = PlexTextPrimary,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    group.title,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary
                )
                Text(
                    "${group.recordings.size} episodes · ${formatFileSize(group.totalSizeMb)}",
                    fontSize = 13.sp, color = PlexTextSecondary
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(group.recordings, key = { it.id ?: "" }) { rec ->
                RecordingPosterCard(
                    recording = rec,
                    isLoadingPlayback = loadingPlaybackId == rec.id,
                    effectiveStatus = viewModel.effectiveStatus(rec),
                    onClick = { onEpisodeClick(rec) },
                    onLongPress = { onEpisodeLongPress(rec) }
                )
            }
        }
    }
}

// ─── Recording poster card ──────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RecordingPosterCard(
    recording: Recording,
    isLoadingPlayback: Boolean,
    effectiveStatus: String,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val posterUrl = rememberPosterUrl(recording.title)
    val progress = if (recording.duration > 0 && recording.resumePositionSec > 0) {
        (recording.resumePositionSec.toFloat() / recording.duration).coerceIn(0f, 1f)
    } else 0f
    val isCloud = recording.storageType?.lowercase() == "cloud"

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
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

            // Storage badge — top-left
            StorageBadge(
                isCloud = isCloud,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
            )

            // Loading spinner overlay when fetching playback URL
            if (isLoadingPlayback) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = PlexTextPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Dark gradient at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth().fillMaxHeight(0.60f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))
                        )
                    )
            )

            // Content at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    recording.title ?: "",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = PlexTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (!recording.episodeTitle.isNullOrBlank()) {
                    Text(
                        recording.episodeTitle,
                        fontSize = 11.sp, color = PlexTextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                // Size + duration metadata
                val metaParts = mutableListOf<String>()
                recording.fileSizeMb?.let { if (it > 0f) metaParts += formatFileSize(it) }
                if (recording.duration > 0) metaParts += formatDurationMinutes(recording.duration)
                if (metaParts.isNotEmpty()) {
                    Text(
                        metaParts.joinToString(" · "),
                        fontSize = 10.sp, color = PlexTextTertiary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                // Status row
                RecordingStatusRow(recording = recording, effectiveStatus = effectiveStatus)
                // Progress bar (for partially watched completed recordings)
                if (progress > 0f && (effectiveStatus == "completed" || effectiveStatus == "complete")) {
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

// ─── Storage badge ──────────────────────────────────────────────────────

@Composable
private fun StorageBadge(isCloud: Boolean, modifier: Modifier = Modifier) {
    val bg = if (isCloud) Color(0xFF22C55E).copy(alpha = 0.85f) else Color(0xFF6B7280).copy(alpha = 0.85f)
    val label = if (isCloud) "CLOUD" else "LOCAL"
    val icon = if (isCloud) Icons.Filled.Cloud else Icons.Filled.Storage
    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            icon, null,
            tint = Color.White,
            modifier = Modifier.size(10.dp)
        )
        Text(
            label,
            fontSize = 9.sp, fontWeight = FontWeight.Bold,
            color = Color.White, letterSpacing = 0.4.sp
        )
    }
}

// ─── Status row ─────────────────────────────────────────────────────────

@Composable
private fun RecordingStatusRow(recording: Recording, effectiveStatus: String) {
    when (effectiveStatus) {
        "recording" -> {
            val transition = rememberInfiniteTransition(label = "rec-pulse")
            val pulse by transition.animateFloat(
                initialValue = 1f, targetValue = 0.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ), label = "rp"
            )
            val elapsed = formatElapsedSinceStart(recording.startEpochSec)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier.size(6.dp).clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = pulse))
                )
                Text(
                    if (elapsed.isNotBlank()) "Recording… $elapsed" else "Recording…",
                    fontSize = 11.sp, color = Color(0xFFEF4444),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        "interrupted" -> {
            Text(
                "Interrupted",
                fontSize = 11.sp, color = Color(0xFFF59E0B),
                fontWeight = FontWeight.Medium
            )
        }
        "failed" -> {
            Column {
                Text(
                    "Failed",
                    fontSize = 11.sp, color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Medium
                )
                if (!recording.errorReason.isNullOrBlank()) {
                    Text(
                        recording.errorReason,
                        fontSize = 10.sp, color = Color(0xFFEF4444).copy(alpha = 0.85f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        "completed", "complete" -> {
            // No status line needed — play affordance is the card itself.
        }
        else -> { /* unknown status — render nothing */ }
    }
}

// ─── Formatting helpers ─────────────────────────────────────────────────

internal fun formatFileSize(mb: Float): String {
    if (mb <= 0f) return ""
    return if (mb >= 1024f) {
        val gb = mb / 1024f
        if (gb >= 10f) "%.0f GB".format(gb) else "%.1f GB".format(gb)
    } else {
        "%.0f MB".format(mb)
    }
}

internal fun formatDurationMinutes(durationSec: Int): String {
    if (durationSec <= 0) return ""
    val totalMin = durationSec / 60
    return if (totalMin >= 60) {
        val h = totalMin / 60
        val m = totalMin % 60
        if (m == 0) "${h}h" else "${h}h ${m}m"
    } else {
        "$totalMin min"
    }
}

private fun formatElapsedSinceStart(startEpochSec: Long): String {
    if (startEpochSec <= 0) return ""
    val now = System.currentTimeMillis() / 1000
    val seconds = (now - startEpochSec).coerceAtLeast(0)
    val totalMin = (seconds / 60).toInt()
    if (totalMin <= 0) return "0 min"
    return if (totalMin >= 60) {
        val h = totalMin / 60
        val m = totalMin % 60
        if (m == 0) "${h}h" else "${h}h ${m}m"
    } else {
        "$totalMin min"
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
