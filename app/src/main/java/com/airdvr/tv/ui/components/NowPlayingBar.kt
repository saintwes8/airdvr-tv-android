package com.airdvr.tv.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.models.EpgProgram
import com.airdvr.tv.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NowPlayingBar(
    channel: Channel?,
    programsByChannel: Map<String, List<EpgProgram>>,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    AnimatedVisibility(
        visible = visible && channel != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        if (channel == null) return@AnimatedVisibility
        val now = System.currentTimeMillis() / 1000
        val chNum = channel.guideNumber ?: ""
        val program = programsByChannel[chNum]?.firstOrNull {
            it.startEpochSec <= now && now < it.endEpochSec
        }
        val progress = if (program != null) {
            val dur = (program.endEpochSec - program.startEpochSec).coerceAtLeast(1)
            ((now - program.startEpochSec).toFloat() / dur).coerceIn(0f, 1f)
        } else 0f

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(Color.Black.copy(alpha = 0.80f))
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "$chNum ${channel.guideName ?: ""}",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = PlexTextPrimary
                )
                Text("|", color = PlexTextTertiary, fontSize = 12.sp)
                Text(
                    program?.title ?: "No data",
                    fontSize = 12.sp, color = PlexTextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (program != null) {
                    val s = timeFormat.format(Date(program.startEpochSec * 1000))
                    val e = timeFormat.format(Date(program.endEpochSec * 1000))
                    Text("$s - $e", fontSize = 11.sp, color = PlexTextTertiary)
                }
            }
            if (program != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = PlexTextPrimary,
                    trackColor = PlexTextTertiary.copy(alpha = 0.2f)
                )
            }
        }
    }
}
