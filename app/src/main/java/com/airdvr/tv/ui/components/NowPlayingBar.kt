package com.airdvr.tv.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.models.EpgProgram
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.util.Constants
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NowPlayingBar(
    channel: Channel,
    currentProgram: EpgProgram?,
    visible: Boolean,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-hide after delay
    LaunchedEffect(visible) {
        if (visible) {
            delay(Constants.CHANNEL_OVERLAY_HIDE_DELAY_MS)
            onHide()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            AirDVRNavy.copy(alpha = 0.85f),
                            AirDVRNavy.copy(alpha = 0.95f)
                        )
                    )
                )
                .padding(horizontal = 32.dp, vertical = 20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Channel info row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Channel number badge
                    Box(
                        modifier = Modifier
                            .background(AirDVRBlue, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = channel.guideNumber,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Column {
                        Text(
                            text = channel.guideName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AirDVRTextPrimary
                        )
                        if (currentProgram != null) {
                            Text(
                                text = currentProgram.title,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = AirDVRTextPrimary
                            )
                        }
                    }
                }

                // Program progress bar
                if (currentProgram != null) {
                    val now = System.currentTimeMillis() / 1000L
                    val duration = currentProgram.endTime - currentProgram.startTime
                    val elapsed = (now - currentProgram.startTime).coerceAtLeast(0L)
                    val progress = if (duration > 0) {
                        (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                    val startStr = timeFormat.format(Date(currentProgram.startTime * 1000))
                    val endStr = timeFormat.format(Date(currentProgram.endTime * 1000))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = AirDVROrange,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = startStr,
                                fontSize = 12.sp,
                                color = AirDVRTextSecondary
                            )
                            Text(
                                text = endStr,
                                fontSize = 12.sp,
                                color = AirDVRTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
