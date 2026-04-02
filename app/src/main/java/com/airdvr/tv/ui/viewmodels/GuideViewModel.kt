package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.models.EpgProgram
import com.airdvr.tv.data.repository.GuideRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GuideUiState(
    val isLoading: Boolean = true,
    val channels: List<Channel> = emptyList(),
    // Map from channel guideNumber to list of programs
    val programsByChannel: Map<String, List<EpgProgram>> = emptyMap(),
    val selectedProgram: EpgProgram? = null,
    val selectedChannel: Channel? = null,
    val error: String? = null
)

class GuideViewModel : ViewModel() {

    private val guideRepo = GuideRepository()

    private val _uiState = MutableStateFlow(GuideUiState())
    val uiState: StateFlow<GuideUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            guideRepo.getChannelsWithPrograms().onSuccess { (channels, programs) ->
                val sorted = channels.sortedBy { it.guideNumber.toDoubleOrNull() ?: Double.MAX_VALUE }
                // Group programs by their guideNumber field (if available) or distribute evenly
                val grouped: Map<String, List<EpgProgram>> = if (programs.any { it.guideNumber != null }) {
                    programs
                        .filter { it.guideNumber != null }
                        .groupBy { it.guideNumber!! }
                } else {
                    // Fallback: assign programs sequentially to channels by index
                    val programsPerChannel = if (sorted.isNotEmpty()) {
                        programs.chunked((programs.size / sorted.size).coerceAtLeast(1))
                    } else emptyList()
                    sorted.mapIndexed { idx, ch ->
                        ch.guideNumber to (programsPerChannel.getOrNull(idx) ?: emptyList())
                    }.toMap()
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    channels = sorted,
                    programsByChannel = grouped
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun selectProgram(program: EpgProgram, channel: Channel) {
        _uiState.value = _uiState.value.copy(
            selectedProgram = program,
            selectedChannel = channel
        )
    }

    fun dismissProgramDetail() {
        _uiState.value = _uiState.value.copy(
            selectedProgram = null,
            selectedChannel = null
        )
    }
}
