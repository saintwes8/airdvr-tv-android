package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.models.Recording
import com.airdvr.tv.data.repository.GuideRepository
import com.airdvr.tv.data.repository.RecordingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val continueWatching: List<Recording> = emptyList(),
    val liveNow: List<Channel> = emptyList(),
    val recentRecordings: List<Recording> = emptyList(),
    val error: String? = null
)

class HomeViewModel : ViewModel() {

    private val guideRepo = GuideRepository()
    private val recordingsRepo = RecordingsRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val channelsDeferred = async { guideRepo.getChannels() }
                val recordingsDeferred = async { recordingsRepo.getRecordings() }

                val channelsResult = channelsDeferred.await()
                val recordingsResult = recordingsDeferred.await()

                var newState = _uiState.value.copy(isLoading = false)

                channelsResult.onSuccess { channels ->
                    val liveNow = channels
                        .filter { it.favorite == 1 }
                        .take(10)
                        .ifEmpty { channels.take(10) }
                    newState = newState.copy(liveNow = liveNow)
                }.onFailure { e ->
                    newState = newState.copy(error = e.message)
                }

                recordingsResult.onSuccess { recordings ->
                    val continueWatching = recordings
                        .filter { it.resumePositionSec > 0 }
                        .sortedByDescending { it.startTime }
                        .take(10)
                    val recent = recordings
                        .filter { it.resumePositionSec == 0 }
                        .sortedByDescending { it.startTime }
                        .take(10)
                    newState = newState.copy(
                        continueWatching = continueWatching,
                        recentRecordings = recent
                    )
                }

                _uiState.value = newState
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}
