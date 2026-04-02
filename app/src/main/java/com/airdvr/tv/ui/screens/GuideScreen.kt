package com.airdvr.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.models.EpgProgram
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.GuideViewModel
import java.text.SimpleDateFormat
import java.util.*

private val CHANNEL_LABEL_WIDTH: Dp = 120.dp
private val CELL_WIDTH_PER_30_MIN: Dp = 200.dp
private val ROW_HEIGHT: Dp = 56.dp
private val TIME_HEADER_HEIGHT: Dp = 40.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GuideScreen(
    onNavigateLiveTV: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: GuideViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val now = remember { System.currentTimeMillis() }

    // Build time slots for the header (current hour, rounded down to 30 min, +6 hours)
    val timeSlots: List<Long> = remember(now) {
        val startMs = (now / (30 * 60 * 1000L)) * (30 * 60 * 1000L)
        (0 until 24).map { startMs + it * 30 * 60 * 1000L }
    }

    // Shared horizontal scroll state for time header + grid rows
    val horizontalScrollState = rememberScrollState()

    // Program detail dialog
    val selectedProgram = uiState.selectedProgram
    val selectedChannel = uiState.selectedChannel
    if (selectedProgram != null && selectedChannel != null) {
        ProgramDetailDialog(
            program = selectedProgram,
            channel = selectedChannel,
            onWatchNow = {
                viewModel.dismissProgramDetail()
                onNavigateLiveTV(selectedChannel.guideNumber)
            },
            onDismiss = { viewModel.dismissProgramDetail() }
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AirDVRTextPrimary
                )
            }
            androidx.tv.material3.Text(
                text = "TV Guide",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AirDVRTextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            androidx.tv.material3.Text(
                text = timeFormat.format(Date(now)),
                fontSize = 14.sp,
                color = AirDVRTextSecondary
            )
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AirDVRBlue)
            }
            return@Column
        }

        if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.tv.material3.Text(
                    text = uiState.error!!,
                    color = Color.Red,
                    fontSize = 16.sp
                )
            }
            return@Column
        }

        // Time header row (channel label + scrollable time slots)
        Row(modifier = Modifier.fillMaxWidth().height(TIME_HEADER_HEIGHT)) {
            // Empty cell above channel labels
            Box(
                modifier = Modifier
                    .width(CHANNEL_LABEL_WIDTH)
                    .fillMaxHeight()
                    .background(AirDVRCard)
            )
            // Time slots header
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(AirDVRCard)
                    .horizontalScroll(horizontalScrollState)
            ) {
                timeSlots.forEach { slotMs ->
                    val isNow = slotMs <= now && now < slotMs + 30 * 60 * 1000L
                    Box(
                        modifier = Modifier
                            .width(CELL_WIDTH_PER_30_MIN)
                            .fillMaxHeight()
                            .background(if (isNow) AirDVRBlue.copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                width = if (isNow) 0.dp else 0.dp,
                                color = Color.Transparent
                            )
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = timeFormat.format(Date(slotMs)),
                            fontSize = 12.sp,
                            color = if (isNow) AirDVROrange else AirDVRTextSecondary,
                            fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Channel rows
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(uiState.channels) { channel ->
                val programs = uiState.programsByChannel[channel.guideNumber] ?: emptyList()
                GuideRow(
                    channel = channel,
                    programs = programs,
                    timeSlots = timeSlots,
                    now = now,
                    horizontalScrollState = horizontalScrollState,
                    onProgramClick = { program ->
                        viewModel.selectProgram(program, channel)
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GuideRow(
    channel: Channel,
    programs: List<EpgProgram>,
    timeSlots: List<Long>,
    now: Long,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    onProgramClick: (EpgProgram) -> Unit
) {
    val guideStartMs = timeSlots.firstOrNull() ?: 0L
    val minsPerDp = 30f / CELL_WIDTH_PER_30_MIN.value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
    ) {
        // Channel label (fixed)
        Box(
            modifier = Modifier
                .width(CHANNEL_LABEL_WIDTH)
                .fillMaxHeight()
                .background(AirDVRCard)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                androidx.tv.material3.Text(
                    text = channel.guideNumber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AirDVROrange
                )
                androidx.tv.material3.Text(
                    text = channel.guideName,
                    fontSize = 11.sp,
                    color = AirDVRTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Programs scrollable row
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(horizontalScrollState)
        ) {
            val visibleStart = guideStartMs / 1000L
            val visibleEnd = (timeSlots.lastOrNull() ?: guideStartMs) / 1000L + 30 * 60L

            // Filter and sort programs in the visible range
            val visiblePrograms = programs
                .filter { it.endTime > visibleStart && it.startTime < visibleEnd }
                .sortedBy { it.startTime }

            // Fill gaps with empty cells if needed
            var currentTime = visibleStart
            val totalSlotDuration = timeSlots.size * 30 * 60L
            val totalWidthDp = CELL_WIDTH_PER_30_MIN * timeSlots.size

            if (visiblePrograms.isEmpty()) {
                // Show empty placeholder for entire row
                Box(
                    modifier = Modifier
                        .width(totalWidthDp)
                        .fillMaxHeight()
                        .background(AirDVRNavy),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "No guide data",
                        fontSize = 12.sp,
                        color = AirDVRTextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            } else {
                // Leading gap before first program
                if (visiblePrograms.first().startTime > currentTime) {
                    val gapSecs = visiblePrograms.first().startTime - currentTime
                    val gapWidth = (gapSecs / 60f / 30f * CELL_WIDTH_PER_30_MIN.value).dp
                    Box(
                        modifier = Modifier
                            .width(gapWidth)
                            .fillMaxHeight()
                            .background(AirDVRNavy)
                    )
                    currentTime = visiblePrograms.first().startTime
                }

                visiblePrograms.forEach { program ->
                    val durationSecs = (program.endTime - program.startTime).coerceAtLeast(1L)
                    val durationMins = durationSecs / 60f
                    val cellWidth = (durationMins / 30f * CELL_WIDTH_PER_30_MIN.value).coerceAtLeast(4f).dp

                    val isNow = program.startTime <= now / 1000L && now / 1000L < program.endTime
                    val primaryCat = program.category?.firstOrNull()?.lowercase() ?: ""
                    val catColor = when {
                        primaryCat.contains("news") -> AirDVRBlue
                        primaryCat.contains("sport") -> AirDVRGreen
                        primaryCat.contains("movie") -> AirDVRPurple
                        else -> AirDVRCard
                    }

                    var focused by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .width(cellWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isNow -> catColor.copy(alpha = 0.5f)
                                    else -> catColor.copy(alpha = 0.25f)
                                }
                            )
                            .border(
                                width = if (isNow) 1.dp else 0.dp,
                                color = if (isNow) AirDVROrange else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                    ) {
                        // Clickable surface
                        androidx.tv.material3.Surface(
                            onClick = { onProgramClick(program) },
                            modifier = Modifier.fillMaxSize(),
                            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
                                shape = RoundedCornerShape(4.dp)
                            ),
                            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = AirDVRFocusRing.copy(alpha = 0.4f)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                androidx.tv.material3.Text(
                                    text = program.title,
                                    fontSize = 12.sp,
                                    color = AirDVRTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    currentTime = program.endTime
                }
            }
        }
    }
}

@Composable
private fun ProgramDetailDialog(
    program: EpgProgram,
    channel: Channel,
    onWatchNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val startStr = timeFormat.format(Date(program.startTime * 1000))
    val endStr = timeFormat.format(Date(program.endTime * 1000))

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(480.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = AirDVRCard)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = program.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AirDVRTextPrimary
                )
                if (!program.episodeTitle.isNullOrBlank()) {
                    Text(
                        text = program.episodeTitle,
                        fontSize = 16.sp,
                        color = AirDVRTextSecondary
                    )
                }
                Text(
                    text = "${channel.guideNumber} ${channel.guideName}  •  $startStr – $endStr",
                    fontSize = 13.sp,
                    color = AirDVRTextSecondary
                )
                if (!program.summary.isNullOrBlank()) {
                    Text(
                        text = program.summary,
                        fontSize = 14.sp,
                        color = AirDVRTextPrimary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onWatchNow,
                        colors = ButtonDefaults.buttonColors(containerColor = AirDVRBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Watch Now", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = { /* Coming soon */ },
                        shape = RoundedCornerShape(8.dp),
                        border = ButtonDefaults.outlinedButtonBorder
                    ) {
                        Text("Record (Coming Soon)", color = AirDVRTextSecondary)
                    }
                }
            }
        }
    }
}
