package com.airdvr.tv.data.models

data class User(
    val id: String,
    val email: String,
    val plan: String = "Free"
)
