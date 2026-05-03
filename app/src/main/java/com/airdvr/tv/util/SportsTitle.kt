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
