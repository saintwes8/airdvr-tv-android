package com.airdvr.tv.util

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

fun parseIsoToEpochSec(iso: String?): Long {
    if (iso.isNullOrBlank()) return 0L
    return try {
        OffsetDateTime.parse(iso).toEpochSecond()
    } catch (e: Exception) {
        try {
            // API returns times without timezone offset -- assume UTC
            LocalDateTime.parse(iso).atZone(ZoneId.of("UTC")).toEpochSecond()
        } catch (e2: Exception) {
            0L
        }
    }
}
