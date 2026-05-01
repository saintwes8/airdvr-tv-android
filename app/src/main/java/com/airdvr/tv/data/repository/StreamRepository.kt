package com.airdvr.tv.data.repository

import com.airdvr.tv.data.stream.StreamModeManager
import com.airdvr.tv.util.Constants

class StreamRepository {

    fun getStreamUrl(channelNumber: String?, quality: String? = null): String {
        if (channelNumber.isNullOrBlank()) return ""
        // Active base URL flips between local agent / remote agent / api.airdvr.com tunnel
        // depending on what passed the most recent health probe.
        val base = "${StreamModeManager.baseUrl.value}${channelNumber}/playlist.m3u8"
        return if (!quality.isNullOrBlank() && quality != "Auto") "$base?quality=$quality" else base
    }

    fun getRecordingStreamUrl(recordingId: String?): String {
        // Local recordings still tunnel through api.airdvr.com — cloud recordings should use
        // the R2 signed URL from /api/recordings/{id}/stream.
        if (recordingId.isNullOrBlank()) return ""
        return "${Constants.BASE_URL}api/stream/recording/${recordingId}/stream.m3u8"
    }
}
