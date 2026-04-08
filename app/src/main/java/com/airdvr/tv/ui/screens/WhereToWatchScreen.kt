package com.airdvr.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.airdvr.tv.data.models.ArtworkItem
import com.airdvr.tv.data.models.WatchProvider
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.WhereToWatchViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WhereToWatchScreen(
    onBack: () -> Unit,
    onNavigateLiveTV: (String) -> Unit,
    viewModel: WhereToWatchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

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
            Text("Find", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary)
        }

        // Search field
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = {
                androidx.compose.material3.Text("Search for any show or movie...", color = PlexTextTertiary, fontSize = 16.sp)
            },
            leadingIcon = { Icon(Icons.Filled.Search, "Search", tint = PlexTextTertiary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PlexTextPrimary.copy(alpha = 0.4f),
                unfocusedBorderColor = PlexBorder,
                focusedTextColor = PlexTextPrimary,
                unfocusedTextColor = PlexTextPrimary,
                cursorColor = PlexTextPrimary,
                focusedContainerColor = PlexSurface,
                unfocusedContainerColor = PlexSurface
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Results
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        color = PlexTextPrimary,
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                        strokeWidth = 2.dp
                    )
                }
                uiState.error != null -> {
                    Text(
                        uiState.error ?: "", color = PlexTextSecondary,
                        fontSize = 14.sp, modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.query.isBlank() -> {
                    if (uiState.isLoadingPopular) {
                        CircularProgressIndicator(
                            color = PlexTextPrimary,
                            modifier = Modifier.align(Alignment.Center).size(32.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (uiState.popular.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.Search, contentDescription = null,
                                tint = PlexBorder, modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("Search for any show or movie", fontSize = 16.sp, color = PlexTextTertiary)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(uiState.popular) { item ->
                                PopularPosterCard(item = item) {
                                    item.title?.let { viewModel.searchTitle(it) }
                                }
                            }
                        }
                    }
                }
                uiState.results.isEmpty() -> {
                    Text(
                        "Not available for streaming",
                        color = PlexTextTertiary, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.results) { provider ->
                            ProviderResultCard(provider = provider, onTune = { onNavigateLiveTV(it) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PopularPosterCard(item: ArtworkItem, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = PlexCard,
            focusedContainerColor = PlexCard
        ),
        border = CardDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(PlexSurface))
            }
            // Bottom gradient for title legibility
            Box(
                modifier = Modifier
                    .fillMaxWidth().fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )
            Text(
                item.title ?: "",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProviderResultCard(
    provider: WatchProvider,
    onTune: (String) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = {
            val ch = provider.channelNumber
            if (provider.available && !ch.isNullOrBlank()) onTune(ch)
        },
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = PlexCard.copy(alpha = 0.7f),
            focusedContainerColor = PlexCard.copy(alpha = 0.9f)
        ),
        border = CardDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!provider.channelNumber.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .background(PlexBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "CH ${provider.channelNumber}",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary
                            )
                        }
                    }
                    Text(
                        provider.name ?: provider.guideName ?: "Unknown",
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary
                    )
                }
                if (!provider.startTime.isNullOrBlank()) {
                    Text(provider.startTime, fontSize = 13.sp, color = PlexTextSecondary)
                }
                if (!provider.description.isNullOrBlank()) {
                    Text(
                        provider.description, fontSize = 12.sp, color = PlexTextTertiary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            val ch = provider.channelNumber
            if (provider.available && !ch.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isFocused) PlexBorder else PlexCard, RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Watch", color = PlexTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                Text(
                    if (provider.available) "Available" else "Unavailable",
                    fontSize = 12.sp, color = PlexTextTertiary
                )
            }
        }
    }
}
