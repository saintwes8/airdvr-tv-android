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
import com.airdvr.tv.data.models.EpgProgram
import com.airdvr.tv.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ProgramCard(
    program: EpgProgram,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1.0f,
        label = "programCardScale"
    )

    val primaryCategory = program.category?.firstOrNull()?.lowercase() ?: ""
    val categoryColor = when {
        primaryCategory.contains("news") -> AirDVRBlue
        primaryCategory.contains("sport") -> AirDVRGreen
        primaryCategory.contains("movie") || primaryCategory.contains("film") -> AirDVRPurple
        else -> AirDVROrange
    }

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val startFormatted = timeFormat.format(Date(program.startTime * 1000))
    val endFormatted = timeFormat.format(Date(program.endTime * 1000))

    Card(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .height(72.dp),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = AirDVRCard,
            focusedContainerColor = AirDVRCard.copy(alpha = 0.9f),
            pressedContainerColor = AirDVRBlue.copy(alpha = 0.2f)
        ),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))),
            focusedBorder = Border(BorderStroke(2.dp, AirDVRFocusRing))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = program.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AirDVRTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$startFormatted – $endFormatted",
                    fontSize = 11.sp,
                    color = AirDVRTextSecondary
                )

                if (primaryCategory.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(categoryColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = program.category?.firstOrNull() ?: "",
                            fontSize = 10.sp,
                            color = categoryColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
