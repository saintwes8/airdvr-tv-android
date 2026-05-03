package com.airdvr.tv.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SportsPreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences("airdvr_sports_prefs", Context.MODE_PRIVATE)

    private val _showBettingLines = MutableStateFlow(prefs.getBoolean(KEY_SHOW_BETTING, false))
    val showBettingLines: StateFlow<Boolean> = _showBettingLines.asStateFlow()

    fun setShowBettingLines(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_BETTING, value).apply()
        _showBettingLines.value = value
    }

    companion object {
        private const val KEY_SHOW_BETTING = "show_betting_lines"
    }
}
