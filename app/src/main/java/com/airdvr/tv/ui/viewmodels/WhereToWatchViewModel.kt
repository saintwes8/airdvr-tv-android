package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.WatchProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WhereToWatchUiState(
    val query: String = "",
    val results: List<WatchProvider> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class WhereToWatchViewModel : ViewModel() {

    private val api = ApiClient.api

    private val _uiState = MutableStateFlow(WhereToWatchUiState())
    val uiState: StateFlow<WhereToWatchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isLoading = false, error = null)
            return
        }
        searchJob = viewModelScope.launch {
            delay(400L)
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val resp = api.getWatchProviders(query)
                if (resp.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        results = resp.body() ?: emptyList(),
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Search failed: ${resp.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }
}
