package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.data.models.Recording
import com.airdvr.tv.data.repository.RecordingsRepository
import com.airdvr.tv.data.repository.StreamRepository
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

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun loadRecording(recordingId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            recordingsRepo.getRecordings().onSuccess { recordings ->
                val recording = recordings.find { it.id == recordingId }
                if (recording != null) {
                    val streamUrl = streamRepo.getRecordingStreamUrl(recordingId)
                    val resumeMs = recording.resumePositionSec * 1000L
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
