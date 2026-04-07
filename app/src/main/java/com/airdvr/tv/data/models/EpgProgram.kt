package com.airdvr.tv.data.models

import com.airdvr.tv.util.parseIsoToEpochSec
import com.google.gson.annotations.SerializedName

data class EpgProgram(
    @SerializedName("title") val title: String? = null,
    @SerializedName("episode_title") val episodeTitle: String? = null,
    @SerializedName("start_time") val startTime: String? = null,
    @SerializedName("end_time") val endTime: String? = null,
    @SerializedName("description") val summary: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("series_id") val seriesId: String? = null,
    @SerializedName("program_id") val programId: String? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("channel_number") val guideNumber: String? = null,
    @SerializedName("is_new") val isNew: Boolean = false
) {
    val startEpochSec: Long get() = parseIsoToEpochSec(startTime)
    val endEpochSec: Long get() = parseIsoToEpochSec(endTime)
}
