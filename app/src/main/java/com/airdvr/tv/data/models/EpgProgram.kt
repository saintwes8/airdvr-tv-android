package com.airdvr.tv.data.models

import com.google.gson.annotations.SerializedName

data class EpgProgram(
    @SerializedName("Title") val title: String,
    @SerializedName("EpisodeTitle") val episodeTitle: String? = null,
    @SerializedName("StartTime") val startTime: Long,
    @SerializedName("EndTime") val endTime: Long,
    @SerializedName("Summary") val summary: String? = null,
    @SerializedName("Category") val category: List<String>? = null,
    @SerializedName("SeriesID") val seriesId: String? = null,
    @SerializedName("ProgramID") val programId: String? = null,
    @SerializedName("Poster") val poster: String? = null,
    @SerializedName("ImageURL") val imageUrl: String? = null,
    @SerializedName("GuideNumber") val guideNumber: String? = null
)
