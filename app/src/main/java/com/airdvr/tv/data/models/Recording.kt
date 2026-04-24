package com.airdvr.tv.data.models

import com.airdvr.tv.util.parseIsoToEpochSec
import com.google.gson.annotations.SerializedName

data class Recording(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("episode_title") val episodeTitle: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("poster_url") val posterUrl: String? = null,
    @SerializedName("backdrop_url") val backdropUrl: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("resume_position_sec") val resumePositionSec: Int = 0,
    @SerializedName("started_at") val startTime: String? = null,
    @SerializedName("ended_at") val endTime: String? = null,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("category") val category: String? = null,
    @SerializedName("channel_number") val channelNumber: String? = null,
    @SerializedName("season") val seasonNumber: Int? = null,
    @SerializedName("episode") val episodeNumber: Int? = null,
    @SerializedName("file_size_mb") val fileSizeMb: Float? = null,
    @SerializedName("storage_type") val storageType: String? = null,
    @SerializedName("file_path") val filePath: String? = null,
    @SerializedName("tmdb_id") val tmdbId: String? = null,
    @SerializedName("error_reason") val errorReason: String? = null,
    @SerializedName("device_name") val deviceName: String? = null
) {
    val startEpochSec: Long get() = parseIsoToEpochSec(startTime)
    val endEpochSec: Long get() = parseIsoToEpochSec(endTime)
}
