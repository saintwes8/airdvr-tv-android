package com.airdvr.tv.data.repository

import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.ChannelLogoInfo

object ChannelLogoRepository {

    private val cache = HashMap<String, ChannelLogoInfo>()
    private var loaded = false

    suspend fun loadLogos() {
        if (loaded) return
        try {
            val response = ApiClient.publicApi.getChannelLogos()
            if (response.isSuccessful) {
                response.body()?.forEach { (guideName, info) ->
                    cache[guideName] = info
                }
                loaded = true
            }
        } catch (_: Exception) {}
    }

    fun getLogoInfo(guideName: String): ChannelLogoInfo? = cache[guideName]
}
