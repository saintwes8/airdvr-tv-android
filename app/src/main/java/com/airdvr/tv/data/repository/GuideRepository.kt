package com.airdvr.tv.data.repository

import android.util.Log
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.Channel
import com.airdvr.tv.data.models.EpgProgram

class GuideRepository {

    private val api = ApiClient.api

    suspend fun getChannels(): Result<List<Channel>> {
        return try {
            val response = api.getGuide()
            if (response.isSuccessful) {
                val channels = response.body()?.channels ?: emptyList()
                Result.success(channels)
            } else {
                Result.failure(Exception("Failed to load channels: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getEpgPrograms(): Result<List<EpgProgram>> {
        return try {
            val response = api.getGuide()
            if (response.isSuccessful) {
                val programs = response.body()?.programs ?: emptyList()
                Result.success(programs)
            } else {
                Result.failure(Exception("Failed to load EPG: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getChannelsWithPrograms(): Result<Pair<List<Channel>, List<EpgProgram>>> {
        return try {
            val now = java.time.Instant.now()
            val start = now.minusSeconds(1800).toString()
            val end = now.plusSeconds(24 * 3600).toString()
            Log.d("GUIDE", "Requesting guide: start=$start end=$end hours=24 limit=10000")
            val response = api.getGuide(start = start, end = end, hours = 24, limit = 10000)
            if (response.isSuccessful) {
                val body = response.body()
                val channels = body?.channels ?: emptyList()
                val programs = body?.programs ?: emptyList()
                if (programs.isNotEmpty()) {
                    val earliest = programs.minOf { it.startEpochSec }
                    val latest = programs.maxOf { it.endEpochSec }
                    val rangeHours = (latest - earliest) / 3600.0
                    Log.d("GUIDE", "Programs loaded: ${programs.size}, channels: ${channels.size}, range: ${rangeHours}h")
                } else {
                    Log.d("GUIDE", "No programs returned, channels: ${channels.size}")
                }
                Result.success(Pair(channels, programs))
            } else {
                Log.d("GUIDE", "Guide API error: ${response.code()}")
                Result.failure(Exception("Failed to load guide: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.d("GUIDE", "Guide API exception: ${e.message}")
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}
