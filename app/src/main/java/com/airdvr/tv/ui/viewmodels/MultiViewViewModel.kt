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

enum class MultiViewLayout { TWO_UP, FOUR_UP }

data class ViewPane(
    val index: Int,
    val channel: Channel? = null,
    val streamUrl: String? = null,
    val isActive: Boolean = false,
    val isTuning: Boolean = false
)

data class MultiViewUiState(
    val layout: MultiViewLayout = MultiViewLayout.TWO_UP,
    val panes: List<ViewPane> = List(4) { ViewPane(it) },
    val focusedPane: Int = 0,
    val channels: List<Channel> = emptyList(),
    val showChannelPicker: Boolean = false,
    val pickerTargetPane: Int = 0,
    val tunerLimitWarning: Boolean = false,
    val isLoading: Boolean = false
)

class MultiViewViewModel : ViewModel() {

    private val guideRepo = GuideRepository()
    private val streamRepo = StreamRepository()

    private val _uiState = MutableStateFlow(MultiViewUiState())
    val uiState: StateFlow<MultiViewUiState> = _uiState.asStateFlow()

    init {
        loadChannels()
    }

    private fun loadChannels() {
        viewModelScope.launch {
            guideRepo.getChannels().onSuccess { channels ->
                val sorted = channels.sortedBy { it.guideNumber.toDoubleOrNull() ?: Double.MAX_VALUE }
                _uiState.value = _uiState.value.copy(channels = sorted)
            }
        }
    }

    fun toggleLayout() {
        val new = if (_uiState.value.layout == MultiViewLayout.TWO_UP) {
            MultiViewLayout.FOUR_UP
        } else {
            MultiViewLayout.TWO_UP
        }
        _uiState.value = _uiState.value.copy(layout = new)
    }

    fun setFocusedPane(index: Int) {
        _uiState.value = _uiState.value.copy(focusedPane = index)
    }

    fun activatePane(index: Int) {
        val panes = _uiState.value.panes.mapIndexed { i, p -> p.copy(isActive = i == index) }
        _uiState.value = _uiState.value.copy(panes = panes, focusedPane = index)
    }

    fun showChannelPickerForPane(paneIndex: Int) {
        _uiState.value = _uiState.value.copy(
            showChannelPicker = true,
            pickerTargetPane = paneIndex
        )
    }

    fun hideChannelPicker() {
        _uiState.value = _uiState.value.copy(showChannelPicker = false)
    }

    fun assignChannelToPane(paneIndex: Int, channel: Channel) {
        // Check how many panes already have streams (excluding the one being assigned)
        val activeStreams = _uiState.value.panes.count { it.channel != null && it.index != paneIndex }
        if (activeStreams >= 2) {
            _uiState.value = _uiState.value.copy(
                tunerLimitWarning = true,
                showChannelPicker = false
            )
            return
        }
        val url = streamRepo.getStreamUrl(channel.guideNumber)
        val panes = _uiState.value.panes.mapIndexed { i, p ->
            if (i == paneIndex) p.copy(channel = channel, streamUrl = url, isTuning = true)
            else p
        }
        _uiState.value = _uiState.value.copy(panes = panes, showChannelPicker = false)
    }

    fun dismissTunerWarning() {
        _uiState.value = _uiState.value.copy(tunerLimitWarning = false)
    }

    fun onPaneReady(paneIndex: Int) {
        val panes = _uiState.value.panes.mapIndexed { i, p ->
            if (i == paneIndex) p.copy(isTuning = false) else p
        }
        _uiState.value = _uiState.value.copy(panes = panes)
    }

    fun removeChannel(paneIndex: Int) {
        val panes = _uiState.value.panes.mapIndexed { i, p ->
            if (i == paneIndex) p.copy(channel = null, streamUrl = null, isTuning = false, isActive = false)
            else p
        }
        _uiState.value = _uiState.value.copy(panes = panes)
    }
}
