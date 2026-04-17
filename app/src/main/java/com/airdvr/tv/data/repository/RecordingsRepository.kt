package com.airdvr.tv.data.repository

import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.Recording
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RecordingsRepository {

    private val api = ApiClient.api
    private val gson = Gson()

    suspend fun getRecordings(): Result<List<Recording>> {
        return try {
            val response = api.getRecordings()
            if (response.isSuccessful) {
                val json = response.body()
                val recordings: List<Recording> = when {
                    json == null -> emptyList()
                    json.isJsonArray -> {
                        val type = object : TypeToken<List<Recording>>() {}.type
                        gson.fromJson(json, type)
                    }
                    json.isJsonObject -> {
                        val obj = json.asJsonObject
                        val arr = obj.getAsJsonArray("recordings") ?: obj.getAsJsonArray("data")
                        if (arr != null) {
                            val type = object : TypeToken<List<Recording>>() {}.type
                            gson.fromJson(arr, type)
                        } else emptyList()
                    }
                    else -> emptyList()
                }
                Result.success(recordings)
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
