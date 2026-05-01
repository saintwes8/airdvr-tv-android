package com.airdvr.tv.data.stream

import android.util.Log
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class StreamMode { LOCAL, REMOTE, TUNNEL }

/**
 * Three-tier streaming: prefers a direct LAN-side agent connection, falls
 * back to a public agent endpoint, finally to the api.airdvr.com tunnel.
 *
 * Probed at app launch and every 5 minutes. UI observes [mode] / [baseUrl]
 * to render a connection indicator and to build stream URLs.
 */
object StreamModeManager {

    private const val TAG = "STREAMMODE"
    private const val LOCAL_TIMEOUT_SEC = 2L
    private const val REMOTE_TIMEOUT_SEC = 3L
    private const val REFRESH_INTERVAL_MS = 5L * 60L * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null

    private val _mode = MutableStateFlow(StreamMode.TUNNEL)
    val mode: StateFlow<StreamMode> = _mode.asStateFlow()

    /** Active stream-base URL (always ends in `/`). */
    private val _baseUrl = MutableStateFlow(Constants.HLS_BASE_URL)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    /**
     * Start the periodic probe. Idempotent — subsequent calls are no-ops.
     * Called once from AirDVRApp.onCreate.
     */
    fun start() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            while (true) {
                refreshNow()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    /** Force an out-of-band refresh (e.g. on user-triggered retune). */
    fun forceRefresh() {
        scope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        val info = fetchAgentInfo()
        val local = info?.localStreamUrl?.takeIf { it.isNotBlank() }
        val remote = info?.remoteStreamUrl?.takeIf { it.isNotBlank() }

        if (local != null && probe(local, LOCAL_TIMEOUT_SEC)) {
            apply(StreamMode.LOCAL, ensureTrailingSlash(local) + "stream/")
            return
        }
        if (remote != null && probe(remote, REMOTE_TIMEOUT_SEC)) {
            apply(StreamMode.REMOTE, ensureTrailingSlash(remote) + "stream/")
            return
        }
        apply(StreamMode.TUNNEL, Constants.HLS_BASE_URL)
    }

    private fun apply(newMode: StreamMode, newBase: String) {
        if (_mode.value != newMode || _baseUrl.value != newBase) {
            Log.d(TAG, "switch → $newMode  base=$newBase")
            _mode.value = newMode
            _baseUrl.value = newBase
        }
    }

    private suspend fun fetchAgentInfo() = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.api.getAgentInfo()
            if (resp.isSuccessful) resp.body() else null
        } catch (e: Exception) {
            Log.d(TAG, "agent/info failed: ${e.message}")
            null
        }
    }

    private suspend fun probe(streamUrl: String, timeoutSec: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val healthUrl = ensureTrailingSlash(streamUrl) + "health"
            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutSec, TimeUnit.SECONDS)
                .readTimeout(timeoutSec, TimeUnit.SECONDS)
                .callTimeout(timeoutSec, TimeUnit.SECONDS)
                .build()
            val resp = client.newCall(Request.Builder().url(healthUrl).get().build()).execute()
            resp.use { it.isSuccessful }
        } catch (e: Exception) {
            Log.d(TAG, "probe $streamUrl failed: ${e.message}")
            false
        }
    }

    private fun ensureTrailingSlash(s: String): String = if (s.endsWith("/")) s else "$s/"
}
