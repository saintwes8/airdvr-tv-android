package com.airdvr.tv.data.models

import com.google.gson.annotations.SerializedName

data class GuideResponse(
    @SerializedName("channels") val channels: List<Channel> = emptyList(),
    @SerializedName("programs") val programs: List<EpgProgram> = emptyList()
)

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String
)

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class TunerInfo(
    @SerializedName("DeviceID") val deviceId: String? = null,
    @SerializedName("LocalIP") val localIp: String? = null,
    @SerializedName("ModelNumber") val modelNumber: String? = null,
    @SerializedName("connected") val connected: Boolean = false,
    @SerializedName("lastSeen") val lastSeen: Long? = null
)

data class StorageInfo(
    @SerializedName("used") val used: Long = 0L,
    @SerializedName("total") val total: Long = 0L,
    @SerializedName("free") val free: Long = 0L
)
