package com.airdvr.tv.data.models

import com.google.gson.annotations.SerializedName

data class GuideResponse(
    @SerializedName("channels") val channels: List<Channel> = emptyList(),
    @SerializedName("programs") val programs: List<EpgProgram> = emptyList()
)

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null
)

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class TunerInfo(
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("local_ip") val localIp: String? = null,
    @SerializedName("model_number") val modelNumber: String? = null,
    @SerializedName("connected") val connected: Boolean = false,
    @SerializedName("last_seen") val lastSeen: Long? = null
)

data class TunersResponse(
    @SerializedName("total") val total: Int = 2,
    @SerializedName("recording") val recording: Int = 0,
    @SerializedName("inUse") val inUse: Int = 0,
    @SerializedName("tuner_count") val tunerCount: Int = 0,
    @SerializedName("tuners") val tuners: List<TunerInfo> = emptyList()
)

data class StorageInfo(
    @SerializedName("used") val used: Long = 0L,
    @SerializedName("total") val total: Long = 0L,
    @SerializedName("free") val free: Long = 0L
)

data class WatchProvider(
    @SerializedName("name") val name: String? = null,
    @SerializedName("logo_url") val logoUrl: String? = null,
    @SerializedName("channel_number") val channelNumber: String? = null,
    @SerializedName("guide_name") val guideName: String? = null,
    @SerializedName("available") val available: Boolean = false,
    @SerializedName("start_time") val startTime: String? = null,
    @SerializedName("description") val description: String? = null
)

data class ArtworkItem(
    @SerializedName("title") val title: String? = null,
    @SerializedName("poster") val posterUrl: String? = null,
    @SerializedName("backdrop") val backdropUrl: String? = null
)

data class ArtworkResponse(
    @SerializedName("title") val title: String? = null,
    @SerializedName("poster") val posterUrl: String? = null,
    @SerializedName("backdrop") val backdropUrl: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("tmdb_id") val tmdbId: String? = null
)

// ── Channel logo info (from /api/channels/logos) ──────────────────────────

data class ChannelLogoInfo(
    @SerializedName("network") val network: String? = null,
    @SerializedName("logoUrl") val logoUrl: String? = null
)

// ── Find / Watch Providers models ─────────────────────────────────────────

data class SearchResult(
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("imdbID") val imdbID: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("backdrop") val backdrop: String? = null,
    @SerializedName("tmdb_id") val tmdbId: Int? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("providers") val providers: TmdbProviders? = null
)

data class TmdbProviders(
    @SerializedName("stream") val stream: List<Provider>? = null,
    @SerializedName("rent") val rent: List<Provider>? = null,
    @SerializedName("buy") val buy: List<Provider>? = null
)

data class Provider(
    @SerializedName("name") val name: String? = null,
    @SerializedName("logo") val logo: String? = null
)

data class WatchProvidersResponse(
    @SerializedName("title") val title: String? = null,
    @SerializedName("results") val results: List<SearchResult>? = null
)

data class PopularArtworkResponse(
    @SerializedName("results") val results: List<SearchResult>? = null,
    @SerializedName("cached") val cached: Boolean? = null
)

// ── User profile / zip code ──────────────────────────────────────────────

data class UserProfile(
    @SerializedName("email") val email: String? = null,
    @SerializedName("zip_code") val zipCode: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("storage_preference") val storagePreference: String? = null,
    @SerializedName("plan") val plan: String? = null
)

data class SetZipRequest(
    @SerializedName("zip_code") val zipCode: String
)

data class StoragePreferenceRequest(
    @SerializedName("storage_preference") val storagePreference: String? = null,
    @SerializedName("cloud_retention_days") val cloudRetentionDays: Int? = null
)

// ── Stream-mode discovery (/api/agent/info) ─────────────────────────────

data class AgentInfo(
    @SerializedName("localStreamUrl") val localStreamUrl: String? = null,
    @SerializedName("remoteStreamUrl") val remoteStreamUrl: String? = null,
    @SerializedName("connected") val connected: Boolean = false
)

// ── Cloud storage usage / warnings ──────────────────────────────────────

data class StorageUsage(
    @SerializedName(value = "used_bytes", alternate = ["used"]) val usedBytes: Long = 0L,
    @SerializedName(value = "total_bytes", alternate = ["total", "limit_bytes"]) val totalBytes: Long = 0L,
    @SerializedName("plan") val plan: String? = null,
    @SerializedName("cloud_retention_days") val cloudRetentionDays: Int? = null
) {
    val percentUsed: Float
        get() = if (totalBytes <= 0L) 0f else (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1.5f)
}

data class StorageWarning(
    @SerializedName("level") val level: String? = null,   // "info", "warning", "critical"
    @SerializedName("message") val message: String? = null,
    @SerializedName("code") val code: String? = null
)

data class StorageWarningsResponse(
    @SerializedName("warnings") val warnings: List<StorageWarning> = emptyList()
)

/** PATCH /api/recordings/{id} body for changing per-recording retention. */
data class RecordingRetentionRequest(
    @SerializedName("cloud_retention_days") val cloudRetentionDays: Int? = null
)

// ── Recording schedule ───────────────────────────────────────────────────

data class RecordingSchedule(
    @SerializedName("id") val id: String? = null,
    @SerializedName("channel_number") val channelNumber: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("start_time") val startTime: String? = null,
    @SerializedName("end_time") val endTime: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("status") val status: String? = null
)

data class ScheduleRequest(
    @SerializedName("channel_number") val channelNumber: String,
    @SerializedName("title") val title: String,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("type") val type: String,
    @SerializedName("storage_preference") val storagePreference: String? = null
)

// ── Recording stream (cloud playback) ───────────────────────────────────

data class RecordingStreamResponse(
    @SerializedName("url") val url: String? = null,
    @SerializedName("expiresAt") val expiresAt: String? = null,
    @SerializedName("storage_type") val storageType: String? = null
)

// ── Recordings list response wrapper ────────────────────────────────────

data class RecordingsResponse(
    @SerializedName("recordings") val recordings: List<Recording> = emptyList()
)

// ── Live sports scores (SportsDataIO via /api/sports/scores/today) ──────

data class SportsScoresResponse(
    @SerializedName("nba") val nba: List<GameScore> = emptyList(),
    @SerializedName("nfl") val nfl: List<GameScore> = emptyList(),
    @SerializedName("mlb") val mlb: List<GameScore> = emptyList(),
    @SerializedName("nhl") val nhl: List<GameScore> = emptyList()
)

data class GameScore(
    @SerializedName("homeTeam") val homeTeam: String? = null,
    @SerializedName("awayTeam") val awayTeam: String? = null,
    @SerializedName("homeScore") val homeScore: Int? = null,
    @SerializedName("awayScore") val awayScore: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("quarter") val quarter: String? = null,
    @SerializedName("timeRemaining") val timeRemaining: String? = null,
    @SerializedName("startTime") val startTime: String? = null,
    @SerializedName("league") val league: String? = null,
    @SerializedName("channel") val channel: String? = null,
    @SerializedName("pointSpread") val pointSpread: Double? = null,
    @SerializedName("overUnder") val overUnder: Double? = null,
    @SerializedName("homeMoneyLine") val homeMoneyLine: Int? = null,
    @SerializedName("awayMoneyLine") val awayMoneyLine: Int? = null,
    @SerializedName("seriesInfo") val seriesInfo: SeriesInfo? = null,
    @SerializedName("homeWinProbability") val homeWinProbability: Float? = null,
    @SerializedName("awayWinProbability") val awayWinProbability: Float? = null
)

data class WinProbabilityResponse(
    @SerializedName("homeWinProbability") val homeWinProbability: Float? = null,
    @SerializedName("awayWinProbability") val awayWinProbability: Float? = null
)

data class SeriesInfo(
    @SerializedName("homeWins") val homeWins: Int? = null,
    @SerializedName("awayWins") val awayWins: Int? = null,
    @SerializedName("gameNumber") val gameNumber: Int? = null,
    @SerializedName("maxLength") val maxLength: Int? = null
)

// ── ESPN site API news (https://site.api.espn.com/apis/site/v2/sports/.../news) ──

data class EspnNewsResponse(
    @SerializedName("articles") val articles: List<EspnArticle> = emptyList()
)

data class EspnArticle(
    @SerializedName("headline") val headline: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("published") val published: String? = null,
    @SerializedName("byline") val byline: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("images") val images: List<EspnImage> = emptyList()
)

data class EspnImage(
    @SerializedName("url") val url: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null
)
