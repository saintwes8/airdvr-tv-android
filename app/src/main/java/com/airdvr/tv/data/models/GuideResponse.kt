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
