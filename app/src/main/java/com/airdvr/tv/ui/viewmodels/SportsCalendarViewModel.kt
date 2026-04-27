package com.airdvr.tv.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.models.EpgProgram
import com.airdvr.tv.data.models.RecordingSchedule
import com.airdvr.tv.data.models.ScheduleRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One sporting event surfaced on the Sports Calendar screen — pre-joined
 * with its channel so the screen can render network/channel info without
 * a second lookup.
 */
data class SportsEvent(
    val program: EpgProgram,
    val channel: Channel?,
    val league: String,
    val homeTeam: String?,
    val awayTeam: String?
) {
    val startEpochSec: Long get() = program.startEpochSec
    val endEpochSec: Long get() = program.endEpochSec
    val title: String get() = program.title ?: ""
    val isLive: Boolean
        get() {
            val now = System.currentTimeMillis() / 1000
            return startEpochSec <= now && now < endEpochSec
        }
    val networkLabel: String?
        get() = channel?.guideName
    val channelNumber: String?
        get() = channel?.guideNumber ?: program.guideNumber
}

/** Sports Calendar UI state. */
data class SportsCalendarUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedLeague: String = "all",
    val todaysGames: List<SportsEvent> = emptyList(),
    val upcomingByDay: List<DaySection> = emptyList(),
    val expandedDayKeys: Set<String> = emptySet(),
    val schedules: List<RecordingSchedule> = emptyList(),
    val toastMessage: String? = null
)

data class DaySection(
    val key: String,
    val label: String,
    val date: LocalDate,
    val events: List<SportsEvent>
)

class SportsCalendarViewModel : ViewModel() {

    private val api = ApiClient.api

    private val _uiState = MutableStateFlow(SportsCalendarUiState())
    val uiState: StateFlow<SportsCalendarUiState> = _uiState.asStateFlow()

    // Cache of all sports events (unfiltered) so changing the league filter
    // doesn't refetch.
    private var allEvents: List<SportsEvent> = emptyList()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val now = java.time.Instant.now()
                val start = now.toString()
                val end = now.plusSeconds(14L * 24 * 3600).toString()
                val resp = api.getGuide(start = start, end = end, hours = 336, limit = 50000)
                if (!resp.isSuccessful) {
                    Log.d("SPORTSCAL", "Guide fetch failed: ${resp.code()}")
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Could not load sports schedule")
                    return@launch
                }
                val body = resp.body()
                val channels = body?.channels ?: emptyList()
                val programs = body?.programs ?: emptyList()
                val byNumber = channels.associateBy { it.guideNumber }
                val sports = programs
                    .filter { isSports(it) }
                    .map { p ->
                        val (away, home) = parseTeams(p.title ?: "") ?: (null to null)
                        SportsEvent(
                            program = p,
                            channel = byNumber[p.guideNumber],
                            league = detectLeague(p.title ?: ""),
                            homeTeam = home,
                            awayTeam = away
                        )
                    }
                    .sortedBy { it.startEpochSec }
                Log.d("SPORTSCAL", "Found ${sports.size} sports events out of ${programs.size} programs")
                allEvents = sports
                applyFilter(_uiState.value.selectedLeague)

                // Fetch existing schedules so we can show "Already scheduled" hints.
                fetchSchedules()
            } catch (e: Exception) {
                Log.d("SPORTSCAL", "Exception: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Network error: ${e.message}")
            }
        }
    }

    private suspend fun fetchSchedules() {
        try {
            val resp = api.getRecordingSchedules()
            if (resp.isSuccessful) {
                _uiState.value = _uiState.value.copy(schedules = resp.body() ?: emptyList())
            }
        } catch (_: Exception) { /* non-fatal */ }
    }

    fun setLeague(league: String) {
        applyFilter(league)
    }

    fun toggleDayExpanded(key: String) {
        val expanded = _uiState.value.expandedDayKeys
        _uiState.value = _uiState.value.copy(
            expandedDayKeys = if (expanded.contains(key)) expanded - key else expanded + key
        )
    }

    private fun applyFilter(league: String) {
        val filtered = if (league == "all") allEvents else allEvents.filter { it.league == league }
        // Sort tier 1 leagues first, then by start time.
        val tier1 = setOf("nfl", "nba", "mlb", "nhl")
        val sorted = filtered.sortedWith(
            compareBy<SportsEvent> { if (tier1.contains(it.league)) 0 else 1 }
                .thenBy { it.startEpochSec }
        )

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val byDate = sorted.groupBy { ev ->
            java.time.Instant.ofEpochSecond(ev.startEpochSec).atZone(zone).toLocalDate()
        }

        // Today: live events first, then upcoming. Past events (already finished today) are excluded.
        val now = System.currentTimeMillis() / 1000
        val todays = (byDate[today] ?: emptyList())
            .filter { it.endEpochSec > now }
            .sortedWith(
                compareBy<SportsEvent> { if (it.isLive) 0 else 1 }
                    .thenBy { it.startEpochSec }
            )

        // Upcoming: tomorrow → +13d
        val sections = (1..13).mapNotNull { offset ->
            val date = today.plusDays(offset.toLong())
            val events = byDate[date] ?: return@mapNotNull null
            if (events.isEmpty()) return@mapNotNull null
            DaySection(
                key = date.toString(),
                label = formatDayLabel(date, offset),
                date = date,
                events = events
            )
        }

        // Tomorrow expanded by default; preserve any user expansions already in state.
        val current = _uiState.value.expandedDayKeys
        val tomorrowKey = today.plusDays(1).toString()
        val expanded = if (current.isEmpty()) setOf(tomorrowKey) else current

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            selectedLeague = league,
            todaysGames = todays,
            upcomingByDay = sections,
            expandedDayKeys = expanded
        )
    }

    private fun formatDayLabel(date: LocalDate, offset: Int): String {
        return when (offset) {
            1 -> "Tomorrow"
            else -> {
                val dayName = date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
                val md = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                "$dayName, $md"
            }
        }
    }

    fun recordEvent(event: SportsEvent, type: String = "once") {
        val channelNumber = event.channelNumber ?: run {
            showToast("Channel not available")
            return
        }
        val title = event.title.ifBlank { "Sports Recording" }
        val now = System.currentTimeMillis() / 1000
        val isAiring = event.startEpochSec <= now && now < event.endEpochSec
        val startTime = if (isAiring) java.time.Instant.now().toString()
            else event.program.startTime ?: return
        val endTime = event.program.endTime ?: return
        val resolvedType = if (isAiring && type == "once") "manual" else type

        viewModelScope.launch {
            try {
                val resp = api.scheduleRecording(
                    ScheduleRequest(
                        channelNumber = channelNumber,
                        title = title,
                        startTime = startTime,
                        endTime = endTime,
                        type = resolvedType
                    )
                )
                if (resp.isSuccessful) {
                    showToast(if (isAiring) "Recording started: $title" else "Recording scheduled: $title")
                    fetchSchedules()
                } else {
                    showToast("Could not schedule recording")
                }
            } catch (_: Exception) {
                showToast("Could not connect. Check your network.")
            }
        }
    }

    fun isScheduled(event: SportsEvent): Boolean {
        return _uiState.value.schedules.any { sched ->
            sched.title == event.title && sched.channelNumber == event.channelNumber
        }
    }

    private fun showToast(msg: String) {
        _uiState.value = _uiState.value.copy(toastMessage = msg)
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.value = _uiState.value.copy(toastMessage = null)
        }
    }

    // ── Sports filtering ────────────────────────────────────────────────────

    companion object {
        private val SPORTS_TITLE_REGEX = Regex(
            """\b(nfl|nba|mlb|nhl|ncaa|mls|pga|ufc|mma|wwe|nascar|tennis|golf|soccer|football|basketball|baseball|hockey)\b""",
            RegexOption.IGNORE_CASE
        )
        private val TEAMS_REGEX = Regex(
            """^\s*(?::\s*)?(.+?)\s+(?:at|vs\.?|@)\s+(.+?)(?:\s*$|\s*[-–:])""",
            RegexOption.IGNORE_CASE
        )

        fun isSports(program: EpgProgram): Boolean {
            val title = (program.title ?: "").lowercase()
            val category = (program.category ?: "").lowercase()
            return category.contains("sport") || SPORTS_TITLE_REGEX.containsMatchIn(title)
        }

        fun detectLeague(title: String): String {
            val t = title.lowercase()
            return when {
                t.contains("nfl") || (t.contains("football") && !t.contains("ncaa") && !t.contains("college")) -> "nfl"
                t.contains("nba") || (t.contains("basketball") && !t.contains("ncaa") && !t.contains("college")) -> "nba"
                t.contains("mlb") || t.contains("baseball") -> "mlb"
                t.contains("nhl") || t.contains("hockey") -> "nhl"
                t.contains("ncaa") || t.contains("college") -> "ncaa"
                t.contains("mls") || t.contains("soccer") || t.contains("premier league") -> "soccer"
                t.contains("pga") || t.contains("golf") -> "golf"
                t.contains("ufc") || t.contains("mma") || t.contains("boxing") -> "ufc"
                t.contains("nascar") || t.contains("f1") || t.contains("formula 1") -> "nascar"
                t.contains("tennis") -> "tennis"
                t.contains("wwe") || t.contains("wrestling") -> "wwe"
                else -> "sport"
            }
        }

        /**
         * Parse a matchup title into (away, home) team names. Returns null if
         * the title doesn't follow a recognised "X at Y" / "X vs Y" pattern.
         */
        fun parseTeams(title: String): Pair<String, String>? {
            // Strip a leading league prefix like "NFL Football: " before matching.
            val stripped = title
                .replace(Regex("^(NFL|NBA|MLB|NHL|NCAA|MLS|PGA|UFC|UFC Fight Night)[^:]*:\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^(College Football|College Basketball|MLS Soccer|Premier League)[^:]*:\\s*", RegexOption.IGNORE_CASE), "")
            val match = TEAMS_REGEX.find(stripped) ?: return null
            val away = match.groupValues[1].trim()
            val home = match.groupValues[2].trim()
            if (away.isBlank() || home.isBlank()) return null
            return away to home
        }
    }
}
