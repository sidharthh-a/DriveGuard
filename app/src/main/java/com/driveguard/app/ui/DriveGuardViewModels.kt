package com.driveguard.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.driveguard.app.data.AppSettings
import com.driveguard.app.data.AppearanceMode
import com.driveguard.app.data.SessionSummary
import com.driveguard.app.detection.Sensitivity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Exposes user settings as UI state and persists changes. */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as com.driveguard.app.DriveGuardApplication).settingsRepository

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun completeOnboarding() = launch { repo.setOnboardingCompleted(true) }
    fun dismissPrecheck() = launch { repo.setPrecheckDismissed(true) }
    fun setSensitivity(s: Sensitivity) = launch { repo.setSensitivity(s.name) }
    fun setSound(enabled: Boolean) = launch { repo.setSoundEnabled(enabled) }
    fun setVibration(enabled: Boolean) = launch { repo.setVibrationEnabled(enabled) }
    fun setAppearance(mode: AppearanceMode) = launch { repo.setAppearance(mode) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/** Exposes session history and deletion/clear operations. */
class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as com.driveguard.app.DriveGuardApplication).sessionRepository

    val sessions: StateFlow<List<SessionSummary>> = repo.observeSessions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun delete(id: Long) = viewModelScope.launch { repo.deleteById(id) }
    fun clearAll() = viewModelScope.launch { repo.clearAll() }
}
