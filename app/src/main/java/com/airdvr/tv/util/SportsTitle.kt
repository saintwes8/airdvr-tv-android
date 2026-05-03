package com.airdvr.tv.util

/**
 * True when the program title is a generic league name (e.g. "NBA Basketball")
 * for which TMDB returns unrelated artwork (e.g. "The Queen of Basketball").
 * Callers should fall back to league/team art instead of TMDB poster/backdrop.
 */
fun shouldSkipTmdbForSports(title: String?): Boolean {
    if (title.isNullOrBlank()) return false
    val t = title.lowercase()
    return t.contains("nba basketball") ||
        t.contains("nfl football") ||
        t.contains("mlb baseball") ||
        t.contains("nhl hockey") ||
        t.contains("ncaa football") ||
        t.contains("ncaa basketball") ||
        t.contains("college football") ||
        t.contains("college basketball") ||
        t.contains("premier league") ||
        t.contains("mls soccer") ||
        t.contains("pga golf") ||
        t.contains("wnba")
}

/** Detect a likely league code (nba/nfl/mlb/nhl) from a generic sports title. */
fun detectLeagueFromTitle(title: String?): String? {
    if (title.isNullOrBlank()) return null
    val t = title.lowercase()
    return when {
        t.contains("nba") || t.contains("wnba") -> "nba"
        t.contains("nfl") || t.contains("football") && !t.contains("soccer") -> "nfl"
        t.contains("mlb") || t.contains("baseball") -> "mlb"
        t.contains("nhl") || t.contains("hockey") -> "nhl"
        else -> null
    }
}

/**
 * Parse an EPG description for an away/home team matchup.
 * Looks for patterns like "Philadelphia 76ers at Boston Celtics" or
 * "Boston Celtics vs Philadelphia 76ers" near the start of the text and
 * matches each side against the team table for the given league.
 *
 * Returns (awayTeamFullName, homeTeamFullName) or null if no match.
 */
fun parseMatchupFromText(league: String?, text: String?): Pair<String, String>? {
    if (text.isNullOrBlank()) return null
    val firstSentence = text.split(Regex("[.!?]")).firstOrNull()?.trim() ?: return null
    if (firstSentence.length > 200) return null
    val separators = listOf(" at ", " @ ", " vs. ", " vs ", " v. ", " versus ")
    val sep = separators.firstOrNull { firstSentence.contains(it, ignoreCase = true) }
        ?: return null
    val idx = firstSentence.indexOf(sep, ignoreCase = true)
    val left = firstSentence.substring(0, idx).trim().trim(',').trim()
    val rightRaw = firstSentence.substring(idx + sep.length).trim().trim(',').trim()
    val right = rightRaw.split(Regex(",|;| - | — ")).firstOrNull()?.trim() ?: rightRaw
    if (left.isBlank() || right.isBlank()) return null
    val leagueKey = (league ?: "").lowercase()
    if (!TeamLogos.hasTeam(leagueKey, left) && !TeamLogos.hasTeam(leagueKey, right)) return null
    val isAtSeparator = sep.trim().equals("at", ignoreCase = true) || sep.trim().equals("@", ignoreCase = true)
    return if (isAtSeparator) left to right else left to right
}
