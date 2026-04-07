package com.airdvr.tv.data.api

import com.airdvr.tv.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface AirDVRApi {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<AuthResponse>

    @GET("api/guide")
    suspend fun getGuide(): Response<GuideResponse>

    @GET("api/recordings")
    suspend fun getRecordings(): Response<List<Recording>>

    @GET("api/tuners")
    suspend fun getTuners(): Response<TunersResponse>

    @GET("api/storage")
    suspend fun getStorage(): Response<StorageInfo>

    @GET("schedules")
    suspend fun getSchedules(): Response<List<Recording>>

    @PATCH("api/recordings/{id}")
    suspend fun updateResumePosition(
        @Path("id") id: String,
        @Body body: Map<String, Int>
    ): Response<Unit>

    @GET("api/watch-providers")
    suspend fun getWatchProviders(@Query("title") title: String): Response<List<WatchProvider>>
}
