package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.AirDVRApp
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.models.EpgProgram
import com.airdvr.tv.data.models.RecordingSchedule
import com.airdvr.tv.data.models.ScheduleRequest
import android.util.Log
import com.airdvr.tv.data.repository.ChannelLogoRepository
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
    val categoriesFocused: Boolean = false,

    // MultiView
    val multiViewPanes: List<PaneState> = emptyList(),
    val activePaneIndex: Int = 0,
    val tunerCount: Int = 2,

    // Toast
    val toastMessage: String? = null,

    // Nav rail
    val navRailVisible: Boolean = false,
    val navRailFocusedIndex: Int = 0,

    // Fullscreen overlay (UP press: action buttons + show details + artwork)
    val showFullscreenOverlay: Boolean = false,

    // Slim now playing bar (auto-hides after 5s in fullscreen)
    val nowPlayingBarVisible: Boolean = true,

    // Mute
    val isMuted: Boolean = false,

    // Closed captions
    val ccEnabled: Boolean = false,

    // Guide appearance
    val guideOpacity: Float = 0.7f,
    val guideColorHex: String = "#21262D",

    // Action overlay button navigation (5 buttons in fullscreen overlay)
    val actionButtonIndex: Int = 0,

    // User profile
    val userInitial: String = "",

    // Recording schedules
    val schedules: List<RecordingSchedule> = emptyList(),

    // User storage preference
    val defaultStoragePreference: String = "local",
    val userPlan: String = "free"
) {
    val filteredChannels: List<Channel>
        get() {
            val cat = categories.getOrNull(selectedCategoryIndex) ?: "All Channels"
            if (cat == "All Channels") return channels
            // Map category label → regex pattern (matched against current program category)
            val pattern = when (cat) {
                "News" -> Regex("news", RegexOption.IGNORE_CASE)
                "Sports" -> Regex("sport", RegexOption.IGNORE_CASE)
                "Entertainment" -> Regex("entertainment|comedy|drama|reality", RegexOption.IGNORE_CASE)
                "Movies" -> Regex("movie|film", RegexOption.IGNORE_CASE)
                "Kids" -> Regex("kids|children|animation|cartoon", RegexOption.IGNORE_CASE)
                else -> return channels
            }
            val now = System.currentTimeMillis() / 1000
            return channels.filter { ch ->
                val progs = programsByChannel[ch.guideNumber ?: ""] ?: emptyList()
                val current = progs.firstOrNull { it.startEpochSec <= now && now < it.endEpochSec }
                current?.category?.let { pattern.containsMatchIn(it) } == true
            }
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
    private val guidePrefsManager = AirDVRApp.instance.guidePreferencesManager

    private val _uiState = MutableStateFlow(LiveTVUiState())
    val uiState: StateFlow<LiveTVUiState> = _uiState.asStateFlow()

    private var fullscreenOverlayJob: Job? = null
    private var nowPlayingBarJob: Job? = null
    private var toastJob: Job? = null

    init {
        val now = System.currentTimeMillis() / 1000
        val windowStart = floorTo30Min(now)
        val email = runCatching { AirDVRApp.instance.tokenManager.getUserEmail() }.getOrDefault("")
        val initial = email.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        _uiState.value = _uiState.value.copy(
            focusTimeEpoch = now,
            timeWindowStartEpoch = windowStart,
            userInitial = initial,
            ccEnabled = guidePrefsManager.ccEnabled.value,
            guideOpacity = guidePrefsManager.opacity.value,
            guideColorHex = guidePrefsManager.color.value
        )
        viewModelScope.launch {
            guidePrefsManager.opacity.collect {
                _uiState.value = _uiState.value.copy(guideOpacity = it)
            }
        }
        viewModelScope.launch {
            guidePrefsManager.color.collect {
                _uiState.value = _uiState.value.copy(guideColorHex = it)
            }
        }
        loadData()
    }

    /** Round an epoch second value down to the nearest 30-minute boundary (in local time). */
    private fun floorTo30Min(epochSec: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = epochSec * 1000L
        val minute = cal.get(java.util.Calendar.MINUTE)
        cal.set(java.util.Calendar.MINUTE, if (minute >= 30) 30 else 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000L
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Load channel logos, schedules, and user profile in parallel
            launch { ChannelLogoRepository.loadLogos() }
            launch { fetchSchedules() }
            launch { fetchUserProfile() }

            try {
                val resp = api.getTuners()
                if (resp.isSuccessful) {
                    val count = resp.body()?.total ?: 2
                    _uiState.value = _uiState.value.copy(tunerCount = count.coerceIn(1, 4))
                }
            } catch (_: Exception) {}

            guideRepo.getChannelsWithPrograms().onSuccess { (channels, programs) ->
                val sorted = channels.sortedBy { ch ->
                    val parts = ch.guideNumber?.split(".") ?: return@sortedBy Double.MAX_VALUE
                    val major = parts.getOrNull(0)?.toDoubleOrNull() ?: return@sortedBy Double.MAX_VALUE
                    val minor = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                    major * 1000 + minor
                }
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
                // Log programs per channel to verify 24h data
                val sampleCh = sorted.firstOrNull()?.guideNumber ?: ""
                val sampleProgs = grouped[sampleCh] ?: emptyList()
                if (sampleProgs.isNotEmpty()) {
                    val earliest = sampleProgs.minOf { it.startEpochSec }
                    val latest = sampleProgs.maxOf { it.endEpochSec }
                    Log.d("GUIDE", "Channel $sampleCh: ${sampleProgs.size} programs, ${(latest - earliest) / 3600.0}h range")
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

    fun tuneToChannel(channel: Channel) {
        if (channel.guideNumber.isNullOrBlank()) return
        val quality = guidePrefsManager.quality.value
        val url = streamRepo.getStreamUrl(channel.guideNumber, quality)
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

    /**
     * UP in guide. Returns true if focus moved within rows; false if already at the top
     * (caller should focus the category bar).
     */
    fun navigateUp(): Boolean {
        val s = _uiState.value
        return if (s.focusedRow > 0) {
            _uiState.value = s.copy(focusedRow = s.focusedRow - 1)
            true
        } else {
            false
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
        val chNum = s.focusedChannel?.guideNumber ?: return false
        val programs = s.programsByChannel[chNum]?.sortedBy { it.startEpochSec } ?: return false
        val current = programs.firstOrNull {
            it.startEpochSec <= s.focusTimeEpoch && s.focusTimeEpoch < it.endEpochSec
        }
        if (current != null) {
            val prevProg = programs.lastOrNull { it.endEpochSec <= current.startEpochSec }
            if (prevProg != null) {
                _uiState.value = s.copy(focusTimeEpoch = prevProg.startEpochSec + 1)
                adjustTimeWindow()
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
        val windowEnd = s.timeWindowStartEpoch + s.visibleDurationSec
        if (s.focusTimeEpoch >= windowEnd - 900) {
            // Push the window forward so the focused program stays visible.
            val newStart = floorTo30Min(s.focusTimeEpoch - s.visibleDurationSec / 2)
            _uiState.value = s.copy(timeWindowStartEpoch = newStart)
        } else if (s.focusTimeEpoch < s.timeWindowStartEpoch) {
            // Scroll backward when focus is before the window start
            val newStart = floorTo30Min(s.focusTimeEpoch)
            _uiState.value = s.copy(timeWindowStartEpoch = newStart)
        }
    }

    /** Reset window so the leftmost slot is the most recent 30-min mark before NOW. */
    fun resetTimeWindowToNow() {
        val now = System.currentTimeMillis() / 1000
        _uiState.value = _uiState.value.copy(
            focusTimeEpoch = now,
            timeWindowStartEpoch = floorTo30Min(now)
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

    fun categoryNavigateLeft() {
        val s = _uiState.value
        if (s.selectedCategoryIndex > 0) {
            _uiState.value = s.copy(
                selectedCategoryIndex = s.selectedCategoryIndex - 1,
                focusedRow = 0
            )
        }
    }

    fun categoryNavigateRight() {
        val s = _uiState.value
        if (s.selectedCategoryIndex < s.categories.size - 1) {
            _uiState.value = s.copy(
                selectedCategoryIndex = s.selectedCategoryIndex + 1,
                focusedRow = 0
            )
        }
    }

    fun focusCategories() {
        _uiState.value = _uiState.value.copy(categoriesFocused = true)
    }

    fun unfocusCategories() {
        _uiState.value = _uiState.value.copy(categoriesFocused = false)
    }

    // ── Mode transitions ────────────────────────────────────────────────────

    fun enterFullScreen() {
        _uiState.value = _uiState.value.copy(
            mode = ScreenMode.FULLSCREEN,
            navRailVisible = false,
            showFullscreenOverlay = false
        )
        pingNowPlayingBar()
    }

    /** Show the slim now-playing bar and (re)start the 5s auto-hide timer. */
    fun pingNowPlayingBar() {
        _uiState.value = _uiState.value.copy(nowPlayingBarVisible = true)
        nowPlayingBarJob?.cancel()
        nowPlayingBarJob = viewModelScope.launch {
            delay(5000)
            _uiState.value = _uiState.value.copy(nowPlayingBarVisible = false)
        }
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
        _uiState.value = _uiState.value.copy(navRailVisible = true, navRailFocusedIndex = 0)
    }

    fun hideNavRail() {
        _uiState.value = _uiState.value.copy(navRailVisible = false)
    }

    fun navRailUp() {
        val s = _uiState.value
        if (s.navRailFocusedIndex > 0) {
            _uiState.value = s.copy(navRailFocusedIndex = s.navRailFocusedIndex - 1)
        }
    }

    fun navRailDown() {
        val s = _uiState.value
        // 7 items: Home, Where to Watch, Sports, Recordings, Custom, Live TV, Settings
        if (s.navRailFocusedIndex < 6) {
            _uiState.value = s.copy(navRailFocusedIndex = s.navRailFocusedIndex + 1)
        }
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
        if (s.actionButtonIndex < 4) {
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
            1 -> recordCurrentProgram()
            2 -> toggleMute()
            3 -> cycleQuality()
            4 -> toggleCC()
        }
    }

    fun toggleMute() {
        _uiState.value = _uiState.value.copy(isMuted = !_uiState.value.isMuted)
    }

    fun toggleCC() {
        guidePrefsManager.toggleCC()
        _uiState.value = _uiState.value.copy(ccEnabled = guidePrefsManager.ccEnabled.value)
    }

    fun cycleQuality() {
        val options = listOf("Auto", "1080p", "720p", "480p")
        val current = guidePrefsManager.quality.value
        val nextIndex = (options.indexOf(current) + 1) % options.size
        val newQuality = options[nextIndex]
        guidePrefsManager.setQuality(newQuality)
        showToast("Quality: $newQuality")
        val ch = _uiState.value.currentChannel ?: return
        val url = streamRepo.getStreamUrl(ch.guideNumber, newQuality)
        _uiState.value = _uiState.value.copy(streamUrl = url, isTuning = true)
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

    /** Exit MultiView entirely and return to single-pane fullscreen on pane 0's channel. */
    fun exitMultiView() {
        val s = _uiState.value
        val firstWithChannel = s.multiViewPanes.firstOrNull { it.channel != null }
        _uiState.value = s.copy(
            mode = ScreenMode.FULLSCREEN,
            multiViewPanes = emptyList(),
            currentChannel = firstWithChannel?.channel ?: s.currentChannel,
            streamUrl = firstWithChannel?.streamUrl ?: s.streamUrl,
            guideOverlayVisible = false,
            activePaneIndex = 0
        )
        pingNowPlayingBar()
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
        val quality = guidePrefsManager.quality.value
        val url = streamRepo.getStreamUrl(channel.guideNumber, quality)
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

    // ── User Profile ───────────────────────────────────────────────────────

    private suspend fun fetchUserProfile() {
        try {
            val resp = api.getUserProfile()
            if (resp.isSuccessful) {
                val profile = resp.body()
                _uiState.value = _uiState.value.copy(
                    defaultStoragePreference = profile?.storagePreference ?: "local",
                    userPlan = profile?.plan ?: "free"
                )
            }
        } catch (_: Exception) { }
    }

    // ── Recording ───────────────────────────────────────────────────────────

    private suspend fun fetchSchedules() {
        try {
            val resp = api.getRecordingSchedules()
            if (resp.isSuccessful) {
                _uiState.value = _uiState.value.copy(schedules = resp.body() ?: emptyList())
            }
        } catch (_: Exception) { }
    }

    fun recordCurrentProgram() {
        val s = _uiState.value
        val channel = s.currentChannel ?: return
        val program = s.currentProgram
        val now = System.currentTimeMillis() / 1000
        val title = program?.title ?: channel.guideName ?: "Manual Recording"
        val channelNumber = channel.guideNumber ?: return

        val isAiring = program != null && program.startEpochSec <= now && now < program.endEpochSec
        val startTime = if (isAiring) {
            java.time.Instant.now().toString()
        } else {
            program?.startTime ?: java.time.Instant.now().toString()
        }
        val endTime = program?.endTime ?: java.time.Instant.ofEpochSecond(now + 3600).toString()
        val type = if (isAiring) "manual" else "once"

        viewModelScope.launch {
            try {
                val resp = api.scheduleRecording(
                    ScheduleRequest(channelNumber, title, startTime, endTime, type)
                )
                if (resp.isSuccessful) {
                    val msg = if (isAiring) "Recording started: $title" else "Recording scheduled: $title"
                    showToast(msg)
                    fetchSchedules()
                } else {
                    showToast("Could not schedule recording")
                }
            } catch (_: Exception) {
                showToast("Could not connect. Check your network.")
            }
        }
    }

    /** Schedule a recording for a specific program (from guide grid). */
    fun scheduleProgram(channel: Channel, program: EpgProgram) {
        scheduleProgramWithStorage(channel, program, null)
    }

    /** Schedule a recording with a specific storage preference. */
    fun scheduleProgramWithStorage(channel: Channel, program: EpgProgram, storage: String?) {
        val channelNumber = channel.guideNumber ?: return
        val title = program.title ?: "Recording"
        val now = System.currentTimeMillis() / 1000
        val isAiring = program.startEpochSec <= now && now < program.endEpochSec
        val startTime = if (isAiring) java.time.Instant.now().toString() else (program.startTime ?: return)
        val endTime = program.endTime ?: return
        val type = if (isAiring) "manual" else "once"

        // Validate cloud storage requires Pro
        val actualStorage = if (storage == "cloud" && _uiState.value.userPlan.lowercase() !in listOf("pro", "premium")) {
            showToast("Cloud storage requires Pro subscription")
            "local"
        } else storage

        viewModelScope.launch {
            try {
                val resp = api.scheduleRecording(
                    ScheduleRequest(channelNumber, title, startTime, endTime, type, storagePreference = actualStorage)
                )
                if (resp.isSuccessful) {
                    val msg = if (isAiring) "Recording started: $title" else "Recording scheduled: $title"
                    showToast(msg)
                    fetchSchedules()
                } else {
                    showToast("Could not schedule recording")
                }
            } catch (_: Exception) {
                showToast("Could not connect. Check your network.")
            }
        }
    }

    fun isScheduled(program: EpgProgram): Boolean {
        return _uiState.value.schedules.any { sched ->
            sched.title == program.title && sched.channelNumber == program.guideNumber
        }
    }

    fun isActivelyRecording(program: EpgProgram): Boolean {
        val now = System.currentTimeMillis() / 1000
        return _uiState.value.schedules.any { sched ->
            sched.title == program.title &&
            sched.channelNumber == program.guideNumber &&
            (sched.status == "recording" || sched.status == "active" ||
             (program.startEpochSec <= now && now < program.endEpochSec && sched.type == "manual"))
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
