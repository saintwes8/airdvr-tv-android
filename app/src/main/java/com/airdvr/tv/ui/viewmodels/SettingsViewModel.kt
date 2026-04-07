package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.AirDVRApp
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.models.StorageInfo
import com.airdvr.tv.data.models.TunerInfo
import com.airdvr.tv.data.repository.AuthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val userEmail: String = "",
    val userPlan: String = "Free",
    val tuners: List<TunerInfo> = emptyList(),
    val storageInfo: StorageInfo? = null,
    val selectedQuality: String = "Auto",
    val ccEnabled: Boolean = false,
    val appVersion: String = "",
    val error: String? = null
)

class SettingsViewModel : ViewModel() {

    private val authRepo = AuthRepository()
    private val api = ApiClient.api
    private val tokenManager = AirDVRApp.instance.tokenManager

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(userEmail = tokenManager.getUserEmail())
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val tunersDeferred = async {
                    try { api.getTuners() } catch (e: Exception) { null }
                }
                val storageDeferred = async {
                    try { api.getStorage() } catch (e: Exception) { null }
                }

                val tunersResponse = tunersDeferred.await()
                val storageResponse = storageDeferred.await()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    tuners = tunersResponse?.body()?.tuners ?: emptyList(),
                    storageInfo = storageResponse?.body()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun setQuality(quality: String) {
        _uiState.value = _uiState.value.copy(selectedQuality = quality)
    }

    fun toggleCC() {
        _uiState.value = _uiState.value.copy(ccEnabled = !_uiState.value.ccEnabled)
    }

    fun setAppVersion(version: String) {
        _uiState.value = _uiState.value.copy(appVersion = version)
    }

    fun setUserEmail(email: String) {
        _uiState.value = _uiState.value.copy(userEmail = email)
    }

    fun logout(): Boolean {
        authRepo.logout()
        return true
    }
}
