package com.airdvr.tv.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.airdvr.tv.data.models.EspnArticle
import com.airdvr.tv.data.models.GameScore
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.DaySection
import com.airdvr.tv.ui.viewmodels.HUB_LEAGUES
import com.airdvr.tv.ui.viewmodels.SportsCalendarViewModel
import com.airdvr.tv.ui.viewmodels.SportsEvent
import com.airdvr.tv.util.TeamLogos
import com.airdvr.tv.util.formatGameTimeLocal
import com.airdvr.tv.util.parseIsoToEpochSec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ── League gradients (per spec) ─────────────────────────────────────────

private fun leagueGradient(league: String, dim: Boolean = false): Brush {
    val (start, end) = when (league.lowercase()) {
        "nba" -> Color(0xFF17408B) to Color(0xFFC9082A)
        "nfl" -> Color(0xFF013369) to Color(0xFF003F7F)
        "mlb" -> Color(0xFF002D72) to Color(0xFFCC0000)
        "nhl" -> Color(0xFF99C5E0) to Color(0xFF003366)
        else -> Color(0xFF1A1A1A) to Color(0xFF050505)
    }
    return if (dim) {
        Brush.linearGradient(listOf(start.copy(alpha = 0.6f), end.copy(alpha = 0.6f)))
    } else {
        Brush.linearGradient(listOf(start, end))
    }
}

// ── Sports Hub Screen ────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsCalendarScreen(
    onBack: () -> Unit,
    viewModel: SportsCalendarViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var recordTarget by remember { mutableStateOf<SportsEvent?>(null) }
    var newsExpanded by remember { mutableStateOf<EspnArticle?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(PlexBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = onBack,
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = PlexCard
                    )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        tint = PlexTextPrimary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Text(
                    "Sports Hub",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PlexTextPrimary, strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 4.dp)
                ) {
                    item("league_filter") {
                        LeagueFilterRow(
                            selected = uiState.selectedLeague,
                            onSelect = { viewModel.setLeague(it) }
                        )
                    }

                    if (uiState.liveGames.isNotEmpty()) {
                        item("live_header") { SectionHeader("LIVE NOW", accent = LiveRedDot) }
                        items(
                            uiState.liveGames,
                            key = { ev -> "live-${ev.program.programId ?: ev.title}-${ev.startEpochSec}" }
                        ) { event ->
                            LiveGameCard(
                                event = event,
                                onClick = { recordTarget = event }
                            )
                        }
                    }

                    if (uiState.todayResults.isNotEmpty()) {
                        item("today_header") { SectionHeader("TODAY") }
                        items(
                            uiState.todayResults,
                            key = { ev -> "today-${ev.program.programId ?: ev.title}-${ev.startEpochSec}" }
                        ) { event ->
                            TodayGameCard(
                                event = event,
                                onClick = { recordTarget = event }
                            )
                        }
                    }

                    if (uiState.upcomingByDay.isNotEmpty()) {
                        item("upcoming_header") { SectionHeader("UPCOMING") }
                        uiState.upcomingByDay.forEach { day ->
                            item("up_day_${day.key}") {
                                Text(
                                    day.label,
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    color = PlexTextSecondary, letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                                )
                            }
                            items(
                                day.events,
                                key = { ev -> "up-${day.key}-${ev.program.programId ?: ev.title}-${ev.startEpochSec}" }
                            ) { event ->
                                UpcomingGameRow(
                                    event = event,
                                    onClick = { recordTarget = event }
                                )
                            }
                        }
                    }

                    if (uiState.news.isNotEmpty()) {
                        item("news_header") { SectionHeader("SPORTS NEWS") }
                        item("news_row") {
                            NewsRow(
                                articles = uiState.news,
                                onSelect = { newsExpanded = it }
                            )
                        }
                    }

                    if (uiState.liveGames.isEmpty() && uiState.todayResults.isEmpty() &&
                        uiState.upcomingByDay.isEmpty() && uiState.news.isEmpty()) {
                        item("empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No games on your channels",
                                    color = PlexTextTertiary, fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        uiState.toastMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .background(PlexCard, RoundedCornerShape(0.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(msg, fontSize = 14.sp, color = PlexTextPrimary)
            }
        }
    }

    if (recordTarget != null) {
        val ev = recordTarget!!
        RecordEventDialog(
            event = ev,
            isAlreadyScheduled = viewModel.isScheduled(ev),
            onDismiss = { recordTarget = null },
            onRecordOnce = { viewModel.recordEvent(ev, "once"); recordTarget = null },
            onRecordSeries = { viewModel.recordEvent(ev, "series"); recordTarget = null }
        )
    }

    if (newsExpanded != null) {
        val article = newsExpanded!!
        NewsArticleDialog(article = article, onDismiss = { newsExpanded = null })
    }
}

// ── League filter ────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LeagueFilterRow(selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(HUB_LEAGUES, key = { it.first }) { (id, label) ->
            LeagueChip(
                label = label,
                isSelected = selected == id,
                onClick = { onSelect(id) }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LeagueChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) PlexAccent else PlexCard,
            focusedContainerColor = if (isSelected) PlexAccent else PlexSurface
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(
                1.dp, if (isSelected) PlexAccent else PlexBorder
            )),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        ),
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 22.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else PlexTextSecondary
            )
        }
    }
}

// ── Section header ──────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(title: String, accent: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (accent != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        }
        Text(
            title,
            fontSize = 16.sp, fontWeight = FontWeight.Bold,
            color = PlexTextPrimary, letterSpacing = 1.sp
        )
    }
}

// ── LIVE NOW card ───────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LiveGameCard(event: SportsEvent, onClick: () -> Unit) {
    val score = event.score
    val league = event.league
    val awayTeam = score?.awayTeam ?: event.awayTeam
    val homeTeam = score?.homeTeam ?: event.homeTeam
    val awayLogo = TeamLogos.urlFor(league, awayTeam)
    val homeLogo = TeamLogos.urlFor(league, homeTeam)
    val awayAbbr = TeamLogos.abbrev(league, awayTeam).ifBlank { (awayTeam ?: "AWAY").take(3).uppercase() }
    val homeAbbr = TeamLogos.abbrev(league, homeTeam).ifBlank { (homeTeam ?: "HOME").take(3).uppercase() }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .focusable(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().background(leagueGradient(league))) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                // LIVE pulsing badge top-right
                LiveBadge(modifier = Modifier.align(Alignment.TopEnd))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TeamCell(
                        logoUrl = awayLogo,
                        abbreviation = awayAbbr,
                        score = score?.awayScore,
                        align = Alignment.Start
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (score?.awayScore != null && score.homeScore != null) {
                            Text(
                                "${score.awayScore} - ${score.homeScore}",
                                fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Text(
                                "vs",
                                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        val statusLine = liveStatusLine(score)
                        if (statusLine.isNotBlank()) {
                            Text(
                                statusLine,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                    TeamCell(
                        logoUrl = homeLogo,
                        abbreviation = homeAbbr,
                        score = score?.homeScore,
                        align = Alignment.End
                    )
                }
            }

            // Win-probability bar (only when InProgress + data available)
            if (score?.homeWinProbability != null && score.awayWinProbability != null) {
                WinProbabilityBar(
                    awayAbbr = awayAbbr,
                    homeAbbr = homeAbbr,
                    awayProb = score.awayWinProbability,
                    homeProb = score.homeWinProbability,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Watch-now strip
            val chNum = event.channelNumber
            val network = event.networkLabel
            if (!chNum.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "● WATCH",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LiveRedDot
                    )
                    Text(
                        "Ch $chNum${if (!network.isNullOrBlank()) " · $network" else ""}",
                        fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamCell(
    logoUrl: String?,
    abbreviation: String,
    score: Int?,
    align: Alignment.Horizontal
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(96.dp)
    ) {
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = abbreviation,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    abbreviation, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
            }
        }
        Text(
            abbreviation,
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White
        )
    }
}

@Composable
private fun WinProbabilityBar(
    awayAbbr: String,
    homeAbbr: String,
    awayProb: Float,
    homeProb: Float,
    modifier: Modifier = Modifier
) {
    val a = awayProb.coerceIn(0f, 1f)
    val h = homeProb.coerceIn(0f, 1f)
    val total = (a + h).takeIf { it > 0f } ?: return
    val aPct = a / total
    val hPct = h / total
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "$awayAbbr ${(aPct * 100).toInt()}%",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
            Text(
                "${(hPct * 100).toInt()}% $homeAbbr",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .weight(aPct.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(Color(0xFFE63946))
            )
            Box(
                modifier = Modifier
                    .weight(hPct.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "live-pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "lp"
    )
    Row(
        modifier = modifier
            .background(LiveRedDot, RoundedCornerShape(0.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = pulse)))
        Text(
            "LIVE",
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.5.sp
        )
    }
}

private fun liveStatusLine(score: GameScore?): String {
    if (score == null) return ""
    val q = score.quarter?.takeIf { it.isNotBlank() }
    val tr = score.timeRemaining?.takeIf { it.isNotBlank() }
    return when {
        q != null && tr != null -> "$q $tr"
        q != null -> q
        tr != null -> tr
        else -> ""
    }
}

// ── TODAY card (scheduled or final result) ──────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TodayGameCard(event: SportsEvent, onClick: () -> Unit) {
    val score = event.score
    val league = event.league
    val awayTeam = score?.awayTeam ?: event.awayTeam
    val homeTeam = score?.homeTeam ?: event.homeTeam
    val awayLogo = TeamLogos.urlFor(league, awayTeam)
    val homeLogo = TeamLogos.urlFor(league, homeTeam)
    val awayAbbr = TeamLogos.abbrev(league, awayTeam).ifBlank { (awayTeam ?: "AWAY").take(3).uppercase() }
    val homeAbbr = TeamLogos.abbrev(league, homeTeam).ifBlank { (homeTeam ?: "HOME").take(3).uppercase() }
    val isFinal = event.isFinal
    val homePts = score?.homeScore
    val awayPts = score?.awayScore
    val awayWon = isFinal && homePts != null && awayPts != null && awayPts > homePts
    val homeWon = isFinal && homePts != null && awayPts != null && homePts > awayPts

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .focusable(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(leagueGradient(league, dim = true))
                .padding(14.dp)
        ) {
            // Status badge top-right
            if (isFinal) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(0.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("FINAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.5.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TeamCellWithName(
                    logoUrl = awayLogo,
                    fullName = awayTeam,
                    abbreviation = awayAbbr,
                    dim = isFinal && !awayWon
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (isFinal && awayPts != null && homePts != null) {
                        Text(
                            "$awayPts - $homePts",
                            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White
                        )
                    } else {
                        val timeStr = formatGameTimeLocal(event.program.startTime).ifBlank {
                            formatStartTime(event.startEpochSec)
                        }
                        Text(
                            timeStr,
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
                        )
                        val ch = event.channelNumber
                        val net = event.networkLabel
                        val chLabel = listOfNotNull(net, ch?.let { "Ch $it" }).joinToString(" · ")
                        if (chLabel.isNotBlank()) {
                            Text(
                                chLabel,
                                fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                    score?.seriesInfo?.let { s ->
                        val hw = s.homeWins ?: 0
                        val aw = s.awayWins ?: 0
                        val winnerAbbr = when {
                            isFinal && awayWon -> awayAbbr
                            isFinal && homeWon -> homeAbbr
                            else -> null
                        }
                        val seriesText = if (winnerAbbr != null && (hw + aw) > 0) {
                            val high = maxOf(hw, aw)
                            val low = minOf(hw, aw)
                            "$winnerAbbr wins series $high-$low"
                        } else null
                        if (seriesText != null) {
                            Text(
                                seriesText,
                                fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                TeamCellWithName(
                    logoUrl = homeLogo,
                    fullName = homeTeam,
                    abbreviation = homeAbbr,
                    dim = isFinal && !homeWon
                )
            }
        }
    }
}

@Composable
private fun TeamCellWithName(
    logoUrl: String?,
    fullName: String?,
    abbreviation: String,
    dim: Boolean
) {
    val alpha = if (dim) 0.45f else 1f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(110.dp)
    ) {
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = fullName ?: abbreviation,
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit,
                alpha = alpha
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f * alpha)),
                contentAlignment = Alignment.Center
            ) {
                Text(abbreviation, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = alpha))
            }
        }
        Text(
            fullName ?: abbreviation,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = alpha),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

// ── UPCOMING compact row ────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UpcomingGameRow(event: SportsEvent, onClick: () -> Unit) {
    val league = event.league
    val awayAbbr = TeamLogos.abbrev(league, event.awayTeam).ifBlank { (event.awayTeam ?: "AWAY").take(3).uppercase() }
    val homeAbbr = TeamLogos.abbrev(league, event.homeTeam).ifBlank { (event.homeTeam ?: "HOME").take(3).uppercase() }
    val timeStr = formatGameTimeLocal(event.program.startTime).ifBlank { formatStartTime(event.startEpochSec) }
    val net = event.networkLabel
    val ch = event.channelNumber
    val tail = listOfNotNull(timeStr.takeIf { it.isNotBlank() }, net).joinToString(" · ")

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .focusable(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PlexCard,
            focusedContainerColor = PlexSurface
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 18.dp)
                    .background(leagueGradient(league)),
                contentAlignment = Alignment.Center
            ) {
                Text(league.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(
                "$awayAbbr @ $homeAbbr",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (tail.isNotBlank()) {
                Text(
                    tail,
                    fontSize = 12.sp, color = PlexTextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (!ch.isNullOrBlank()) {
                Text(
                    "Ch $ch",
                    fontSize = 11.sp, color = PlexTextTertiary, fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── SPORTS NEWS (horizontal) ────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NewsRow(articles: List<EspnArticle>, onSelect: (EspnArticle) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(articles) { _, article ->
            NewsCard(article = article, onClick = { onSelect(article) })
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NewsCard(article: EspnArticle, onClick: () -> Unit) {
    val imageUrl = article.images.firstOrNull { !it.url.isNullOrBlank() }?.url
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(260.dp)
            .height(200.dp)
            .focusable(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PlexCard,
            focusedContainerColor = PlexCard
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(PlexSurface),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = article.headline,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("ESPN", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PlexTextTertiary)
                }
            }
            Text(
                article.headline ?: "",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PlexTextPrimary,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun NewsArticleDialog(article: EspnArticle, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PlexCard,
        title = {
            androidx.compose.material3.Text(
                article.headline ?: "ESPN",
                color = PlexTextPrimary, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!article.byline.isNullOrBlank()) {
                    androidx.compose.material3.Text(
                        article.byline, fontSize = 12.sp, color = PlexTextTertiary
                    )
                }
                if (!article.description.isNullOrBlank()) {
                    androidx.compose.material3.Text(
                        article.description, fontSize = 14.sp, color = PlexTextSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("Close", color = PlexTextPrimary)
            }
        }
    )
}

// ── Record dialog ───────────────────────────────────────────────────────

@Composable
private fun RecordEventDialog(
    event: SportsEvent,
    isAlreadyScheduled: Boolean,
    onDismiss: () -> Unit,
    onRecordOnce: () -> Unit,
    onRecordSeries: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PlexCard,
        title = {
            androidx.compose.material3.Text(
                event.title,
                color = PlexTextPrimary, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val ch = formatChannelLabel(event)
                if (ch.isNotBlank()) {
                    androidx.compose.material3.Text(
                        ch, color = PlexTextSecondary, fontSize = 14.sp
                    )
                }
                androidx.compose.material3.Text(
                    formatGameTimeLocal(event.program.startTime).ifBlank { formatStartTime(event.startEpochSec) },
                    color = PlexTextSecondary, fontSize = 14.sp
                )
                if (isAlreadyScheduled) {
                    androidx.compose.material3.Text(
                        "Already scheduled",
                        color = Color(0xFFF59E0B), fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onRecordOnce) {
                    androidx.compose.material3.Text("Record Once", color = LiveRedDot)
                }
                TextButton(onClick = onRecordSeries) {
                    androidx.compose.material3.Text("Record Series", color = LiveRedDot)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("Cancel", color = PlexTextSecondary)
            }
        }
    )
}

// ── Helpers ─────────────────────────────────────────────────────────────

private fun formatChannelLabel(event: SportsEvent): String {
    val number = event.channelNumber
    val network = event.networkLabel
    return when {
        !number.isNullOrBlank() && !network.isNullOrBlank() -> "Ch $number $network"
        !number.isNullOrBlank() -> "Ch $number"
        !network.isNullOrBlank() -> network
        else -> ""
    }
}

private fun formatStartTime(epochSec: Long): String {
    if (epochSec <= 0L) return ""
    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }
    return fmt.format(Date(epochSec * 1000))
}
