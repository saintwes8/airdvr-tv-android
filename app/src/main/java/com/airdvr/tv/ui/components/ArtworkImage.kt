package com.airdvr.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.airdvr.tv.data.repository.ArtworkRepository
import com.airdvr.tv.util.TeamLogos
import com.airdvr.tv.util.detectLeagueFromTitle
import com.airdvr.tv.util.shouldSkipTmdbForSports
import kotlinx.coroutines.delay

/**
 * Async fetch of poster URL for a title with 500ms debounce. Returns null
 * until loaded; null also means "not available". Cached across calls so
 * re-renders are instant. Returns null for generic sports titles ("NBA
 * Basketball" etc.) where TMDB matches the wrong art — callers should
 * render [SportsTitleArtwork] instead.
 */
@Composable
fun rememberPosterUrl(title: String?): String? {
    if (title.isNullOrBlank()) return null
    if (shouldSkipTmdbForSports(title)) return null
    var url by remember(title) {
        mutableStateOf(ArtworkRepository.cachedPoster(title)?.ifBlank { null })
    }
    LaunchedEffect(title) {
        if (ArtworkRepository.cachedPoster(title) == null) {
            delay(500L)
            url = ArtworkRepository.fetchPoster(title)
        }
    }
    return url
}

/** Async fetch of backdrop URL for a title with 500ms debounce. */
@Composable
fun rememberBackdropUrl(title: String?): String? {
    if (title.isNullOrBlank()) return null
    if (shouldSkipTmdbForSports(title)) return null
    var url by remember(title) {
        mutableStateOf(ArtworkRepository.cachedBackdrop(title)?.ifBlank { null })
    }
    LaunchedEffect(title) {
        if (ArtworkRepository.cachedBackdrop(title) == null) {
            delay(500L)
            url = ArtworkRepository.fetchBackdrop(title)
        }
    }
    return url
}

/**
 * Artwork tile for a generic sports title (e.g. "NBA Basketball"). Renders
 * a dark gradient background with the league logo centered. Use as a
 * replacement for AsyncImage when [shouldSkipTmdbForSports] returns true.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsTitleArtwork(
    title: String?,
    modifier: Modifier = Modifier
) {
    val league = detectLeagueFromTitle(title)
    val leagueLogo = league?.let { TeamLogos.leagueUrl(it) }
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF0E1622), Color(0xFF000000))
    )
    Box(
        modifier = modifier.background(gradient),
        contentAlignment = Alignment.Center
    ) {
        if (leagueLogo != null) {
            AsyncImage(
                model = leagueLogo,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(0.55f),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                (league ?: title?.take(3) ?: "").uppercase(),
                fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFB0B7BF)
            )
        }
    }
}
