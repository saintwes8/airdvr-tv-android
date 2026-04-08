package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.ArtworkItem
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
    val popular: List<ArtworkItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingPopular: Boolean = false,
    val error: String? = null
)

class WhereToWatchViewModel : ViewModel() {

    private val api = ApiClient.api

    private val _uiState = MutableStateFlow(WhereToWatchUiState())
    val uiState: StateFlow<WhereToWatchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadPopular()
    }

    fun loadPopular() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPopular = true)
            try {
                val resp = api.getPopularArtwork()
                if (resp.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        popular = resp.body() ?: emptyList(),
                        isLoadingPopular = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoadingPopular = false)
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingPopular = false)
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isLoading = false, error = null)
            return
        }
        searchJob = viewModelScope.launch {
            delay(400L)
            performSearch(query)
        }
    }

    /** Run a search immediately (e.g., user clicked a popular poster). */
    fun searchTitle(title: String) {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(query = title)
        searchJob = viewModelScope.launch { performSearch(title) }
    }

    private suspend fun performSearch(query: String) {
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
