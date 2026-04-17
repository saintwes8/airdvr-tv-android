package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.Recording
import com.airdvr.tv.data.models.RecordingSchedule
import com.airdvr.tv.data.models.ScheduleRequest
import com.airdvr.tv.data.repository.RecordingsRepository
import com.airdvr.tv.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RecordingsTab { RECORDINGS, UPCOMING }

enum class RecordingCategory { ALL, TV_SHOWS, MOVIES, SPORTS }

data class RecordingsUiState(
    val isLoading: Boolean = true,
    val allRecordings: List<Recording> = emptyList(),
    val filteredRecordings: List<Recording> = emptyList(),
    val schedules: List<RecordingSchedule> = emptyList(),
    val selectedTab: RecordingsTab = RecordingsTab.RECORDINGS,
    val selectedCategory: RecordingCategory = RecordingCategory.ALL,
    val error: String? = null,
    val toastMessage: String? = null
)

class RecordingsViewModel : ViewModel() {

    private val repo = RecordingsRepository()
    private val api = ApiClient.api

    private val _uiState = MutableStateFlow(RecordingsUiState())
    val uiState: StateFlow<RecordingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.getRecordings().onSuccess { recordings ->
                val filtered = applyFilter(recordings, _uiState.value.selectedCategory)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    allRecordings = recordings,
                    filteredRecordings = filtered
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
            fetchSchedules()
        }
    }

    private suspend fun fetchSchedules() {
        try {
            val resp = api.getRecordingSchedules()
            if (resp.isSuccessful) {
                _uiState.value = _uiState.value.copy(schedules = resp.body() ?: emptyList())
            }
        } catch (_: Exception) { }
    }

    fun setTab(tab: RecordingsTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun scheduleRecording(channelNumber: String, title: String, startTime: String, endTime: String, type: String) {
        viewModelScope.launch {
            try {
                val resp = api.scheduleRecording(
                    ScheduleRequest(channelNumber, title, startTime, endTime, type)
                )
                if (resp.isSuccessful) {
                    showToast("Recording scheduled: $title")
                    fetchSchedules()
                } else {
                    showToast("Could not schedule recording")
                }
            } catch (_: Exception) {
                showToast("Could not connect. Check your network.")
            }
        }
    }

    fun deleteRecording(id: String) {
        viewModelScope.launch {
            try {
                val resp = api.deleteRecording(id)
                if (resp.isSuccessful) {
                    showToast("Recording deleted")
                    load()
                } else {
                    showToast("Could not delete recording")
                }
            } catch (_: Exception) {
                showToast("Could not connect. Check your network.")
            }
        }
    }

    fun cancelSchedule(id: String) {
        viewModelScope.launch {
            try {
                val resp = api.deleteSchedule(id)
                if (resp.isSuccessful) {
                    showToast("Schedule cancelled")
                    fetchSchedules()
                } else {
                    showToast("Could not cancel schedule")
                }
            } catch (_: Exception) {
                showToast("Could not connect. Check your network.")
            }
        }
    }

    fun playRecording(recording: Recording, onPlayUrl: (String) -> Unit) {
        viewModelScope.launch {
            if (recording.storageType == "cloud") {
                try {
                    val resp = api.getRecordingStream(recording.id!!)
                    if (resp.isSuccessful) {
                        val url = resp.body()?.url
                        if (url != null) onPlayUrl(url)
                        else showToast("Could not get playback URL")
                    } else if (resp.code() == 403) {
                        showToast("Cloud playback requires Pro subscription")
                    } else {
                        showToast("Could not load recording")
                    }
                } catch (_: Exception) {
                    showToast("Could not connect. Check your network.")
                }
            } else {
                // Local recording — stream through tunnel
                val streamUrl = "${Constants.BASE_URL}api/stream/recording/${recording.id}/stream.m3u8"
                onPlayUrl(streamUrl)
            }
        }
    }

    fun setCategory(category: RecordingCategory) {
        val filtered = applyFilter(_uiState.value.allRecordings, category)
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            filteredRecordings = filtered
        )
    }

    private fun showToast(message: String) {
        _uiState.value = _uiState.value.copy(toastMessage = message)
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.value = _uiState.value.copy(toastMessage = null)
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    private fun applyFilter(recordings: List<Recording>, category: RecordingCategory): List<Recording> {
        return when (category) {
            RecordingCategory.ALL -> recordings
            RecordingCategory.TV_SHOWS -> recordings.filter { r ->
                val cat = r.category?.lowercase() ?: ""
                cat.contains("series") || cat.contains("tv") || cat.contains("show") || cat.contains("episode")
            }
            RecordingCategory.MOVIES -> recordings.filter { r ->
                val cat = r.category?.lowercase() ?: ""
                cat.contains("movie") || cat.contains("film") || cat.contains("cinema")
            }
            RecordingCategory.SPORTS -> recordings.filter { r ->
                val cat = r.category?.lowercase() ?: ""
                cat.contains("sport")
            }
        }
    }
}
