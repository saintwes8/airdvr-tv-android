package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.SearchResult
import com.airdvr.tv.data.repository.ArtworkRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WhereToWatchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val popular: List<SearchResult> = emptyList(),
    val selectedResult: SearchResult? = null,
    val detailResult: SearchResult? = null,
    val isLoading: Boolean = false,
    val isLoadingPopular: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val error: String? = null
)

class WhereToWatchViewModel : ViewModel() {

    private val publicApi = ApiClient.publicApi

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
                val items = ArtworkRepository.fetchPopular()
                _uiState.value = _uiState.value.copy(popular = items, isLoadingPopular = false)
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
            val resp = publicApi.getWatchProviders(query)
            if (resp.isSuccessful) {
                _uiState.value = _uiState.value.copy(
                    results = resp.body()?.results ?: emptyList(),
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Search unavailable right now"
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Could not connect. Check your network."
            )
        }
    }

    fun selectResult(result: SearchResult) {
        _uiState.value = _uiState.value.copy(
            selectedResult = result,
            detailResult = result,
            isLoadingDetail = true
        )
        viewModelScope.launch {
            try {
                val resp = publicApi.getWatchProviders(result.title ?: "")
                if (resp.isSuccessful) {
                    val detailed = resp.body()?.results?.firstOrNull()
                    _uiState.value = _uiState.value.copy(
                        detailResult = detailed ?: result,
                        isLoadingDetail = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoadingDetail = false)
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingDetail = false)
            }
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedResult = null,
            detailResult = null
        )
    }
}
