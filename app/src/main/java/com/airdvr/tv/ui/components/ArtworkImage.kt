package com.airdvr.tv.ui.components

import androidx.compose.runtime.*
import com.airdvr.tv.data.repository.ArtworkRepository

/**
 * Async fetch of poster URL for a title. Returns null until loaded; null also
 * means "not available". Cached across calls so re-renders are instant.
 */
@Composable
fun rememberPosterUrl(title: String?): String? {
    if (title.isNullOrBlank()) return null
    var url by remember(title) {
        mutableStateOf(ArtworkRepository.cachedPoster(title)?.ifBlank { null })
    }
    LaunchedEffect(title) {
        if (ArtworkRepository.cachedPoster(title) == null) {
            url = ArtworkRepository.fetchPoster(title)
        }
    }
    return url
}

/** Async fetch of backdrop URL for a title. */
@Composable
fun rememberBackdropUrl(title: String?): String? {
    if (title.isNullOrBlank()) return null
    var url by remember(title) {
        mutableStateOf(ArtworkRepository.cachedBackdrop(title)?.ifBlank { null })
    }
    LaunchedEffect(title) {
        if (ArtworkRepository.cachedBackdrop(title) == null) {
            url = ArtworkRepository.fetchBackdrop(title)
        }
    }
    return url
}
