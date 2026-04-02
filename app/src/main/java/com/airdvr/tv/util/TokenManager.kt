package com.airdvr.tv.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                Constants.PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular prefs if encryption fails (e.g. emulator without secure hardware)
            context.getSharedPreferences(Constants.PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(Constants.KEY_ACCESS_TOKEN, accessToken)
            .putString(Constants.KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(Constants.KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(Constants.KEY_REFRESH_TOKEN, null)

    fun clearTokens() {
        prefs.edit()
            .remove(Constants.KEY_ACCESS_TOKEN)
            .remove(Constants.KEY_REFRESH_TOKEN)
            .apply()
    }

    fun isLoggedIn(): Boolean = !getAccessToken().isNullOrBlank()
}
