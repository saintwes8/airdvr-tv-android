package com.airdvr.tv.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.models.EpgProgram
import com.airdvr.tv.data.models.EspnArticle
import com.airdvr.tv.data.models.GameScore
import com.airdvr.tv.data.models.RecordingSchedule
import com.airdvr.tv.data.models.ScheduleRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One sporting event surfaced on the Sports Hub — pre-joined with its
 * channel so the screen can render network/channel info without a
 * second lookup.
 */
data class SportsEvent(
    val program: EpgProgram,
    val channel: Channel?,
    val league: String,
    val homeTeam: String?,
    val awayTeam: String?,
    val score: GameScore? = null
) {
    val startEpochSec: Long get() = program.startEpochSec
    val endEpochSec: Long get() = program.endEpochSec
    val title: String get() = program.title ?: ""
    val isLive: Boolean
        get() {
            val now = System.currentTimeMillis() / 1000
            return startEpochSec <= now && now < endEpochSec
        }
    val isFinal: Boolean
        get() = (score?.status ?: "").lowercase().contains("final")
    val networkLabel: String?
        get() = channel?.guideName
    val channelNumber: String?
        get() = channel?.guideNumber ?: program.guideNumber
}

/** Sports Hub UI state. */
data class SportsCalendarUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedLeague: String = "all",
    /** Live games right now (in-progress). */
    val liveGames: List<SportsEvent> = emptyList(),
    /** Today: scheduled (start in future) + final results from earlier today. */
    val todayResults: List<SportsEvent> = emptyList(),
    /** Compact list of upcoming days — limited to next 3 calendar days. */
    val upcomingByDay: List<DaySection> = emptyList(),
    /** ESPN headlines for the selected league (or merged across the four when ALL). */
    val news: List<EspnArticle> = emptyList(),
    val schedules: List<RecordingSchedule> = emptyList(),
    val toastMessage: String? = null
)

data class DaySection(
    val key: String,
    val label: String,
    val date: LocalDate,
    val events: List<SportsEvent>
)

/** Hub league filter — restricted to the four scoreboard sports. */
val HUB_LEAGUES: List<Pair<String, String>> = listOf(
    "all" to "ALL",
    "nba" to "NBA",
    "nfl" to "NFL",
    "mlb" to "MLB",
    "nhl" to "NHL"
)

/** ESPN sport/league slugs for the news endpoint. */
private val ESPN_NEWS_PATH: Map<String, Pair<String, String>> = mapOf(
    "nba" to ("basketball" to "nba"),
    "nfl" to ("football" to "nfl"),
    "mlb" to ("baseball" to "mlb"),
    "nhl" to ("hockey" to "nhl")
)

class SportsCalendarViewModel : ViewModel() {

    private val api = ApiClient.api
    private val espnApi = ApiClient.espnApi

    private val _uiState = MutableStateFlow(SportsCalendarUiState())
    val uiState: StateFlow<SportsCalendarUiState> = _uiState.asStateFlow()

    // Cache of all sports events (unfiltered) so changing the league filter
    // doesn't refetch.
    private var allEvents: List<SportsEvent> = emptyList()
    private var liveScores: List<GameScore> = emptyList()
    private var scoresPollJob: Job? = null

    init { load() }

    override fun onCleared() {
        super.onCleared()
        scoresPollJob?.cancel()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val now = java.time.Instant.now()
                val start = now.toString()
                val end = now.plusSeconds(7L * 24 * 3600).toString()
                val resp = api.getGuide(start = start, end = end, hours = 168, limit = 50000)
                if (!resp.isSuccessful) {
                    Log.d("SPORTSHUB", "Guide fetch failed: ${resp.code()}")
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
                    // Hub only surfaces the four major leagues — everything else stays in the guide.
                    .filter { it.league in setOf("nba", "nfl", "mlb", "nhl") }
                    .sortedBy { it.startEpochSec }
                Log.d("SPORTSHUB", "Found ${sports.size} hub events out of ${programs.size} programs")
                allEvents = sports
                applyFilter(_uiState.value.selectedLeague)

                fetchSchedules()
                fetchScoresAndAttach()
                fetchNews(_uiState.value.selectedLeague)
                startScoresPollingIfNeeded()
            } catch (e: Exception) {
                Log.d("SPORTSHUB", "Exception: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Network error: ${e.message}")
            }
        }
    }

    private suspend fun fetchScoresAndAttach() {
        try {
            val resp = api.getSportsScoresToday()
            if (!resp.isSuccessful) return
            val body = resp.body() ?: return
            liveScores = body.nba + body.nfl + body.mlb + body.nhl
            attachScoresToEvents()
            // Win probabilities for live games — fire-and-forget, attached as they return.
            fetchWinProbabilitiesForLive()
        } catch (e: Exception) {
            Log.d("SPORTSHUB", "scores fetch failed: ${e.message}")
        }
    }

    private suspend fun fetchWinProbabilitiesForLive() {
        val live = allEvents.filter { it.isLive && it.score != null }
        if (live.isEmpty()) return
        val updates = mutableMapOf<String, GameScore>()
        for (ev in live) {
            val s = ev.score ?: continue
            val status = (s.status ?: "").lowercase()
            if (!status.contains("progress") && !status.contains("inprogress")) continue
            val home = s.homeTeam ?: continue
            val away = s.awayTeam ?: continue
            val lg = (s.league ?: ev.league).ifBlank { ev.league }
            try {
                val resp = api.getWinProbability(lg, home, away)
                if (resp.isSuccessful) {
                    val body = resp.body() ?: continue
                    updates[gameKey(s)] = s.copy(
                        homeWinProbability = body.homeWinProbability,
                        awayWinProbability = body.awayWinProbability
                    )
                }
            } catch (_: Exception) { /* skip on failure */ }
        }
        if (updates.isNotEmpty()) {
            allEvents = allEvents.map { ev ->
                val key = ev.score?.let { gameKey(it) }
                if (key != null && updates.containsKey(key)) ev.copy(score = updates[key]) else ev
            }
            applyFilter(_uiState.value.selectedLeague)
        }
    }

    private fun attachScoresToEvents() {
        if (liveScores.isEmpty()) return
        val byLeague = liveScores.groupBy { (it.league ?: "").lowercase() }
        allEvents = allEvents.map { ev -> ev.copy(score = matchScore(ev, byLeague)) }
        applyFilter(_uiState.value.selectedLeague)
    }

    private fun matchScore(event: SportsEvent, byLeague: Map<String, List<GameScore>>): GameScore? {
        val pool = byLeague[event.league] ?: return null
        if (pool.isEmpty()) return null
        // Channel-number first.
        event.channelNumber?.let { ch ->
            pool.firstOrNull { it.channel == ch }?.let { return it }
        }
        // Team-name match.
        val home = event.homeTeam?.lowercase() ?: ""
        val away = event.awayTeam?.lowercase() ?: ""
        if (home.isNotBlank() || away.isNotBlank()) {
            pool.firstOrNull { g ->
                val gh = (g.homeTeam ?: "").lowercase()
                val ga = (g.awayTeam ?: "").lowercase()
                (home.isNotBlank() && (gh.contains(home) || home.contains(gh))) ||
                (away.isNotBlank() && (ga.contains(away) || away.contains(ga)))
            }?.let { return it }
        }
        // Loose substring against title.
        val title = event.title.lowercase()
        return pool.firstOrNull { g ->
            val gh = (g.homeTeam ?: "").lowercase()
            val ga = (g.awayTeam ?: "").lowercase()
            (gh.isNotBlank() && title.contains(gh)) || (ga.isNotBlank() && title.contains(ga))
        }
    }

    private fun startScoresPollingIfNeeded() {
        scoresPollJob?.cancel()
        if (allEvents.none { it.isLive }) return
        scoresPollJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                fetchScoresAndAttach()
                if (allEvents.none { it.isLive }) break
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

    /**
     * Fetch ESPN headlines. When [league] is "all" we merge a few from each
     * of the four leagues (NBA/NFL/MLB/NHL); otherwise just one league.
     */
    private fun fetchNews(league: String) {
        viewModelScope.launch {
            val targets = when (league) {
                "all" -> ESPN_NEWS_PATH.keys.toList()
                else -> listOfNotNull(league.takeIf { ESPN_NEWS_PATH.containsKey(it) })
            }
            val collected = mutableListOf<EspnArticle>()
            for (lg in targets) {
                val (sport, leagueSlug) = ESPN_NEWS_PATH[lg] ?: continue
                try {
                    val resp = espnApi.getNews(sport, leagueSlug, limit = if (league == "all") 4 else 10)
                    if (resp.isSuccessful) {
                        resp.body()?.articles?.let { collected.addAll(it) }
                    }
                } catch (e: Exception) {
                    Log.d("SPORTSHUB", "ESPN news fetch failed for $lg: ${e.message}")
                }
            }
            _uiState.value = _uiState.value.copy(news = collected)
        }
    }

    fun setLeague(league: String) {
        if (_uiState.value.selectedLeague == league) return
        applyFilter(league)
        fetchNews(league)
    }

    private fun applyFilter(league: String) {
        val filtered = if (league == "all") allEvents else allEvents.filter { it.league == league }
        val sorted = filtered.sortedBy { it.startEpochSec }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val byDate = sorted.groupBy { ev ->
            java.time.Instant.ofEpochSecond(ev.startEpochSec).atZone(zone).toLocalDate()
        }

        // Live = currently playing (any league bucket).
        val live = sorted.filter { it.isLive }

        // Today's results = today's events that are NOT live (scheduled later or finished).
        val todays = (byDate[today] ?: emptyList()).filter { !it.isLive }

        // Upcoming: tomorrow → +3d (matches the 3-day spec)
        val sections = (1..3).mapNotNull { offset ->
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

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            selectedLeague = league,
            liveGames = live,
            todayResults = todays,
            upcomingByDay = sections
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
            delay(3000)
            _uiState.value = _uiState.value.copy(toastMessage = null)
        }
    }

    // ── Sports filtering — kept on companion for cross-screen reuse ─────

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

        fun parseTeams(title: String): Pair<String, String>? {
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
