package com.airdvr.tv.data.repository

import com.airdvr.tv.util.Constants

class StreamRepository {

    fun getStreamUrl(channelNumber: String?, quality: String? = null): String {
        if (channelNumber.isNullOrBlank()) return ""
        val base = "${Constants.HLS_BASE_URL}${channelNumber}/playlist.m3u8"
        return if (!quality.isNullOrBlank() && quality != "Auto") "$base?quality=$quality" else base
    }

    fun getRecordingStreamUrl(recordingId: String?): String {
        if (recordingId.isNullOrBlank()) return ""
        return "${Constants.BASE_URL}api/stream/recording/${recordingId}/stream.m3u8"
    }
}
