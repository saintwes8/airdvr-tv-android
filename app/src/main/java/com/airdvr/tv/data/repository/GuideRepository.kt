package com.airdvr.tv.data.repository

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
            val start = now.minusSeconds(1800).toString()       // 30 min ago
            val end = now.plusSeconds(24 * 3600).toString()      // +24 hours
            val response = api.getGuide(start = start, end = end)
            if (response.isSuccessful) {
                val body = response.body()
                val channels = body?.channels ?: emptyList()
                val programs = body?.programs ?: emptyList()
                Result.success(Pair(channels, programs))
            } else {
                Result.failure(Exception("Failed to load guide: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}
