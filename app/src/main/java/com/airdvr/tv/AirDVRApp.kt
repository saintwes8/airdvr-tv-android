package com.airdvr.tv

import android.app.Application
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.util.TokenManager

class AirDVRApp : Application() {

    lateinit var tokenManager: TokenManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        tokenManager = TokenManager(this)
        ApiClient.init(tokenManager)
    }

    companion object {
        lateinit var instance: AirDVRApp
            private set
    }
}
