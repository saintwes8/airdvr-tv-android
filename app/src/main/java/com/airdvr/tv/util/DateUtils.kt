package com.airdvr.tv.util

import java.time.OffsetDateTime

fun parseIsoToEpochSec(iso: String?): Long {
    if (iso.isNullOrBlank()) return 0L
    return try {
        OffsetDateTime.parse(iso).toEpochSecond()
    } catch (e: Exception) {
        0L
    }
}
