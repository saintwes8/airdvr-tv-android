package com.airdvr.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    val categories = listOf(
        RecordingCategory.ALL to "All",
        RecordingCategory.TV_SHOWS to "TV Shows",
        RecordingCategory.MOVIES to "Movies",
        RecordingCategory.SPORTS to "Sports"
    )

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
            Text(
                text = "Recordings",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AirDVRTextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${uiState.filteredRecordings.size} recordings",
                fontSize = 14.sp,
                color = AirDVRTextSecondary
            )
        }

        // Category filter tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AirDVRNavy)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { (category, label) ->
                val isSelected = uiState.selectedCategory == category
                androidx.tv.material3.Surface(
                    onClick = { viewModel.setCategory(category) },
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
                        shape = RoundedCornerShape(20.dp)
                    ),
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) AirDVRBlue else AirDVRCard,
                        focusedContainerColor = AirDVRBlue.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.padding(horizontal = 0.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Color.White else AirDVRTextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Content area
        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AirDVRBlue)
                    }
                }
                uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = uiState.error!!, color = Color.Red, fontSize = 16.sp)
                    }
                }
                uiState.filteredRecordings.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No recordings found",
                            color = AirDVRTextSecondary,
                            fontSize = 16.sp
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 190.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(uiState.filteredRecordings) { recording ->
                            RecordingCard(
                                recording = recording,
                                onClick = { onNavigatePlayer(recording.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
