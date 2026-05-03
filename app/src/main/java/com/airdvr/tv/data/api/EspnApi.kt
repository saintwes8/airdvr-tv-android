package com.airdvr.tv.data.api

import com.airdvr.tv.data.models.EspnNewsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * ESPN public site API. Used for sports headlines on the Sports Hub —
 * no auth, base URL https://site.api.espn.com/.
 *
 * Sport/league mapping (sport slug → league slug):
 *   basketball → nba
 *   football   → nfl
 *   baseball   → mlb
 *   hockey     → nhl
 */
interface EspnApi {
    @GET("apis/site/v2/sports/{sport}/{league}/news")
    suspend fun getNews(
        @Path("sport") sport: String,
        @Path("league") league: String,
        @Query("limit") limit: Int = 10
    ): Response<EspnNewsResponse>
}
