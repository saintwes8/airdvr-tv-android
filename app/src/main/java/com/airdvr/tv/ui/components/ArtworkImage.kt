package com.airdvr.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.airdvr.tv.data.repository.ArtworkRepository
import com.airdvr.tv.util.TeamLogos
import com.airdvr.tv.util.detectLeagueFromTitle
import com.airdvr.tv.util.parseMatchupFromText
import com.airdvr.tv.util.shouldSkipTmdbForSports
import kotlinx.coroutines.delay

/**
 * Async fetch of poster URL for a title with 500ms debounce. Returns null
 * until loaded; null also means "not available". Cached across calls so
 * re-renders are instant. Returns null for generic sports titles ("NBA
 * Basketball" etc.) where TMDB matches the wrong art — callers should
 * render [SportsTitleArtwork] instead.
 */
@Composable
fun rememberPosterUrl(title: String?): String? {
    if (title.isNullOrBlank()) return null
    if (shouldSkipTmdbForSports(title)) return null
    var url by remember(title) {
        mutableStateOf(ArtworkRepository.cachedPoster(title)?.ifBlank { null })
    }
    LaunchedEffect(title) {
        if (ArtworkRepository.cachedPoster(title) == null) {
            delay(500L)
            url = ArtworkRepository.fetchPoster(title)
        }
    }
    return url
}

/** Async fetch of backdrop URL for a title with 500ms debounce. */
@Composable
fun rememberBackdropUrl(title: String?): String? {
    if (title.isNullOrBlank()) return null
    if (shouldSkipTmdbForSports(title)) return null
    var url by remember(title) {
        mutableStateOf(ArtworkRepository.cachedBackdrop(title)?.ifBlank { null })
    }
    LaunchedEffect(title) {
        if (ArtworkRepository.cachedBackdrop(title) == null) {
            delay(500L)
            url = ArtworkRepository.fetchBackdrop(title)
        }
    }
    return url
}

/**
 * Brand color used as the accent for a major network's text artwork.
 */
private fun networkAccent(name: String?): Color = when ((name ?: "").uppercase()) {
    "FOX" -> Color(0xFF003478)
    "CBS" -> Color(0xFF033A85)
    "ABC" -> Color(0xFF000000)
    "NBC" -> Color(0xFF6460AA)
    "ESPN" -> Color(0xFFD00000)
    "TNT" -> Color(0xFFE30613)
    "TBS" -> Color(0xFF1B6BC0)
    "CNN" -> Color(0xFFCC0000)
    "FOX NEWS", "FOXNEWS" -> Color(0xFF003478)
    "MSNBC" -> Color(0xFF1A4080)
    "BLOOMBERG" -> Color(0xFF000000)
    "HBO" -> Color(0xFF000000)
    "USA" -> Color(0xFF1B4D9A)
    "TLC" -> Color(0xFF982C61)
    "DISCOVERY" -> Color(0xFF193160)
    "HISTORY" -> Color(0xFF000000)
    else -> Color(0xFF1A1A1A)
}

/**
 * Tile shown for non-sports channels that have no program artwork
 * (news / talk / generic). Uses the channel logo when available, otherwise
 * the network name on a brand-colored gradient — never a blank tile.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NetworkArtwork(
    network: String?,
    logoUrl: String?,
    fallbackLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val brandColor = networkAccent(network ?: fallbackLabel)
    val gradient = Brush.linearGradient(
        colors = listOf(brandColor, Color(0xFF050505))
    )
    Box(
        modifier = modifier.background(gradient),
        contentAlignment = Alignment.Center
    ) {
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = network,
                modifier = Modifier.fillMaxSize(0.55f),
                contentScale = ContentScale.Fit
            )
        } else {
            val label = (network?.takeIf { it.isNotBlank() } ?: fallbackLabel ?: "").uppercase()
            Text(
                label.ifBlank { "TV" },
                fontSize = 30.sp, fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * League brand gradients used for sport-themed artwork tiles.
 * Returns a fallback dark gradient for unknown leagues.
 */
private fun leagueGradient(league: String?): Brush = when ((league ?: "").lowercase()) {
    "nba" -> Brush.linearGradient(listOf(Color(0xFF17408B), Color(0xFFC9082A)))
    "nfl" -> Brush.linearGradient(listOf(Color(0xFF013369), Color(0xFF000F1F)))
    "mlb" -> Brush.linearGradient(listOf(Color(0xFF002D72), Color(0xFFCC0000)))
    "nhl" -> Brush.linearGradient(listOf(Color(0xFFCCE5FF), Color(0xFF003366)))
    else -> Brush.linearGradient(listOf(Color(0xFF0E1622), Color(0xFF000000)))
}

private fun leaguePillBg(league: String?): Color = when ((league ?: "").lowercase()) {
    "nba" -> Color(0xFFC9082A)
    "nfl" -> Color(0xFF013369)
    "mlb" -> Color(0xFF002D72)
    "nhl" -> Color(0xFF111111)
    else -> Color(0xFF222222)
}

/**
 * Artwork tile for a generic sports title (e.g. "NBA Basketball"). Renders
 * a sport-themed gradient background. When [description] contains a parsable
 * matchup ("Philadelphia 76ers at Boston Celtics"), the tile shows both team
 * logos with "vs" in the center and a league pill in the corner.
 *
 * Falls back to the league logo, then to text on the gradient.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsTitleArtwork(
    title: String?,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val league = detectLeagueFromTitle(title)
    val gradient = leagueGradient(league)
    val matchup = remember(league, description) { parseMatchupFromText(league, description) }
    val awayTeam = matchup?.first
    val homeTeam = matchup?.second
    val leagueKey = (league ?: "").lowercase()
    val awayLogo = awayTeam?.let { TeamLogos.urlFor(leagueKey, it) }
    val homeLogo = homeTeam?.let { TeamLogos.urlFor(leagueKey, it) }
    val leagueLogo = league?.let { TeamLogos.leagueUrl(it) }

    Box(
        modifier = modifier.background(gradient)
    ) {
        when {
            awayLogo != null && homeLogo != null -> {
                Row(
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AsyncImage(
                        model = awayLogo,
                        contentDescription = awayTeam,
                        modifier = Modifier.size(32.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        "vs",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    AsyncImage(
                        model = homeLogo,
                        contentDescription = homeTeam,
                        modifier = Modifier.size(32.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            leagueLogo != null -> {
                AsyncImage(
                    model = leagueLogo,
                    contentDescription = title,
                    modifier = Modifier.align(Alignment.Center).fillMaxSize(0.5f),
                    contentScale = ContentScale.Fit
                )
            }
            else -> {
                Text(
                    (league ?: title?.take(3) ?: "").uppercase(),
                    fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        if (!league.isNullOrBlank()) {
            Text(
                league.uppercase(),
                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(leaguePillBg(league))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}
