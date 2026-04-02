package com.airdvr.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelCard(
    channel: Channel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        label = "channelCardScale"
    )

    val borderColor = when {
        isSelected -> AirDVROrange
        isFocused -> AirDVRFocusRing
        else -> Color.Transparent
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .width(160.dp)
            .height(80.dp),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = if (isSelected) AirDVRBlue.copy(alpha = 0.3f) else AirDVRCard,
            focusedContainerColor = AirDVRCard,
            pressedContainerColor = AirDVRBlue.copy(alpha = 0.5f)
        ),
        border = CardDefaults.border(
            border = Border(BorderStroke(2.dp, borderColor)),
            focusedBorder = Border(BorderStroke(2.dp, AirDVRFocusRing))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Channel number
            Text(
                text = channel.guideNumber,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) AirDVROrange else AirDVRTextPrimary,
                modifier = Modifier.width(40.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = channel.guideName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AirDVRTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (channel.hd == 1) {
                    Box(
                        modifier = Modifier
                            .background(AirDVRBlue, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "HD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
