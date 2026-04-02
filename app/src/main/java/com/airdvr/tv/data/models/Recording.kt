package com.airdvr.tv.data.models

import com.google.gson.annotations.SerializedName

data class Recording(
    @SerializedName("RecordingID") val id: String,
    @SerializedName("Title") val title: String,
    @SerializedName("EpisodeTitle") val episodeTitle: String? = null,
    @SerializedName("Status") val status: String,
    @SerializedName("PosterURL") val posterUrl: String? = null,
    @SerializedName("ImageURL") val imageUrl: String? = null,
    @SerializedName("ResumeOffsetSeconds") val resumePositionSec: Int = 0,
    @SerializedName("StartTime") val startTime: Long = 0L,
    @SerializedName("Duration") val duration: Int = 0,
    @SerializedName("Category") val category: List<String>? = null,
    @SerializedName("ChannelNumber") val channelNumber: String? = null
)
