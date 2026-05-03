package com.airdvr.tv.util

import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun parseIsoToEpochSec(iso: String?): Long {
    if (iso.isNullOrBlank()) return 0L
    return try {
        // Handles "Z" and "+HH:MM" offsets
        OffsetDateTime.parse(iso).toEpochSecond()
    } catch (_: Exception) {
        try {
            // No offset — assume UTC (legacy data)
            LocalDateTime.parse(iso).atZone(ZoneId.of("UTC")).toEpochSecond()
        } catch (_: Exception) {
            try {
                // Last resort — interpret as local time
                LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toEpochSecond()
            } catch (_: Exception) {
                0L
            }
        }
    }
}

/**
 * Format a sports-API ISO timestamp (e.g. "2026-05-02T19:30:00Z") in the user's
 * local timezone using [pattern]. Returns "" if [iso] can't be parsed.
 */
fun formatGameTimeLocal(iso: String?, pattern: String = "h:mm a"): String {
    val epoch = parseIsoToEpochSec(iso)
    if (epoch <= 0L) return ""
    val fmt = SimpleDateFormat(pattern, Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }
    return fmt.format(Date(epoch * 1000L))
}
