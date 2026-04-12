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
    @SerializedName("created_at") val createdAt: String? = null
)

data class SetZipRequest(
    @SerializedName("zip_code") val zipCode: String
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
    @SerializedName("type") val type: String
)
