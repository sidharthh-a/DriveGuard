package com.driveguard.app

import android.app.Application
import com.driveguard.app.data.AppDatabase
import com.driveguard.app.data.SessionRepository
import com.driveguard.app.data.SettingsRepository

/**
 * Minimal manual DI: one Application-scoped instance of each repository.
 * No framework needed at this scale; constructors stay testable.
 */
class DriveGuardApplication : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var sessionRepository: SessionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        sessionRepository = SessionRepository(AppDatabase.get(this).sessionDao())
    }
}
