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
    suspend fun getWatchProviders(@Query("title") title: String): Response<WatchProvidersResponse>

    @GET("api/artwork/popular")
    suspend fun getPopularArtwork(): Response<PopularArtworkResponse>

    @GET("api/artwork")
    suspend fun getArtwork(@Query("title") title: String): Response<ArtworkResponse>

    @GET("api/channels/logos")
    suspend fun getChannelLogos(): Response<Map<String, ChannelLogoInfo>>

    @GET("api/user/profile")
    suspend fun getUserProfile(): Response<UserProfile>

    @PUT("api/user/zip")
    suspend fun setZipCode(@Body request: SetZipRequest): Response<UserProfile>

    @POST("api/recordings/schedule")
    suspend fun scheduleRecording(@Body request: ScheduleRequest): Response<RecordingSchedule>

    @GET("api/recordings/schedules")
    suspend fun getRecordingSchedules(): Response<List<RecordingSchedule>>

    @DELETE("api/recordings/schedule/{id}")
    suspend fun deleteSchedule(@Path("id") id: String): Response<Unit>

    @DELETE("api/recordings/{id}")
    suspend fun deleteRecording(@Path("id") id: String): Response<Unit>
}
