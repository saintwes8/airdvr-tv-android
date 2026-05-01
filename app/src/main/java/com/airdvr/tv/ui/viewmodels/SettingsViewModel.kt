package com.airdvr.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdvr.tv.AirDVRApp
import com.airdvr.tv.data.models.SetZipRequest
import com.airdvr.tv.data.models.StorageInfo
import com.airdvr.tv.data.models.StoragePreferenceRequest
import com.airdvr.tv.data.models.StorageUsage
import com.airdvr.tv.data.api.ApiClient
import com.airdvr.tv.data.repository.AuthRepository
import com.airdvr.tv.data.repository.RecordingsRepository
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
    val recordingsUsedMb: Float = 0f,
    val selectedQuality: String = "Auto",
    val guideOpacity: Float = 0.7f,
    val guideColor: String = "#21262D",
    val appVersion: String = "",
    // Recording storage
    val storagePreference: String = "local",
    val deviceName: String = "",
    val keepLocalCopyAfterCloud: Boolean = true,
    val storageUpdating: Boolean = false,
    // Cloud-storage usage + retention
    val cloudStorageUsage: StorageUsage? = null,
    val cloudRetentionDays: Int? = null,   // null == "Never"
    val error: String? = null,
    val toastMessage: String? = null
) {
    val isPro: Boolean get() = userPlan.lowercase() in listOf("pro", "premium")
    val uploadToCloud: Boolean get() = storagePreference.lowercase() == "cloud"
}

class SettingsViewModel : ViewModel() {

    private val authRepo = AuthRepository()
    private val recordingsRepo = RecordingsRepository()
    private val api = ApiClient.api
    private val tokenManager = AirDVRApp.instance.tokenManager
    private val guidePrefsManager = AirDVRApp.instance.guidePreferencesManager
    private val recordingPrefs = AirDVRApp.instance
        .applicationContext
        .getSharedPreferences("airdvr_recording_prefs", android.content.Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            userEmail = tokenManager.getUserEmail(),
            selectedQuality = guidePrefsManager.quality.value,
            guideOpacity = guidePrefsManager.opacity.value,
            guideColor = guidePrefsManager.color.value,
            keepLocalCopyAfterCloud = recordingPrefs.getBoolean("keep_local_copy", true)
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
                val recordingsDeferred = async {
                    try { recordingsRepo.getRecordings().getOrNull() } catch (e: Exception) { null }
                }
                val cloudUsageDeferred = async {
                    try { api.getStorageUsage() } catch (e: Exception) { null }
                }

                val tunersResponse = tunersDeferred.await()
                val storageResponse = storageDeferred.await()
                val profileResponse = profileDeferred.await()
                val recordings = recordingsDeferred.await()
                val cloudUsage = cloudUsageDeferred.await()?.takeIf { it.isSuccessful }?.body()

                val total = tunersResponse?.body()?.total ?: 2
                val inUse = tunersResponse?.body()?.inUse ?: 0
                val zip = profileResponse?.body()?.zipCode ?: ""
                val plan = profileResponse?.body()?.plan ?: "Free"
                val storagePref = profileResponse?.body()?.storagePreference ?: "local"

                // Sum recording file sizes as a fallback for storage usage
                val recordingsUsedMb = recordings?.sumOf { (it.fileSizeMb ?: 0f).toDouble() }?.toFloat() ?: 0f

                // Prefer the device name from the first local recording if present
                val deviceName = recordings?.firstOrNull { !it.deviceName.isNullOrBlank() }?.deviceName ?: ""

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    tunerTotal = total,
                    tunersInUse = inUse,
                    storageInfo = storageResponse?.body(),
                    userZipCode = zip,
                    userPlan = plan,
                    storagePreference = storagePref,
                    deviceName = deviceName,
                    recordingsUsedMb = recordingsUsedMb,
                    cloudStorageUsage = cloudUsage,
                    cloudRetentionDays = cloudUsage?.cloudRetentionDays
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

    fun toggleUploadToCloud() {
        val state = _uiState.value
        if (!state.isPro && !state.uploadToCloud) {
            // Can't enable cloud on free plan
            showToast("Requires Pro subscription")
            return
        }
        val next = if (state.uploadToCloud) "local" else "cloud"
        setStoragePreference(next)
    }

    fun toggleKeepLocalCopy() {
        val next = !_uiState.value.keepLocalCopyAfterCloud
        recordingPrefs.edit().putBoolean("keep_local_copy", next).apply()
        _uiState.value = _uiState.value.copy(keepLocalCopyAfterCloud = next)
    }

    /** Set the global cloud-recording retention. `days = null` → "Never". */
    fun setCloudRetentionDays(days: Int?) {
        val current = _uiState.value.cloudRetentionDays
        if (current == days) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(storageUpdating = true)
            try {
                val resp = api.setStoragePreference(
                    StoragePreferenceRequest(cloudRetentionDays = days)
                )
                if (resp.isSuccessful) {
                    val label = days?.let { "$it days" } ?: "Never"
                    _uiState.value = _uiState.value.copy(
                        cloudRetentionDays = days,
                        storageUpdating = false,
                        toastMessage = "Cloud retention: $label"
                    )
                    // Refresh cloud usage to reflect any pruning that may follow.
                    val usage = try { api.getStorageUsage() } catch (_: Exception) { null }
                    if (usage?.isSuccessful == true) {
                        _uiState.value = _uiState.value.copy(cloudStorageUsage = usage.body())
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        storageUpdating = false,
                        toastMessage = if (resp.code() == 403) "Requires Pro subscription" else "Could not update retention"
                    )
                }
                kotlinx.coroutines.delay(3000)
                _uiState.value = _uiState.value.copy(toastMessage = null)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    storageUpdating = false,
                    toastMessage = "Could not connect. Check your network."
                )
                kotlinx.coroutines.delay(3000)
                _uiState.value = _uiState.value.copy(toastMessage = null)
            }
        }
    }

    private fun setStoragePreference(pref: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(storageUpdating = true)
            try {
                val resp = api.setStoragePreference(StoragePreferenceRequest(storagePreference = pref))
                if (resp.isSuccessful) {
                    val applied = resp.body()?.storagePreference ?: pref
                    _uiState.value = _uiState.value.copy(
                        storagePreference = applied,
                        storageUpdating = false,
                        toastMessage = if (applied == "cloud") "Uploading new recordings to cloud" else "Saving recordings locally"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        storageUpdating = false,
                        toastMessage = if (resp.code() == 403) "Requires Pro subscription" else "Could not update storage preference"
                    )
                }
                kotlinx.coroutines.delay(3000)
                _uiState.value = _uiState.value.copy(toastMessage = null)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    storageUpdating = false,
                    toastMessage = "Could not connect. Check your network."
                )
                kotlinx.coroutines.delay(3000)
                _uiState.value = _uiState.value.copy(toastMessage = null)
            }
        }
    }

    private fun showToast(msg: String) {
        _uiState.value = _uiState.value.copy(toastMessage = msg)
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.value = _uiState.value.copy(toastMessage = null)
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
