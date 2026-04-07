package com.airdvr.tv.data.models

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("id") val id: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("plan") val plan: String? = "Free"
)
