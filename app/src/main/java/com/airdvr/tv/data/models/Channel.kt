package com.airdvr.tv.data.models

import com.google.gson.annotations.SerializedName

data class Channel(
    @SerializedName("GuideNumber") val guideNumber: String,
    @SerializedName("GuideName") val guideName: String,
    @SerializedName("HD") val hd: Int = 0,
    @SerializedName("VideoCodec") val videoCodec: String? = null,
    @SerializedName("AudioCodec") val audioCodec: String? = null,
    @SerializedName("URL") val url: String? = null,
    @SerializedName("Favorite") val favorite: Int = 0
)
