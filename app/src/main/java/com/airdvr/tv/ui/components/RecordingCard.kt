package com.airdvr.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.airdvr.tv.data.models.Recording
import com.airdvr.tv.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RecordingCard(
    recording: Recording,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        label = "recordingCardScale"
    )

    val posterUrl = recording.posterUrl ?: recording.imageUrl
    val progressFraction = if (recording.duration > 0 && recording.resumePositionSec > 0) {
        (recording.resumePositionSec.toFloat() / recording.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .width(180.dp),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = AirDVRCard,
            focusedContainerColor = AirDVRCard,
            pressedContainerColor = AirDVRBlue.copy(alpha = 0.2f)
        ),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))),
            focusedBorder = Border(BorderStroke(2.dp, AirDVRFocusRing))
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Poster image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(AirDVRNavy)
            ) {
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = recording.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AirDVRCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = recording.title.take(2).uppercase(),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = AirDVRTextSecondary
                        )
                    }
                }

                // Progress bar at bottom of image
                if (progressFraction > 0f) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = AirDVROrange,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            // Title area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = recording.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AirDVRTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!recording.episodeTitle.isNullOrBlank()) {
                    Text(
                        text = recording.episodeTitle,
                        fontSize = 11.sp,
                        color = AirDVRTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
