package com.airdvr.tv.ui.screens

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.DaySection
import com.airdvr.tv.ui.viewmodels.SportsCalendarViewModel
import com.airdvr.tv.ui.viewmodels.SportsEvent
import com.airdvr.tv.util.TeamLogos
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── League configuration ────────────────────────────────────────────────

private data class LeagueOption(val id: String, val label: String)

private val leagueOptions = listOf(
    LeagueOption("all", "All"),
    LeagueOption("nfl", "NFL"),
    LeagueOption("nba", "NBA"),
    LeagueOption("mlb", "MLB"),
    LeagueOption("nhl", "NHL"),
    LeagueOption("ncaa", "NCAA"),
    LeagueOption("soccer", "Soccer"),
    LeagueOption("golf", "Golf"),
    LeagueOption("tennis", "Tennis"),
    LeagueOption("ufc", "UFC"),
    LeagueOption("sport", "Other"),
)

private val leagueColors = mapOf(
    "nfl" to Color(0xFF013369),
    "nba" to Color(0xFFC9082A),
    "mlb" to Color(0xFF002D72),
    "nhl" to Color(0xFF000000),
    "ncaa" to Color(0xFF004B8D),
    "soccer" to Color(0xFF00A651),
    "golf" to Color(0xFF00533E),
    "ufc" to Color(0xFFD20A0A),
    "nascar" to Color(0xFFFFB300),
    "tennis" to Color(0xFF1A6EBD),
    "wwe" to Color(0xFF6B0F1A),
    "sport" to Color(0xFF3B82F6),
)

// Team logo lookup is shared across screens — see com.airdvr.tv.util.TeamLogos.

private fun teamLogoUrl(league: String, teamName: String?): String? =
    TeamLogos.urlFor(league, teamName)

// ── Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsCalendarScreen(
    onBack: () -> Unit,
    viewModel: SportsCalendarViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var recordTarget by remember { mutableStateOf<SportsEvent?>(null) }
    var optionsTarget by remember { mutableStateOf<SportsEvent?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(PlexBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
                Text("Sports Calendar", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary)
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PlexTextPrimary, strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // 1. League filter row
                    item("league_filter") {
                        LeagueFilterRow(
                            selected = uiState.selectedLeague,
                            onSelect = { viewModel.setLeague(it) }
                        )
                    }

                    // 2. Today's games (only show if there are games or no filter results)
                    if (uiState.todaysGames.isNotEmpty()) {
                        item("today_header") {
                            SectionHeader("Today's Games")
                        }
                        item("today_row") {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.todaysGames, key = { ev -> "today-${ev.program.programId ?: ev.title}-${ev.startEpochSec}" }) { event ->
                                    GameCard(
                                        event = event,
                                        wide = true,
                                        onClick = { recordTarget = event },
                                        onLongPress = { optionsTarget = event }
                                    )
                                }
                            }
                        }
                    }

                    // 3. Day-by-day sections
                    uiState.upcomingByDay.forEach { day ->
                        item("day_${day.key}") {
                            DaySectionRow(
                                day = day,
                                isExpanded = uiState.expandedDayKeys.contains(day.key),
                                onToggle = { viewModel.toggleDayExpanded(day.key) },
                                onEventClick = { recordTarget = it },
                                onEventLongPress = { optionsTarget = it }
                            )
                        }
                    }

                    // Empty state
                    if (uiState.todaysGames.isEmpty() && uiState.upcomingByDay.isEmpty()) {
                        item("empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No upcoming sports on your channels",
                                    color = PlexTextTertiary,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // Toast
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

    // Record dialog (single click — Record Once)
    if (recordTarget != null) {
        val ev = recordTarget!!
        RecordEventDialog(
            event = ev,
            isAlreadyScheduled = viewModel.isScheduled(ev),
            onDismiss = { recordTarget = null },
            onRecordOnce = {
                viewModel.recordEvent(ev, "once")
                recordTarget = null
            },
            onRecordSeries = {
                viewModel.recordEvent(ev, "series")
                recordTarget = null
            }
        )
    }

    // Long-press options
    if (optionsTarget != null) {
        val ev = optionsTarget!!
        AlertDialog(
            onDismissRequest = { optionsTarget = null },
            containerColor = PlexCard,
            title = {
                androidx.compose.material3.Text(
                    ev.title,
                    color = PlexTextPrimary, fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!ev.networkLabel.isNullOrBlank() || !ev.channelNumber.isNullOrBlank()) {
                        androidx.compose.material3.Text(
                            "Ch ${ev.channelNumber ?: ""} ${ev.networkLabel ?: ""}".trim(),
                            color = PlexTextSecondary, fontSize = 13.sp
                        )
                    }
                    androidx.compose.material3.Text(
                        formatStartTime(ev.startEpochSec),
                        color = PlexTextSecondary, fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = {
                        viewModel.recordEvent(ev, "once")
                        optionsTarget = null
                    }) {
                        androidx.compose.material3.Text("Record Once", color = Color(0xFFEF4444))
                    }
                    TextButton(onClick = {
                        viewModel.recordEvent(ev, "series")
                        optionsTarget = null
                    }) {
                        androidx.compose.material3.Text("Record Series", color = Color(0xFFEF4444))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { optionsTarget = null }) {
                    androidx.compose.material3.Text("Cancel", color = PlexTextSecondary)
                }
            }
        )
    }
}

// ── League filter row ───────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LeagueFilterRow(selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(leagueOptions, key = { it.id }) { opt ->
            LeagueChip(
                label = opt.label,
                isSelected = selected == opt.id,
                onClick = { onSelect(opt.id) }
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
            modifier = Modifier.padding(horizontal = 18.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) Color.White else PlexTextSecondary
            )
        }
    }
}

// ── Section header ──────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 18.sp, fontWeight = FontWeight.Bold,
        color = PlexTextPrimary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

// ── Day section ─────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DaySectionRow(
    day: DaySection,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onEventClick: (SportsEvent) -> Unit,
    onEventLongPress: (SportsEvent) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header (focusable, click to expand/collapse)
        Surface(
            onClick = onToggle,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = PlexCard
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = PlexTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    day.label,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = PlexTextPrimary
                )
                Text(
                    "${day.events.size} game${if (day.events.size == 1) "" else "s"}",
                    fontSize = 12.sp, color = PlexTextTertiary
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(day.events, key = { ev -> "${day.key}-${ev.program.programId ?: ev.title}-${ev.startEpochSec}" }) { event ->
                    GameCard(
                        event = event,
                        wide = false,
                        onClick = { onEventClick(event) },
                        onLongPress = { onEventLongPress(event) }
                    )
                }
            }
        }
    }
}

// ── Game card ───────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GameCard(
    event: SportsEvent,
    wide: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val cardWidth = if (wide) 280.dp else 240.dp
    val cardHeight = if (wide) 160.dp else 150.dp
    var focused by remember { mutableStateOf(false) }
    val leagueColor = leagueColors[event.league] ?: PlexAccent

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU
                ) {
                    onLongPress(); true
                } else false
            },
        shape = CardDefaults.shape(shape = RoundedCornerShape(0.dp)),
        colors = CardDefaults.colors(
            containerColor = PlexCard,
            focusedContainerColor = PlexCard
        ),
        border = CardDefaults.border(
            border = Border(border = androidx.compose.foundation.BorderStroke(1.dp, PlexBorder)),
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, PlexTextPrimary))
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // League badge — top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(leagueColor, RoundedCornerShape(0.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    leagueLabel(event.league),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, letterSpacing = 0.4.sp
                )
            }

            // Live badge — top-right
            if (event.isLive) {
                LiveBadge(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            }

            // Center: matchup
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Prefer SportsDataIO-supplied team names when we have a matched live game.
                val sdAway = event.score?.awayTeam?.takeIf { it.isNotBlank() }
                val sdHome = event.score?.homeTeam?.takeIf { it.isNotBlank() }
                val displayAway = sdAway ?: event.awayTeam
                val displayHome = sdHome ?: event.homeTeam
                if (displayAway != null && displayHome != null) {
                    MatchupRow(
                        league = event.league,
                        away = displayAway,
                        home = displayHome,
                        wide = wide
                    )
                    val score = event.score
                    if (score != null && score.homeScore != null && score.awayScore != null) {
                        Text(
                            "${score.awayScore} - ${score.homeScore}",
                            fontSize = if (wide) 18.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PlexTextPrimary
                        )
                    }
                } else {
                    Text(
                        event.title,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = PlexTextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 3, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom-left: channel + network
            val chLabel = formatChannelLabel(event)
            if (chLabel.isNotBlank()) {
                Text(
                    chLabel,
                    fontSize = 11.sp, color = PlexTextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                )
            }

            // Bottom-right: start time, or quarter/clock for in-progress games.
            val score = event.score
            val rightLabel = when {
                event.isLive && score != null -> {
                    val q = score.quarter?.takeIf { it.isNotBlank() }
                    val tr = score.timeRemaining?.takeIf { it.isNotBlank() }
                    when {
                        q != null && tr != null -> "$q · $tr"
                        q != null -> q
                        tr != null -> tr
                        else -> null
                    }
                }
                !event.isLive -> formatStartTime(event.startEpochSec)
                else -> null
            }
            if (rightLabel != null) {
                Text(
                    rightLabel,
                    fontSize = 11.sp, color = PlexTextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                )
            }

            // Focus overlay — "Record" hint
            if (focused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 26.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(0.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "Press OK to Record",
                        fontSize = 10.sp, color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchupRow(league: String, away: String, home: String, wide: Boolean) {
    val logoSize = if (wide) 44.dp else 38.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (wide) 14.dp else 10.dp)
    ) {
        TeamLogoOrName(league = league, team = away, logoSize = logoSize)
        Text(
            "vs",
            fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = PlexTextTertiary
        )
        TeamLogoOrName(league = league, team = home, logoSize = logoSize)
    }
}

@Composable
private fun TeamLogoOrName(league: String, team: String, logoSize: androidx.compose.ui.unit.Dp) {
    val url = teamLogoUrl(league, team)
    var imageFailed by remember(url) { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.width(logoSize + 16.dp)
    ) {
        if (url != null && !imageFailed) {
            AsyncImage(
                model = url,
                contentDescription = team,
                modifier = Modifier.size(logoSize),
                contentScale = ContentScale.Fit,
                onError = { imageFailed = true }
            )
        } else {
            Box(
                modifier = Modifier
                    .size(logoSize)
                    .clip(CircleShape)
                    .background(PlexSurface)
                    .border(1.dp, PlexBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    abbreviateTeamName(team),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = PlexTextPrimary
                )
            }
        }
        Text(
            team,
            fontSize = 9.sp, color = PlexTextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
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
            .background(Color(0xFFEF4444), RoundedCornerShape(0.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = pulse))
        )
        Text(
            "LIVE",
            fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = Color.White, letterSpacing = 0.5.sp
        )
    }
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
                    formatStartTime(event.startEpochSec),
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
                    androidx.compose.material3.Text("Record Once", color = Color(0xFFEF4444))
                }
                TextButton(onClick = onRecordSeries) {
                    androidx.compose.material3.Text("Record Series", color = Color(0xFFEF4444))
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

private fun leagueLabel(league: String): String = when (league) {
    "nfl" -> "NFL"
    "nba" -> "NBA"
    "mlb" -> "MLB"
    "nhl" -> "NHL"
    "ncaa" -> "NCAA"
    "soccer" -> "SOCCER"
    "golf" -> "GOLF"
    "ufc" -> "UFC"
    "nascar" -> "NASCAR"
    "tennis" -> "TENNIS"
    "wwe" -> "WWE"
    else -> "SPORT"
}

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
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochSec * 1000))
}

private fun abbreviateTeamName(name: String): String {
    val cleaned = name.trim()
    val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words[0].take(3).uppercase()
        else -> words.takeLast(1).first().take(3).uppercase()
    }
}
