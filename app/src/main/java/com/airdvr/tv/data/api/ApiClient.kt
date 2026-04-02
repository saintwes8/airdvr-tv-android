package com.airdvr.tv.data.api

import com.airdvr.tv.data.models.RefreshRequest
import com.airdvr.tv.util.Constants
import com.airdvr.tv.util.TokenManager
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var tokenManager: TokenManager? = null

    fun init(tm: TokenManager) {
        tokenManager = tm
    }

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    private val authInterceptor = Interceptor { chain ->
        val token = tokenManager?.getAccessToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private val tokenRefreshInterceptor = Interceptor { chain ->
        val response: Response = chain.proceed(chain.request())
        if (response.code == 401) {
            response.close()
            val refreshToken = tokenManager?.getRefreshToken()
            if (refreshToken != null) {
                // Use a bare OkHttpClient for the refresh call to avoid recursion
                val refreshClient = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                val refreshRetrofit = Retrofit.Builder()
                    .baseUrl(Constants.BASE_URL)
                    .client(refreshClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()
                val refreshApi = refreshRetrofit.create(AirDVRApi::class.java)
                try {
                    val refreshResponse = refreshRetrofit
                        .create(AirDVRApi::class.java)
                        .let {
                            kotlinx.coroutines.runBlocking {
                                it.refresh(RefreshRequest(refreshToken))
                            }
                        }
                    if (refreshResponse.isSuccessful) {
                        val body = refreshResponse.body()
                        if (body != null) {
                            tokenManager?.saveTokens(body.accessToken, body.refreshToken)
                            // Retry original request with new token
                            val newRequest = chain.request().newBuilder()
                                .removeHeader("Authorization")
                                .addHeader("Authorization", "Bearer ${body.accessToken}")
                                .build()
                            return@Interceptor chain.proceed(newRequest)
                        }
                    } else {
                        // Refresh failed — clear tokens so the UI navigates to login
                        tokenManager?.clearTokens()
                    }
                } catch (e: Exception) {
                    tokenManager?.clearTokens()
                }
            } else {
                tokenManager?.clearTokens()
            }
            // Return the original 401 so callers know auth failed
            chain.proceed(chain.request())
        } else {
            response
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(tokenRefreshInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val api: AirDVRApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AirDVRApi::class.java)
    }
}
