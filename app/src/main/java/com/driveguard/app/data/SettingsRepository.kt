package com.driveguard.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Appearance mode for the app. */
enum class AppearanceMode { SYSTEM, LIGHT, DARK }

/** Immutable snapshot of user settings. */
data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val precheckDismissed: Boolean = false,
    val sensitivityName: String = "MEDIUM",
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "driveguard_settings")

/** DataStore-backed settings store. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarding_completed")
        val PRECHECK_DISMISSED = booleanPreferencesKey("precheck_dismissed")
        val SENSITIVITY = stringPreferencesKey("sensitivity")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val APPEARANCE = stringPreferencesKey("appearance_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            onboardingCompleted = prefs[Keys.ONBOARDED] ?: false,
            precheckDismissed = prefs[Keys.PRECHECK_DISMISSED] ?: false,
            sensitivityName = prefs[Keys.SENSITIVITY] ?: "MEDIUM",
            soundEnabled = prefs[Keys.SOUND] ?: true,
            vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
            appearance = prefs[Keys.APPEARANCE]?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() }
                ?: AppearanceMode.SYSTEM,
        )
    }

    suspend fun setOnboardingCompleted(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDED] = done }
    }

    suspend fun setPrecheckDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[Keys.PRECHECK_DISMISSED] = dismissed }
    }

    suspend fun setSensitivity(name: String) {
        context.dataStore.edit { it[Keys.SENSITIVITY] = name }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND] = enabled }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATION] = enabled }
    }

    suspend fun setAppearance(mode: AppearanceMode) {
        context.dataStore.edit { it[Keys.APPEARANCE] = mode.name }
    }
}
