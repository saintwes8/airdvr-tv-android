package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.repository.GuideRepository
import com.airdvr.tv.data.repository.StreamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LiveTVUiState(
    val channels: List<Channel> = emptyList(),
    val selectedChannel: Channel? = null,
    val streamUrl: String? = null,
    val isLoading: Boolean = true,
    val isTuning: Boolean = false,
    val error: String? = null,
    val showOverlay: Boolean = false
)

class LiveTVViewModel : ViewModel() {

    private val guideRepo = GuideRepository()
    private val streamRepo = StreamRepository()

    private val _uiState = MutableStateFlow(LiveTVUiState())
    val uiState: StateFlow<LiveTVUiState> = _uiState.asStateFlow()

    init {
        loadChannels()
    }

    fun loadChannels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            guideRepo.getChannels().onSuccess { channels ->
                val sorted = channels.sortedBy { it.guideNumber.toDoubleOrNull() ?: Double.MAX_VALUE }
                _uiState.value = _uiState.value.copy(channels = sorted, isLoading = false)
                if (sorted.isNotEmpty() && _uiState.value.selectedChannel == null) {
                    tuneToChannel(sorted.first())
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun tuneToChannel(channel: Channel) {
        val url = streamRepo.getStreamUrl(channel.guideNumber)
        _uiState.value = _uiState.value.copy(
            selectedChannel = channel,
            streamUrl = url,
            isTuning = true,
            showOverlay = true,
            error = null
        )
    }

    fun onPlayerReady() {
        _uiState.value = _uiState.value.copy(isTuning = false)
    }

    fun onPlayerError(error: String) {
        _uiState.value = _uiState.value.copy(isTuning = false, error = error)
    }

    fun showOverlay() {
        _uiState.value = _uiState.value.copy(showOverlay = true)
    }

    fun hideOverlay() {
        _uiState.value = _uiState.value.copy(showOverlay = false)
    }

    fun channelUp() {
        val channels = _uiState.value.channels
        val current = _uiState.value.selectedChannel ?: return
        val idx = channels.indexOf(current)
        if (idx < channels.size - 1) tuneToChannel(channels[idx + 1])
    }

    fun channelDown() {
        val channels = _uiState.value.channels
        val current = _uiState.value.selectedChannel ?: return
        val idx = channels.indexOf(current)
        if (idx > 0) tuneToChannel(channels[idx - 1])
    }
}
