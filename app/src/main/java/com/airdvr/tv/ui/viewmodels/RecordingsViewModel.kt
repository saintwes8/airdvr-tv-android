package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.data.models.Recording
import com.airdvr.tv.data.repository.RecordingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RecordingCategory { ALL, TV_SHOWS, MOVIES, SPORTS }

data class RecordingsUiState(
    val isLoading: Boolean = true,
    val allRecordings: List<Recording> = emptyList(),
    val filteredRecordings: List<Recording> = emptyList(),
    val selectedCategory: RecordingCategory = RecordingCategory.ALL,
    val error: String? = null
)

class RecordingsViewModel : ViewModel() {

    private val repo = RecordingsRepository()

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
        }
    }

    fun setCategory(category: RecordingCategory) {
        val filtered = applyFilter(_uiState.value.allRecordings, category)
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            filteredRecordings = filtered
        )
    }

    private fun applyFilter(recordings: List<Recording>, category: RecordingCategory): List<Recording> {
        return when (category) {
            RecordingCategory.ALL -> recordings
            RecordingCategory.TV_SHOWS -> recordings.filter { r ->
                val cats = r.category?.map { it.lowercase() } ?: emptyList()
                cats.any { it.contains("series") || it.contains("tv") || it.contains("show") || it.contains("episode") }
            }
            RecordingCategory.MOVIES -> recordings.filter { r ->
                val cats = r.category?.map { it.lowercase() } ?: emptyList()
                cats.any { it.contains("movie") || it.contains("film") || it.contains("cinema") }
            }
            RecordingCategory.SPORTS -> recordings.filter { r ->
                val cats = r.category?.map { it.lowercase() } ?: emptyList()
                cats.any { it.contains("sport") }
            }
        }
    }
}
