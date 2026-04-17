package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.AirDVRApp
import com.airdvr.tv.data.models.SetZipRequest
import com.airdvr.tv.data.models.StorageInfo
import com.airdvr.tv.data.api.ApiClient
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
    val userZipCode: String = "",
    val tunerTotal: Int = 2,
    val tunersInUse: Int = 0,
    val storageInfo: StorageInfo? = null,
    val selectedQuality: String = "Auto",
    val guideOpacity: Float = 0.7f,
    val guideColor: String = "#21262D",
    val appVersion: String = "",
    val error: String? = null,
    val toastMessage: String? = null
)

class SettingsViewModel : ViewModel() {

    private val authRepo = AuthRepository()
    private val api = ApiClient.api
    private val tokenManager = AirDVRApp.instance.tokenManager
    private val guidePrefsManager = AirDVRApp.instance.guidePreferencesManager

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            userEmail = tokenManager.getUserEmail(),
            selectedQuality = guidePrefsManager.quality.value,
            guideOpacity = guidePrefsManager.opacity.value,
            guideColor = guidePrefsManager.color.value
        )
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
                val profileDeferred = async {
                    try { api.getUserProfile() } catch (e: Exception) { null }
                }

                val tunersResponse = tunersDeferred.await()
                val storageResponse = storageDeferred.await()
                val profileResponse = profileDeferred.await()

                val total = tunersResponse?.body()?.total ?: 2
                val inUse = tunersResponse?.body()?.inUse ?: 0
                val zip = profileResponse?.body()?.zipCode ?: ""

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    tunerTotal = total,
                    tunersInUse = inUse,
                    storageInfo = storageResponse?.body(),
                    userZipCode = zip
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun setQuality(quality: String) {
        guidePrefsManager.setQuality(quality)
        _uiState.value = _uiState.value.copy(selectedQuality = quality)
    }

    fun cycleQuality() {
        val options = listOf("Auto", "1080p", "720p", "480p")
        val currentIdx = options.indexOf(_uiState.value.selectedQuality).coerceAtLeast(0)
        val next = options[(currentIdx + 1) % options.size]
        setQuality(next)
    }

    fun setGuideOpacity(opacity: Float) {
        guidePrefsManager.setOpacity(opacity)
        _uiState.value = _uiState.value.copy(guideOpacity = opacity)
    }

    fun setGuideColor(color: String) {
        guidePrefsManager.setColor(color)
        _uiState.value = _uiState.value.copy(guideColor = color)
    }

    fun updateZipCode(zip: String) {
        viewModelScope.launch {
            try {
                val resp = api.setZipCode(SetZipRequest(zip))
                if (resp.isSuccessful) {
                    _uiState.value = _uiState.value.copy(userZipCode = zip, toastMessage = "Guide data updating...")
                    kotlinx.coroutines.delay(3000)
                    _uiState.value = _uiState.value.copy(toastMessage = null)
                } else {
                    _uiState.value = _uiState.value.copy(toastMessage = "Could not update location")
                    kotlinx.coroutines.delay(3000)
                    _uiState.value = _uiState.value.copy(toastMessage = null)
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(toastMessage = "Could not connect. Check your network.")
                kotlinx.coroutines.delay(3000)
                _uiState.value = _uiState.value.copy(toastMessage = null)
            }
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
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
