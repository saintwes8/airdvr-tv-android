package com.airdvr.tv.data.repository

import com.airdvr.tv.AirDVRApp
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.LoginRequest
import com.airdvr.tv.data.models.RefreshRequest
import com.airdvr.tv.util.TokenManager

class AuthRepository(
    private val tokenManager: TokenManager = AirDVRApp.instance.tokenManager
) {

    private val api = ApiClient.api

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            val response = api.login(LoginRequest(email, password))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    tokenManager.saveTokens(body.accessToken ?: "", body.refreshToken ?: "")
                    tokenManager.saveEmail(email)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorMsg = when (response.code()) {
                    401 -> "Invalid email or password"
                    403 -> "Account access denied"
                    429 -> "Too many attempts, please try again later"
                    500 -> "Server error, please try again"
                    else -> "Login failed (${response.code()})"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    fun logout() {
        tokenManager.clearTokens()
    }

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    suspend fun refreshToken(): Result<Unit> {
        val refreshToken = tokenManager.getRefreshToken()
            ?: return Result.failure(Exception("No refresh token available"))
        return try {
            val response = api.refresh(RefreshRequest(refreshToken))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    tokenManager.saveTokens(body.accessToken ?: "", body.refreshToken ?: "")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Empty refresh response"))
                }
            } else {
                tokenManager.clearTokens()
                Result.failure(Exception("Token refresh failed (${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error during token refresh: ${e.message}"))
        }
    }
}
