package com.airdvr.tv.data.repository

import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.Recording

class RecordingsRepository {

    private val api = ApiClient.api

    suspend fun getRecordings(): Result<List<Recording>> {
        return try {
            val response = api.getRecordings()
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Failed to load recordings: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun updateResumePosition(recordingId: String, positionSeconds: Int): Result<Unit> {
        return try {
            val response = api.updateResumePosition(
                recordingId,
                mapOf("ResumeOffsetSeconds" to positionSeconds)
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update resume position: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}
