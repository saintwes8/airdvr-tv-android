package com.airdvr.tv.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.airdvr.tv.ui.components.RecordingCard
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
                            RecordingCard(
                                recording = recording,
                                onClick = { onNavigatePlayer(recording.id ?: "") }
                            )
                        }
                    }
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
