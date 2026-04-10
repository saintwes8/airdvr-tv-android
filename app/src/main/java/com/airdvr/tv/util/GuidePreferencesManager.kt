package com.airdvr.tv.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GuidePreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences("airdvr_guide_prefs", Context.MODE_PRIVATE)

    private val _opacity = MutableStateFlow(prefs.getFloat("guide_opacity", 0.7f))
    val opacity: StateFlow<Float> = _opacity.asStateFlow()

    private val _color = MutableStateFlow(prefs.getString("guide_color", "#21262D") ?: "#21262D")
    val color: StateFlow<String> = _color.asStateFlow()

    private val _quality = MutableStateFlow(prefs.getString("guide_quality", "Auto") ?: "Auto")
    val quality: StateFlow<String> = _quality.asStateFlow()

    private val _ccEnabled = MutableStateFlow(prefs.getBoolean("cc_enabled", false))
    val ccEnabled: StateFlow<Boolean> = _ccEnabled.asStateFlow()

    fun setOpacity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        prefs.edit().putFloat("guide_opacity", clamped).apply()
        _opacity.value = clamped
    }

    fun setColor(value: String) {
        prefs.edit().putString("guide_color", value).apply()
        _color.value = value
    }

    fun setQuality(value: String) {
        prefs.edit().putString("guide_quality", value).apply()
        _quality.value = value
    }

    fun toggleCC() {
        val newValue = !_ccEnabled.value
        prefs.edit().putBoolean("cc_enabled", newValue).apply()
        _ccEnabled.value = newValue
    }
}
