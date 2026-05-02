package com.airdvr.tv.util

/**
 * ESPN-CDN team logo lookup. Names are matched case-insensitively against
 * full team names with substring fallback so partial / informal names
 * (e.g. "Knicks", "Lakers") still resolve to a logo.
 */
object TeamLogos {

    private val nfl = mapOf(
        "arizona cardinals" to "ari", "atlanta falcons" to "atl", "baltimore ravens" to "bal",
        "buffalo bills" to "buf", "carolina panthers" to "car", "chicago bears" to "chi",
        "cincinnati bengals" to "cin", "cleveland browns" to "cle", "dallas cowboys" to "dal",
        "denver broncos" to "den", "detroit lions" to "det", "green bay packers" to "gb",
        "houston texans" to "hou", "indianapolis colts" to "ind", "jacksonville jaguars" to "jax",
        "kansas city chiefs" to "kc", "las vegas raiders" to "lv", "los angeles chargers" to "lac",
        "los angeles rams" to "lar", "miami dolphins" to "mia", "minnesota vikings" to "min",
        "new england patriots" to "ne", "new orleans saints" to "no", "new york giants" to "nyg",
        "new york jets" to "nyj", "philadelphia eagles" to "phi", "pittsburgh steelers" to "pit",
        "san francisco 49ers" to "sf", "seattle seahawks" to "sea", "tampa bay buccaneers" to "tb",
        "tennessee titans" to "ten", "washington commanders" to "wsh"
    )

    private val nba = mapOf(
        "atlanta hawks" to "atl", "boston celtics" to "bos", "brooklyn nets" to "bkn",
        "charlotte hornets" to "cha", "chicago bulls" to "chi", "cleveland cavaliers" to "cle",
        "dallas mavericks" to "dal", "denver nuggets" to "den", "detroit pistons" to "det",
        "golden state warriors" to "gs", "houston rockets" to "hou", "indiana pacers" to "ind",
        "los angeles clippers" to "lac", "la clippers" to "lac", "los angeles lakers" to "lal",
        "la lakers" to "lal", "memphis grizzlies" to "mem", "miami heat" to "mia",
        "milwaukee bucks" to "mil", "minnesota timberwolves" to "min", "new orleans pelicans" to "no",
        "new york knicks" to "ny", "oklahoma city thunder" to "okc", "orlando magic" to "orl",
        "philadelphia 76ers" to "phi", "phoenix suns" to "phx", "portland trail blazers" to "por",
        "sacramento kings" to "sac", "san antonio spurs" to "sa", "toronto raptors" to "tor",
        "utah jazz" to "utah", "washington wizards" to "wsh"
    )

    private val mlb = mapOf(
        "arizona diamondbacks" to "ari", "atlanta braves" to "atl", "baltimore orioles" to "bal",
        "boston red sox" to "bos", "chicago cubs" to "chc", "chicago white sox" to "chw",
        "cincinnati reds" to "cin", "cleveland guardians" to "cle", "colorado rockies" to "col",
        "detroit tigers" to "det", "houston astros" to "hou", "kansas city royals" to "kc",
        "los angeles angels" to "laa", "los angeles dodgers" to "lad", "miami marlins" to "mia",
        "milwaukee brewers" to "mil", "minnesota twins" to "min", "new york mets" to "nym",
        "new york yankees" to "nyy", "oakland athletics" to "oak", "philadelphia phillies" to "phi",
        "pittsburgh pirates" to "pit", "san diego padres" to "sd", "san francisco giants" to "sf",
        "seattle mariners" to "sea", "st. louis cardinals" to "stl", "st louis cardinals" to "stl",
        "tampa bay rays" to "tb", "texas rangers" to "tex", "toronto blue jays" to "tor",
        "washington nationals" to "wsh"
    )

    private val nhl = mapOf(
        "anaheim ducks" to "ana", "arizona coyotes" to "ari", "boston bruins" to "bos",
        "buffalo sabres" to "buf", "calgary flames" to "cgy", "carolina hurricanes" to "car",
        "chicago blackhawks" to "chi", "colorado avalanche" to "col", "columbus blue jackets" to "cbj",
        "dallas stars" to "dal", "detroit red wings" to "det", "edmonton oilers" to "edm",
        "florida panthers" to "fla", "los angeles kings" to "la", "minnesota wild" to "min",
        "montreal canadiens" to "mtl", "nashville predators" to "nsh", "new jersey devils" to "nj",
        "new york islanders" to "nyi", "new york rangers" to "nyr", "ottawa senators" to "ott",
        "philadelphia flyers" to "phi", "pittsburgh penguins" to "pit", "san jose sharks" to "sj",
        "seattle kraken" to "sea", "st. louis blues" to "stl", "st louis blues" to "stl",
        "tampa bay lightning" to "tb", "toronto maple leafs" to "tor", "utah hockey club" to "utah",
        "vancouver canucks" to "van", "vegas golden knights" to "vgk", "washington capitals" to "wsh",
        "winnipeg jets" to "wpg"
    )

    fun urlFor(league: String, teamName: String?): String? {
        if (teamName.isNullOrBlank()) return null
        val key = teamName.trim().lowercase()
        val table = when (league) {
            "nfl" -> nfl
            "nba" -> nba
            "mlb" -> mlb
            "nhl" -> nhl
            else -> return null
        }
        val abbrev = table[key] ?: table.entries.firstOrNull { key.contains(it.key) || it.key.contains(key) }?.value
        ?: return null
        return "https://a.espncdn.com/i/teamlogos/$league/500/$abbrev.png"
    }

    /** ESPN league-logo URL (e.g. "nba" → nba.png). */
    fun leagueUrl(league: String): String? = when (league) {
        "nfl", "nba", "mlb", "nhl" -> "https://a.espncdn.com/i/teamlogos/leagues/500/$league.png"
        else -> null
    }

    /** ESPN 3-letter abbreviation for a team, uppercased. */
    fun abbrev(league: String, teamName: String?): String {
        if (teamName.isNullOrBlank()) return ""
        val key = teamName.trim().lowercase()
        val table = when (league) {
            "nfl" -> nfl
            "nba" -> nba
            "mlb" -> mlb
            "nhl" -> nhl
            else -> return shortName(teamName).take(3).uppercase()
        }
        val match = table[key]
            ?: table.entries.firstOrNull { key.contains(it.key) || it.key.contains(key) }?.value
        return match?.uppercase() ?: shortName(teamName).take(3).uppercase()
    }

    /**
     * Strip the city prefix from a full team name to get the nickname
     * ("Detroit Pistons" → "Pistons", "New York Knicks" → "Knicks").
     * Falls back to the last word.
     */
    fun shortName(teamName: String?): String {
        if (teamName.isNullOrBlank()) return ""
        val full = teamName.trim()
        val parts = full.split(Regex("\\s+"))
        if (parts.size <= 1) return full
        // Two-word nicknames we want to preserve.
        val twoWordNicknames = setOf("trail blazers", "red sox", "white sox", "blue jays",
            "red wings", "blue jackets", "maple leafs", "golden knights", "hockey club")
        if (parts.size >= 3) {
            val lastTwo = "${parts[parts.size - 2]} ${parts.last()}".lowercase()
            if (twoWordNicknames.contains(lastTwo)) {
                return "${parts[parts.size - 2]} ${parts.last()}"
            }
        }
        return parts.last()
    }
}
