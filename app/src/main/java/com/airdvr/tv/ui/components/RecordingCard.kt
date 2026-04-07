package com.airdvr.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.airdvr.tv.data.models.Recording
import com.airdvr.tv.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RecordingCard(
    recording: Recording,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val progress = if (recording.duration != null && recording.duration > 0 && recording.resumePositionSec != null) {
        (recording.resumePositionSec.toFloat() / recording.duration).coerceIn(0f, 1f)
    } else 0f
    val isUnwatched = recording.resumePositionSec == null || recording.resumePositionSec == 0

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(140.dp)
            .height(210.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = PlexCard.copy(alpha = 0.80f),
            focusedContainerColor = PlexBorder.copy(alpha = 0.95f)
        ),
        border = CardDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Dark gradient overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )

            // Content at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    recording.title ?: "Untitled",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = PlexTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (!recording.episodeTitle.isNullOrBlank()) {
                    Text(
                        recording.episodeTitle,
                        fontSize = 11.sp, color = PlexTextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = PlexTextPrimary,
                        trackColor = PlexTextTertiary.copy(alpha = 0.3f)
                    )
                }
            }

            // Unwatched dot
            if (isUnwatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PlexTextPrimary)
                )
            }
        }
    }
}
