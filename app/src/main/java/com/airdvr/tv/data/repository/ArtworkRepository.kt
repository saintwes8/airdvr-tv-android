package com.airdvr.tv.data.repository

import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.SearchResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Per-process cache + fetcher for show/movie artwork looked up by title.
 * Maps title -> poster URL and title -> backdrop URL. Misses are cached as
 * empty strings to avoid hammering the API.
 */
object ArtworkRepository {

    private val api = ApiClient.publicApi

    // title -> poster URL (empty string = known miss)
    private val posterCache = HashMap<String, String>()
    // title -> backdrop URL (empty string = known miss)
    private val backdropCache = HashMap<String, String>()
    private val lock = Mutex()

    /** Get cached poster (sync). Returns null if not cached at all. */
    fun cachedPoster(title: String): String? = posterCache[title.normalize()]
    fun cachedBackdrop(title: String): String? = backdropCache[title.normalize()]

    /** Fetch poster URL for a title, populating both poster + backdrop cache. */
    suspend fun fetchPoster(title: String): String? = fetch(title)?.first

    /** Fetch backdrop URL for a title, populating both poster + backdrop cache. */
    suspend fun fetchBackdrop(title: String): String? = fetch(title)?.second

    /** Fetch popular artwork results from /api/artwork/popular. */
    suspend fun fetchPopular(): List<SearchResult> {
        return try {
            val resp = api.getPopularArtwork()
            if (resp.isSuccessful) {
                resp.body()?.results ?: emptyList()
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetch(title: String): Pair<String?, String?>? {
        val key = title.normalize()
        if (key.isBlank()) return null
        // Cache hit (including known-miss empty strings)
        posterCache[key]?.let { posterUrl ->
            val backdropUrl = backdropCache[key].orEmpty()
            return posterUrl.ifBlank { null } to backdropUrl.ifBlank { null }
        }
        return lock.withLock {
            // Re-check inside the lock
            posterCache[key]?.let { posterUrl ->
                val backdropUrl = backdropCache[key].orEmpty()
                return@withLock posterUrl.ifBlank { null } to backdropUrl.ifBlank { null }
            }
            try {
                val resp = api.getArtwork(title)
                if (resp.isSuccessful) {
                    val body = resp.body()
                    val poster = body?.posterUrl.orEmpty()
                    val backdrop = body?.backdropUrl.orEmpty()
                    posterCache[key] = poster
                    backdropCache[key] = backdrop
                    poster.ifBlank { null } to backdrop.ifBlank { null }
                } else {
                    posterCache[key] = ""
                    backdropCache[key] = ""
                    null to null
                }
            } catch (_: Exception) {
                posterCache[key] = ""
                backdropCache[key] = ""
                null to null
            }
        }
    }

    private fun String.normalize(): String = trim().lowercase()
}
