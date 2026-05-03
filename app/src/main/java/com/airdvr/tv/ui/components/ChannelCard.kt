package com.airdvr.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .height(112.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(shape = RoundedCornerShape(0.dp)),
        colors = CardDefaults.colors(
            containerColor = PlexCard.copy(alpha = 0.80f),
            focusedContainerColor = PlexBorder.copy(alpha = 0.95f)
        ),
        border = CardDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    channel.guideNumber ?: "",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary
                )
                Text(
                    channel.guideName ?: "",
                    fontSize = 14.sp, color = PlexTextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // LIVE dot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
                Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PlexTextSecondary)
            }
        }
    }
}
