package com.airdvr.tv.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.Recording
import com.airdvr.tv.data.repository.RecordingsRepository
import com.airdvr.tv.data.repository.StreamRepository
import com.airdvr.tv.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val recording: Recording? = null,
    val streamUrl: String? = null,
    val isLoading: Boolean = true,
    val isPlaying: Boolean = true,
    val showControls: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val ccEnabled: Boolean = false,
    val error: String? = null
)

class PlayerViewModel : ViewModel() {

    private val recordingsRepo = RecordingsRepository()
    private val streamRepo = StreamRepository()
    private val api = ApiClient.api

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun loadRecording(recordingId: String, preResolvedUrl: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            recordingsRepo.getRecordings().onSuccess { recordings ->
                val recording = recordings.find { it.id == recordingId }
                if (recording != null) {
                    val resumeMs = recording.resumePositionSec * 1000L
                    val streamUrl = if (preResolvedUrl != null) {
                        Log.d("PLAYBACK", "Using pre-resolved URL: $preResolvedUrl")
                        preResolvedUrl
                    } else if (recording.storageType == "cloud") {
                        // Cloud recording: fetch signed URL from API
                        try {
                            val resp = api.getRecordingStream(recordingId)
                            if (resp.isSuccessful) {
                                val url = resp.body()?.url
                                Log.d("PLAYBACK", "Cloud stream URL: $url")
                                url
                            } else {
                                Log.d("PLAYBACK", "Cloud stream API error: ${resp.code()}")
                                val msg = when (resp.code()) {
                                    403 -> "Cloud playback requires Pro"
                                    409 -> "Recording not ready"
                                    503 -> "DVR agent offline"
                                    else -> "Could not load recording (${resp.code()})"
                                }
                                _uiState.value = _uiState.value.copy(isLoading = false, error = msg)
                                return@onSuccess
                            }
                        } catch (e: Exception) {
                            Log.d("PLAYBACK", "Cloud stream exception: ${e.message}")
                            _uiState.value = _uiState.value.copy(isLoading = false, error = "Network error: ${e.message}")
                            return@onSuccess
                        }
                    } else {
                        // Local recording: stream through tunnel via HLS
                        val url = "${Constants.BASE_URL}api/stream/recording/$recordingId/stream.m3u8"
                        Log.d("PLAYBACK", "Local stream URL: $url")
                        url
                    }

                    if (streamUrl == null) {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = "Could not get playback URL")
                        return@onSuccess
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        recording = recording,
                        streamUrl = streamUrl,
                        currentPositionMs = resumeMs
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Recording not found"
                    )
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun showControls() {
        _uiState.value = _uiState.value.copy(showControls = true)
    }

    fun hideControls() {
        _uiState.value = _uiState.value.copy(showControls = false)
    }

    fun togglePlayPause() {
        _uiState.value = _uiState.value.copy(isPlaying = !_uiState.value.isPlaying)
    }

    fun setPlaying(playing: Boolean) {
        _uiState.value = _uiState.value.copy(isPlaying = playing)
    }

    fun updatePosition(positionMs: Long, durationMs: Long) {
        _uiState.value = _uiState.value.copy(
            currentPositionMs = positionMs,
            durationMs = durationMs
        )
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun toggleCC() {
        _uiState.value = _uiState.value.copy(ccEnabled = !_uiState.value.ccEnabled)
    }

    fun onPlayerReady() {
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    fun onPlayerError(error: String) {
        _uiState.value = _uiState.value.copy(isLoading = false, error = error)
    }

    fun updateResumePosition(positionSeconds: Int) {
        val recordingId = _uiState.value.recording?.id ?: return
        viewModelScope.launch {
            recordingsRepo.updateResumePosition(recordingId, positionSeconds)
        }
    }
}
