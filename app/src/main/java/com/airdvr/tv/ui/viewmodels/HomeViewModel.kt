package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.AirDVRApp
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.models.EpgProgram
import com.airdvr.tv.data.models.Recording
import com.airdvr.tv.data.repository.ArtworkRepository
import com.airdvr.tv.data.repository.GuideRepository
import com.airdvr.tv.data.repository.RecordingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LiveChannelEntry(
    val channel: Channel,
    val program: EpgProgram?
)

data class UpcomingEntry(
    val channel: Channel,
    val program: EpgProgram
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val heroChannel: Channel? = null,
    val heroProgram: EpgProgram? = null,
    val heroEntries: List<LiveChannelEntry> = emptyList(),
    val liveNow: List<LiveChannelEntry> = emptyList(),
    val recordings: List<Recording> = emptyList(),
    val upcoming: List<UpcomingEntry> = emptyList(),
    val userInitial: String = "",
    val error: String? = null
)

class HomeViewModel : ViewModel() {

    private val guideRepo = GuideRepository()
    private val recordingsRepo = RecordingsRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        val email = runCatching { AirDVRApp.instance.tokenManager.getUserEmail() }.getOrDefault("")
        val initial = email.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        _uiState.value = _uiState.value.copy(userInitial = initial)
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val guideDeferred = async { guideRepo.getChannelsWithPrograms() }
                val recordingsDeferred = async { recordingsRepo.getRecordings() }

                val guideResult = guideDeferred.await()
                val recordingsResult = recordingsDeferred.await()

                var newState = _uiState.value.copy(isLoading = false)

                guideResult.onSuccess { (channels, programs) ->
                    val sorted = channels.sortedBy { it.guideNumber?.toDoubleOrNull() ?: Double.MAX_VALUE }
                    val byChannel = if (programs.any { it.guideNumber != null }) {
                        programs.filter { it.guideNumber != null }.groupBy { it.guideNumber!! }
                    } else emptyMap()

                    val now = System.currentTimeMillis() / 1000
                    val twoHours = now + (2 * 3600L)

                    fun currentProgram(ch: Channel): EpgProgram? {
                        val list = byChannel[ch.guideNumber ?: ""] ?: return null
                        return list.firstOrNull { it.startEpochSec <= now && now < it.endEpochSec }
                    }

                    val liveNow = sorted.take(30).map { ch -> LiveChannelEntry(ch, currentProgram(ch)) }
                    val hero = sorted.firstOrNull { it.favorite == true } ?: sorted.firstOrNull()
                    val heroProg = hero?.let { currentProgram(it) }

                    // Hero cycling: first 10 live entries
                    val heroEntries = liveNow.take(10)

                    val upcomingRaw = sorted.flatMap { ch ->
                        val list = byChannel[ch.guideNumber ?: ""] ?: emptyList()
                        list.filter { it.startEpochSec in (now + 1)..twoHours }
                            .map { UpcomingEntry(ch, it) }
                    }.sortedBy { it.program.startEpochSec }
                    // Deduplicate by show title
                    val seenTitles = mutableSetOf<String>()
                    val upcoming = upcomingRaw.filter { entry ->
                        val title = entry.program.title ?: return@filter true
                        seenTitles.add(title)
                    }.take(30)

                    newState = newState.copy(
                        heroChannel = hero,
                        heroProgram = heroProg,
                        heroEntries = heroEntries,
                        liveNow = liveNow,
                        upcoming = upcoming
                    )

                    // Pre-fetch backdrops for hero entries
                    heroEntries.forEach { entry ->
                        entry.program?.title?.let { title ->
                            launch { ArtworkRepository.fetchBackdrop(title) }
                        }
                    }
                }.onFailure { e -> newState = newState.copy(error = e.message) }

                recordingsResult.onSuccess { recordings ->
                    val sorted = recordings.sortedByDescending { it.startEpochSec }.take(20)
                    newState = newState.copy(recordings = sorted)
                }

                _uiState.value = newState
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}
