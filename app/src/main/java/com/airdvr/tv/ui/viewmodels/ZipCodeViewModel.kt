package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.SetZipRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ZipCodeUiState(
    val zipCode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

class ZipCodeViewModel : ViewModel() {

    private val api = ApiClient.api

    private val _uiState = MutableStateFlow(ZipCodeUiState())
    val uiState: StateFlow<ZipCodeUiState> = _uiState.asStateFlow()

    fun onZipChange(zip: String) {
        if (zip.length <= 5 && zip.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(zipCode = zip, error = null)
        }
    }

    fun submit() {
        val zip = _uiState.value.zipCode
        if (zip.length != 5) {
            _uiState.value = _uiState.value.copy(error = "Enter a valid 5-digit zip code")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val resp = api.setZipCode(SetZipRequest(zip))
                if (resp.isSuccessful) {
                    _uiState.value = _uiState.value.copy(isLoading = false, success = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Could not save location. Try again."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Could not connect. Check your network."
                )
            }
        }
    }
}
