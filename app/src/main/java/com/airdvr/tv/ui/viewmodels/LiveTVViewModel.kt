package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.AirDVRApp
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.models.EpgProgram
import com.airdvr.tv.data.repository.GuideRepository
import com.airdvr.tv.data.repository.StreamRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ScreenMode { GUIDE, FULLSCREEN, MULTIVIEW }

enum class MultiViewNavDirection { LEFT, RIGHT, UP, DOWN }

data class PaneState(
    val channel: Channel? = null,
    val streamUrl: String? = null,
    val isTuning: Boolean = false
)

data class LiveTVUiState(
    val mode: ScreenMode = ScreenMode.GUIDE,
    val guideOverlayVisible: Boolean = false,

    // Data
    val channels: List<Channel> = emptyList(),
    val programsByChannel: Map<String, List<EpgProgram>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,

    // Playback
    val currentChannel: Channel? = null,
    val streamUrl: String? = null,
    val isTuning: Boolean = false,

    // Guide grid navigation
    val focusedRow: Int = 0,
    val focusTimeEpoch: Long = 0L,
    val timeWindowStartEpoch: Long = 0L,

    // Category filter
    val categories: List<String> = listOf("All Channels", "News", "Sports", "Entertainment", "Movies", "Kids"),
    val selectedCategoryIndex: Int = 0,

    // MultiView
    val multiViewPanes: List<PaneState> = emptyList(),
    val activePaneIndex: Int = 0,
    val tunerCount: Int = 2,

    // Toast
    val toastMessage: String? = null,

    // Nav rail
    val navRailVisible: Boolean = false,

    // Fullscreen overlay (UP press: action buttons + show details + artwork)
    val showFullscreenOverlay: Boolean = false,

    // Mute
    val isMuted: Boolean = false,

    // Action overlay button navigation (4 buttons in fullscreen overlay)
    val actionButtonIndex: Int = 0,

    // User profile
    val userInitial: String = ""
) {
    val filteredChannels: List<Channel>
        get() {
            val cat = categories.getOrNull(selectedCategoryIndex) ?: "All Channels"
            if (cat == "All Channels") return channels
            return channels.filter { ch ->
                val progs = programsByChannel[ch.guideNumber ?: ""] ?: emptyList()
                progs.any { it.category?.contains(cat, ignoreCase = true) == true }
            }.ifEmpty { channels }
        }

    val focusedChannel: Channel?
        get() = filteredChannels.getOrNull(focusedRow)

    val focusedProgram: EpgProgram?
        get() {
            val chNum = focusedChannel?.guideNumber ?: return null
            val programs = programsByChannel[chNum] ?: return null
            return programs.firstOrNull {
                it.startEpochSec <= focusTimeEpoch && focusTimeEpoch < it.endEpochSec
            }
        }

    val currentProgram: EpgProgram?
        get() {
            val chNum = currentChannel?.guideNumber ?: return null
            val programs = programsByChannel[chNum] ?: return null
            val now = System.currentTimeMillis() / 1000
            return programs.firstOrNull { it.startEpochSec <= now && now < it.endEpochSec }
        }

    val isMultiView: Boolean get() = multiViewPanes.size >= 2

    val visibleDurationSec: Long get() = 3L * 3600L // 3 hours
}

class LiveTVViewModel : ViewModel() {

    private val guideRepo = GuideRepository()
    private val streamRepo = StreamRepository()
    private val api = ApiClient.api

    private val _uiState = MutableStateFlow(LiveTVUiState())
    val uiState: StateFlow<LiveTVUiState> = _uiState.asStateFlow()

    private var fullscreenOverlayJob: Job? = null
    private var toastJob: Job? = null

    init {
        val now = System.currentTimeMillis() / 1000
        val email = runCatching { AirDVRApp.instance.tokenManager.getUserEmail() }.getOrDefault("")
        val initial = email.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        _uiState.value = _uiState.value.copy(
            focusTimeEpoch = now,
            timeWindowStartEpoch = now,
            userInitial = initial
        )
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val resp = api.getTuners()
                if (resp.isSuccessful) {
                    val count = resp.body()?.tunerCount ?: 2
                    _uiState.value = _uiState.value.copy(tunerCount = count.coerceIn(1, 4))
                }
            } catch (_: Exception) {}

            guideRepo.getChannelsWithPrograms().onSuccess { (channels, programs) ->
                val sorted = channels.sortedBy { it.guideNumber?.toDoubleOrNull() ?: Double.MAX_VALUE }
                val grouped = if (programs.any { it.guideNumber != null }) {
                    programs.filter { it.guideNumber != null }.groupBy { it.guideNumber!! }
                } else {
                    val perChannel = if (sorted.isNotEmpty()) {
                        programs.chunked((programs.size / sorted.size).coerceAtLeast(1))
                    } else emptyList()
                    sorted.mapIndexed { idx, ch ->
                        (ch.guideNumber ?: "") to (perChannel.getOrNull(idx) ?: emptyList())
                    }.toMap()
                }
                _uiState.value = _uiState.value.copy(
                    channels = sorted,
                    programsByChannel = grouped,
                    isLoading = false
                )
                if (sorted.isNotEmpty() && _uiState.value.currentChannel == null) {
                    tuneToChannel(sorted.first())
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    // ── Tuning ──────────────────────────────────────────────────────────────

    private fun tuneToChannel(channel: Channel) {
        if (channel.guideNumber.isNullOrBlank()) return
        val url = streamRepo.getStreamUrl(channel.guideNumber)
        _uiState.value = _uiState.value.copy(
            currentChannel = channel,
            streamUrl = url,
            isTuning = true,
            error = null
        )
    }

    fun onPlayerReady() {
        _uiState.value = _uiState.value.copy(isTuning = false)
    }

    fun onPlayerError(error: String) {
        _uiState.value = _uiState.value.copy(isTuning = false, error = error)
    }

    // ── Guide Grid Navigation ───────────────────────────────────────────────

    fun navigateUp() {
        val s = _uiState.value
        if (s.focusedRow > 0) {
            _uiState.value = s.copy(focusedRow = s.focusedRow - 1)
        }
    }

    fun navigateDown() {
        val s = _uiState.value
        val filtered = s.filteredChannels
        if (s.focusedRow < filtered.size - 1) {
            _uiState.value = s.copy(focusedRow = s.focusedRow + 1)
        }
    }

    /**
     * LEFT navigation in guide. If we can't move left further (already at NOW or
     * leftmost program), returns false so caller knows to transition to FULLSCREEN.
     */
    fun navigateLeft(): Boolean {
        val s = _uiState.value
        val now = System.currentTimeMillis() / 1000
        val chNum = s.focusedChannel?.guideNumber ?: return false
        val programs = s.programsByChannel[chNum]?.sortedBy { it.startEpochSec } ?: return false
        val current = programs.firstOrNull {
            it.startEpochSec <= s.focusTimeEpoch && s.focusTimeEpoch < it.endEpochSec
        }
        if (current != null) {
            // Find previous program that ends after now (to keep focus inside the visible window)
            val prevProg = programs.lastOrNull {
                it.endEpochSec <= current.startEpochSec && it.endEpochSec > now
            }
            if (prevProg != null) {
                val newFocus = maxOf(prevProg.startEpochSec, now) + 1
                _uiState.value = s.copy(focusTimeEpoch = newFocus)
                return true
            }
            // Already on the leftmost program — exit to fullscreen
            return false
        }
        return false
    }

    fun navigateRight() {
        val s = _uiState.value
        val chNum = s.focusedChannel?.guideNumber ?: return
        val programs = s.programsByChannel[chNum]?.sortedBy { it.startEpochSec } ?: return
        val current = programs.firstOrNull {
            it.startEpochSec <= s.focusTimeEpoch && s.focusTimeEpoch < it.endEpochSec
        }
        if (current != null) {
            val nextProg = programs.firstOrNull { it.startEpochSec >= current.endEpochSec }
            if (nextProg != null) {
                _uiState.value = s.copy(focusTimeEpoch = nextProg.startEpochSec + 1)
                adjustTimeWindow()
            }
        } else {
            val nextProg = programs.firstOrNull { it.startEpochSec > s.focusTimeEpoch }
            if (nextProg != null) {
                _uiState.value = s.copy(focusTimeEpoch = nextProg.startEpochSec + 1)
                adjustTimeWindow()
            }
        }
    }

    private fun adjustTimeWindow() {
        val s = _uiState.value
        val now = System.currentTimeMillis() / 1000
        val windowEnd = s.timeWindowStartEpoch + s.visibleDurationSec
        if (s.focusTimeEpoch >= windowEnd - 900) {
            // Push the window forward so the focused program stays visible.
            // Window start never goes earlier than NOW.
            val newStart = maxOf(now, s.focusTimeEpoch - s.visibleDurationSec / 2)
            _uiState.value = s.copy(timeWindowStartEpoch = newStart)
        }
    }

    /** Reset window so the leftmost slot is NOW. */
    fun resetTimeWindowToNow() {
        val now = System.currentTimeMillis() / 1000
        _uiState.value = _uiState.value.copy(
            focusTimeEpoch = now,
            timeWindowStartEpoch = now
        )
    }

    // ── Select / Long Press ─────────────────────────────────────────────────

    fun selectFocused() {
        val s = _uiState.value
        val focused = s.focusedChannel ?: return
        if (focused.guideNumber == s.currentChannel?.guideNumber) {
            // Already tuned: enter fullscreen
            enterFullScreen()
        } else {
            tuneToChannel(focused)
        }
    }

    // ── Categories ──────────────────────────────────────────────────────────

    fun selectCategory(index: Int) {
        val s = _uiState.value
        if (index in s.categories.indices) {
            _uiState.value = s.copy(selectedCategoryIndex = index, focusedRow = 0)
        }
    }

    // ── Mode transitions ────────────────────────────────────────────────────

    fun enterFullScreen() {
        _uiState.value = _uiState.value.copy(
            mode = ScreenMode.FULLSCREEN,
            navRailVisible = false,
            showFullscreenOverlay = false
        )
    }

    fun enterGuide() {
        resetTimeWindowToNow()
        _uiState.value = _uiState.value.copy(
            mode = ScreenMode.GUIDE,
            navRailVisible = false,
            showFullscreenOverlay = false
        )
        // Snap focused row to current channel
        val s = _uiState.value
        val idx = s.filteredChannels.indexOfFirst { it.guideNumber == s.currentChannel?.guideNumber }
        if (idx >= 0) {
            _uiState.value = s.copy(focusedRow = idx)
        }
    }

    fun showNavRail() {
        _uiState.value = _uiState.value.copy(navRailVisible = true)
    }

    fun hideNavRail() {
        _uiState.value = _uiState.value.copy(navRailVisible = false)
    }

    // ── Fullscreen UP overlay ───────────────────────────────────────────────

    fun showFullscreenOverlay() {
        _uiState.value = _uiState.value.copy(showFullscreenOverlay = true, actionButtonIndex = 0)
        fullscreenOverlayJob?.cancel()
        fullscreenOverlayJob = viewModelScope.launch {
            delay(8000)
            _uiState.value = _uiState.value.copy(showFullscreenOverlay = false)
        }
    }

    fun hideFullscreenOverlay() {
        fullscreenOverlayJob?.cancel()
        _uiState.value = _uiState.value.copy(showFullscreenOverlay = false)
    }

    fun overlayNavigateLeft() {
        val s = _uiState.value
        if (s.actionButtonIndex > 0) {
            _uiState.value = s.copy(actionButtonIndex = s.actionButtonIndex - 1)
        }
        resetOverlayTimer()
    }

    fun overlayNavigateRight() {
        val s = _uiState.value
        if (s.actionButtonIndex < 3) {
            _uiState.value = s.copy(actionButtonIndex = s.actionButtonIndex + 1)
        }
        resetOverlayTimer()
    }

    private fun resetOverlayTimer() {
        fullscreenOverlayJob?.cancel()
        fullscreenOverlayJob = viewModelScope.launch {
            delay(8000)
            _uiState.value = _uiState.value.copy(showFullscreenOverlay = false)
        }
    }

    fun activateOverlayButton() {
        when (_uiState.value.actionButtonIndex) {
            0 -> {
                // MultiView
                hideFullscreenOverlay()
                startMultiView()
            }
            1 -> showToast("Recording coming soon")
            2 -> toggleMute()
            3 -> showToast("Quality settings coming soon")
        }
    }

    fun toggleMute() {
        _uiState.value = _uiState.value.copy(isMuted = !_uiState.value.isMuted)
    }

    // ── MultiView ───────────────────────────────────────────────────────────

    /**
     * Begin a new MultiView session: pane 0 is the currently watched channel,
     * pane 1 is empty + guide overlay opens for picking the second channel.
     */
    fun startMultiView() {
        val s = _uiState.value
        if (s.tunerCount < 2) {
            showToast("Maximum ${s.tunerCount} simultaneous streams")
            return
        }
        val current = s.currentChannel
        val pane0 = PaneState(current, s.streamUrl)
        val pane1 = PaneState(null, null)
        _uiState.value = s.copy(
            mode = ScreenMode.MULTIVIEW,
            multiViewPanes = listOf(pane0, pane1),
            activePaneIndex = 1,
            guideOverlayVisible = true
        )
    }

    /**
     * Add another empty pane and open the guide overlay to pick a channel for it.
     * Bound to "press MultiView again" while inside MULTIVIEW.
     */
    fun addPaneForPick() {
        val s = _uiState.value
        if (s.mode != ScreenMode.MULTIVIEW) return
        if (s.multiViewPanes.size >= s.tunerCount) {
            showToast("Maximum ${s.tunerCount} simultaneous streams")
            return
        }
        val newPanes = s.multiViewPanes + PaneState(null, null)
        _uiState.value = s.copy(
            multiViewPanes = newPanes,
            activePaneIndex = newPanes.size - 1,
            guideOverlayVisible = true
        )
    }

    fun removeLastPane() {
        val s = _uiState.value
        val panes = s.multiViewPanes
        if (panes.size <= 2) {
            // Return to single view, keep pane 0 as current channel
            val first = panes.firstOrNull { it.channel != null }
            _uiState.value = s.copy(
                mode = ScreenMode.FULLSCREEN,
                multiViewPanes = emptyList(),
                currentChannel = first?.channel ?: s.currentChannel,
                streamUrl = first?.streamUrl ?: s.streamUrl,
                guideOverlayVisible = false,
                activePaneIndex = 0
            )
        } else {
            val newPanes = panes.dropLast(1)
            _uiState.value = s.copy(
                multiViewPanes = newPanes,
                activePaneIndex = s.activePaneIndex.coerceAtMost(newPanes.size - 1)
            )
        }
    }

    fun switchActivePane(direction: MultiViewNavDirection) {
        val s = _uiState.value
        val count = s.multiViewPanes.size
        if (count <= 1) return
        val cols = if (count <= 2) count else 2
        val current = s.activePaneIndex
        val row = current / cols
        val col = current % cols
        val rows = (count + cols - 1) / cols
        val newIndex = when (direction) {
            MultiViewNavDirection.LEFT -> if (col > 0) current - 1 else current
            MultiViewNavDirection.RIGHT -> if (col < cols - 1 && current + 1 < count) current + 1 else current
            MultiViewNavDirection.UP -> if (row > 0) current - cols else current
            MultiViewNavDirection.DOWN -> {
                if (row < rows - 1 && current + cols < count) current + cols else current
            }
        }
        if (newIndex != current) {
            _uiState.value = s.copy(activePaneIndex = newIndex)
        }
    }

    fun showGuideOverlay() {
        _uiState.value = _uiState.value.copy(guideOverlayVisible = true)
    }

    fun hideGuideOverlay() {
        val s = _uiState.value
        // If active pane is empty, removing the overlay should drop that empty pane.
        val active = s.multiViewPanes.getOrNull(s.activePaneIndex)
        if (active != null && active.channel == null && s.multiViewPanes.size > 1) {
            val newPanes = s.multiViewPanes.toMutableList()
            newPanes.removeAt(s.activePaneIndex)
            _uiState.value = s.copy(
                multiViewPanes = newPanes,
                activePaneIndex = (s.activePaneIndex - 1).coerceAtLeast(0),
                guideOverlayVisible = false
            )
            if (newPanes.size < 2) {
                removeLastPane()
            }
        } else {
            _uiState.value = s.copy(guideOverlayVisible = false)
        }
    }

    fun tuneActivePaneToFocused() {
        val s = _uiState.value
        val channel = s.focusedChannel ?: return
        if (channel.guideNumber.isNullOrBlank()) return
        val url = streamRepo.getStreamUrl(channel.guideNumber)
        val newPanes = s.multiViewPanes.toMutableList()
        val idx = s.activePaneIndex
        if (idx < newPanes.size) {
            newPanes[idx] = PaneState(channel, url, isTuning = true)
            _uiState.value = s.copy(multiViewPanes = newPanes, guideOverlayVisible = false)
        }
    }

    // ── Channel Up/Down (remote buttons) ────────────────────────────────────

    fun channelUp() {
        val s = _uiState.value
        val channels = s.filteredChannels
        val current = s.currentChannel ?: return
        val idx = channels.indexOf(current)
        if (idx < channels.size - 1) {
            tuneToChannel(channels[idx + 1])
            _uiState.value = _uiState.value.copy(focusedRow = idx + 1)
        }
    }

    fun channelDown() {
        val s = _uiState.value
        val channels = s.filteredChannels
        val current = s.currentChannel ?: return
        val idx = channels.indexOf(current)
        if (idx > 0) {
            tuneToChannel(channels[idx - 1])
            _uiState.value = _uiState.value.copy(focusedRow = idx - 1)
        }
    }

    // ── Toast ───────────────────────────────────────────────────────────────

    fun showToast(message: String) {
        _uiState.value = _uiState.value.copy(toastMessage = message)
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(3000)
            _uiState.value = _uiState.value.copy(toastMessage = null)
        }
    }

    fun clearToast() {
        toastJob?.cancel()
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
