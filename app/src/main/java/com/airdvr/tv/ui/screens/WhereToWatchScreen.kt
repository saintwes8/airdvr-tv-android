package com.airdvr.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.airdvr.tv.data.models.Provider
import com.airdvr.tv.data.models.SearchResult
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

    // Handle back: if detail view open, close it; otherwise navigate back
    BackHandler(enabled = uiState.selectedResult != null) {
        viewModel.clearSelection()
    }

    Box(modifier = Modifier.fillMaxSize().background(PlexBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = {
                    if (uiState.selectedResult != null) viewModel.clearSelection() else onBack()
                }) {
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
                                columns = GridCells.Fixed(4),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(uiState.popular) { item ->
                                    PopularPosterCard(item = item) {
                                        viewModel.selectResult(item)
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
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(uiState.results) { result ->
                                PopularPosterCard(item = result) {
                                    viewModel.selectResult(result)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Detail overlay
        if (uiState.selectedResult != null) {
            DetailOverlay(
                result = uiState.detailResult ?: uiState.selectedResult!!,
                isLoading = uiState.isLoadingDetail,
                onBack = { viewModel.clearSelection() }
            )
        }
    }
}

// ─── Popular / Search Result poster card ─────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PopularPosterCard(item: SearchResult, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(containerColor = PlexCard, focusedContainerColor = PlexCard),
        border = CardDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!item.poster.isNullOrBlank()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(PlexSurface))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth().fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp)
            ) {
                Text(
                    item.title ?: "",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (!item.year.isNullOrBlank()) {
                    Text(item.year, fontSize = 11.sp, color = PlexTextSecondary)
                }
            }
        }
    }
}

// ─── Detail overlay ──────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailOverlay(
    result: SearchResult,
    isLoading: Boolean,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(PlexBg)) {
        // Backdrop background
        if (!result.backdrop.isNullOrBlank()) {
            AsyncImage(
                model = result.backdrop,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f).align(Alignment.TopCenter),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.35f to PlexBg.copy(alpha = 0.7f),
                            0.55f to PlexBg
                        )
                    )
                )
            )
        }

        // Content
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 32.dp)
        ) {
            // Back button
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PlexTextPrimary)
            }
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // Poster
                if (!result.poster.isNullOrBlank()) {
                    AsyncImage(
                        model = result.poster,
                        contentDescription = result.title,
                        modifier = Modifier
                            .width(220.dp).height(330.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PlexCard),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(32.dp))
                }

                // Info column
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            result.title ?: "",
                            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary,
                            maxLines = 3, overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Year + type
                    item {
                        val meta = listOfNotNull(
                            result.year?.takeIf { it.isNotBlank() },
                            result.mediaType?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
                        )
                        if (meta.isNotEmpty()) {
                            Text(
                                meta.joinToString(" \u00B7 "),
                                fontSize = 14.sp, color = PlexTextSecondary
                            )
                        }
                    }
                    // Overview
                    if (!result.overview.isNullOrBlank()) {
                        item {
                            Text(
                                result.overview,
                                fontSize = 14.sp, color = PlexTextSecondary,
                                maxLines = 8, overflow = TextOverflow.Ellipsis,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    if (isLoading) {
                        item {
                            CircularProgressIndicator(
                                color = PlexTextPrimary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        val providers = result.providers
                        val hasAny = !providers?.stream.isNullOrEmpty() ||
                                !providers?.rent.isNullOrEmpty() ||
                                !providers?.buy.isNullOrEmpty()

                        if (hasAny) {
                            if (!providers?.stream.isNullOrEmpty()) {
                                item { ProviderSection("Stream on", providers!!.stream!!) }
                            }
                            if (!providers?.rent.isNullOrEmpty()) {
                                item { ProviderSection("Rent on", providers!!.rent!!) }
                            }
                            if (!providers?.buy.isNullOrEmpty()) {
                                item { ProviderSection("Buy on", providers!!.buy!!) }
                            }
                        } else {
                            item {
                                Text(
                                    "Not available for streaming in your region",
                                    fontSize = 14.sp, color = PlexTextTertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProviderSection(label: String, providers: List<Provider>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(providers) { provider ->
                ProviderChip(provider)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProviderChip(provider: Provider) {
    Row(
        modifier = Modifier
            .background(PlexCard, RoundedCornerShape(8.dp))
            .border(1.dp, PlexBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!provider.logo.isNullOrBlank()) {
            AsyncImage(
                model = provider.logo,
                contentDescription = provider.name,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Fit
            )
        }
        Text(
            provider.name ?: "",
            fontSize = 13.sp, color = PlexTextPrimary,
            maxLines = 1
        )
    }
}
