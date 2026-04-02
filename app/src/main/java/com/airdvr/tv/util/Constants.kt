package com.airdvr.tv.util

object Constants {
    const val BASE_URL = "https://api.airdvr.com/"
    const val HLS_BASE_URL = "https://api.airdvr.com/api/stream/"
    const val MIN_BUFFER_MS = 15_000
    const val MAX_BUFFER_MS = 60_000
    const val BUFFER_FOR_PLAYBACK_MS = 5_000
    const val STREAM_RETRY_COUNT = 6
    const val STREAM_RETRY_DELAY_MS = 2_000L
    const val TUNING_TIMEOUT_MS = 30_000L
    const val CHANNEL_OVERLAY_HIDE_DELAY_MS = 5_000L

    // Shared preferences
    const val PREFS_NAME = "airdvr_secure_prefs"
    const val KEY_ACCESS_TOKEN = "access_token"
    const val KEY_REFRESH_TOKEN = "refresh_token"

    // Navigation routes
    const val ROUTE_HOME = "home"
    const val ROUTE_LIVE_TV = "live_tv"
    const val ROUTE_GUIDE = "guide"
    const val ROUTE_MULTIVIEW = "multiview"
    const val ROUTE_RECORDINGS = "recordings"
    const val ROUTE_PLAYER = "player/{recordingId}"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_LOGIN = "login"
}
