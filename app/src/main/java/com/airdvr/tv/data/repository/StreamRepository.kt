package com.airdvr.tv.data.repository

import com.airdvr.tv.util.Constants

class StreamRepository {

    fun getStreamUrl(channelNumber: String): String {
        return "${Constants.HLS_BASE_URL}${channelNumber}/playlist.m3u8"
    }

    fun getRecordingStreamUrl(recordingId: String): String {
        return "${Constants.BASE_URL}api/recordings/${recordingId}/stream.m3u8"
    }
}
