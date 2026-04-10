package com.airdvr.tv.ui.components

import androidx.compose.runtime.*
import com.airdvr.tv.data.repository.ArtworkRepository
import kotlinx.coroutines.delay

/**
 * Async fetch of poster URL for a title with 500ms debounce. Returns null
 * until loaded; null also means "not available". Cached across calls so
 * re-renders are instant.
 */
@Composable
fun rememberPosterUrl(title: String?): String? {
    if (title.isNullOrBlank()) return null
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
