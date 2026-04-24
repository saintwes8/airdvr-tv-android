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

/**
 * A display group in the Recordings tab: either a single recording or
 * a show with multiple episodes. `recordings` has exactly one entry for
 * single-recording groups and >1 entries for show groups.
 */
data class RecordingGroup(
    val key: String,
    val title: String,
    val recordings: List<Recording>,
    val isShow: Boolean
) {
    val totalSizeMb: Float get() = recordings.sumOf { (it.fileSizeMb ?: 0f).toDouble() }.toFloat()
    val posterUrl: String? get() = recordings.firstOrNull { !it.posterUrl.isNullOrBlank() }?.posterUrl
}

data class RecordingsUiState(
    val isLoading: Boolean = true,
    val allRecordings: List<Recording> = emptyList(),
    val filteredRecordings: List<Recording> = emptyList(),
    val groups: List<RecordingGroup> = emptyList(),
    val expandedGroupKey: String? = null,
    val schedules: List<RecordingSchedule> = emptyList(),
    val selectedTab: RecordingsTab = RecordingsTab.RECORDINGS,
    val selectedCategory: RecordingCategory = RecordingCategory.ALL,
    val error: String? = null,
    val toastMessage: String? = null,
    val loadingPlaybackId: String? = null
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
                    filteredRecordings = filtered,
                    groups = buildGroups(filtered)
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
        _uiState.value = _uiState.value.copy(selectedTab = tab, expandedGroupKey = null)
    }

    fun toggleGroupExpanded(key: String) {
        val current = _uiState.value.expandedGroupKey
        _uiState.value = _uiState.value.copy(
            expandedGroupKey = if (current == key) null else key
        )
    }

    fun closeExpandedGroup() {
        _uiState.value = _uiState.value.copy(expandedGroupKey = null)
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
        val id = recording.id ?: return
        // Don't attempt playback for in-progress or failed recordings
        val effectiveStatus = effectiveStatus(recording)
        if (effectiveStatus != "completed" && effectiveStatus != "complete") {
            when (effectiveStatus) {
                "recording" -> showToast("Recording in progress")
                "interrupted" -> showToast("Recording was interrupted")
                "failed" -> showToast("Recording failed")
                else -> showToast("Recording not ready")
            }
            return
        }

        viewModelScope.launch {
            if (recording.storageType?.lowercase() == "cloud") {
                _uiState.value = _uiState.value.copy(loadingPlaybackId = id)
                try {
                    val resp = api.getRecordingStream(id)
                    if (resp.isSuccessful) {
                        val url = resp.body()?.url
                        _uiState.value = _uiState.value.copy(loadingPlaybackId = null)
                        if (url != null) onPlayUrl(url)
                        else showToast("Could not get playback URL")
                    } else {
                        _uiState.value = _uiState.value.copy(loadingPlaybackId = null)
                        when (resp.code()) {
                            403 -> showToast("Cloud playback requires Pro")
                            409 -> showToast("Recording not ready")
                            503 -> showToast("DVR agent offline")
                            else -> showToast("Could not load recording")
                        }
                    }
                } catch (_: Exception) {
                    _uiState.value = _uiState.value.copy(loadingPlaybackId = null)
                    showToast("Could not connect. Check your network.")
                }
            } else {
                // Local recording — stream through tunnel
                val streamUrl = "${Constants.BASE_URL}api/stream/recording/$id/stream.m3u8"
                onPlayUrl(streamUrl)
            }
        }
    }

    fun setCategory(category: RecordingCategory) {
        val filtered = applyFilter(_uiState.value.allRecordings, category)
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            filteredRecordings = filtered,
            groups = buildGroups(filtered),
            expandedGroupKey = null
        )
    }

    fun showToast(message: String) {
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

    private fun buildGroups(recordings: List<Recording>): List<RecordingGroup> {
        val byTitle = recordings.groupBy { (it.title ?: "").trim().ifBlank { "Untitled" } }
        return byTitle.map { (title, items) ->
            RecordingGroup(
                key = title.lowercase(),
                title = title,
                recordings = items.sortedByDescending { it.startEpochSec },
                isShow = items.size > 1
            )
        }.sortedBy { it.title.lowercase() }
    }

    /**
     * Compute the effective status of a recording. A recording with status
     * "recording" whose ended_at is in the past, or which started more than
     * 4 hours ago, is considered interrupted.
     */
    fun effectiveStatus(recording: Recording): String {
        val raw = recording.status?.lowercase() ?: ""
        if (raw == "recording" || raw == "active") {
            val now = System.currentTimeMillis() / 1000
            val ended = recording.endEpochSec
            val started = recording.startEpochSec
            val hasPastEnd = ended in 1..<now
            val tooLongAgo = started in 1..<(now - 4 * 60 * 60)
            return if (hasPastEnd || tooLongAgo) "interrupted" else "recording"
        }
        return raw
    }
}
