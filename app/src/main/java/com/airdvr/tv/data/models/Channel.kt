package com.airdvr.tv.data.models

import com.google.gson.annotations.SerializedName

data class Channel(
    @SerializedName("guide_number") val guideNumber: String? = null,
    @SerializedName("guide_name") val guideName: String? = null,
    @SerializedName("hd") val hd: Boolean? = null,
    @SerializedName("video_codec") val videoCodec: String? = null,
    @SerializedName("audio_codec") val audioCodec: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("favorite") val favorite: Boolean? = null,
    @SerializedName("signal_strength") val signalStrength: Int? = null,
    @SerializedName("signal_quality") val signalQuality: Int? = null
)
