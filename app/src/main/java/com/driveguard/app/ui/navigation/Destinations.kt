package com.driveguard.app.ui.navigation

/** Type-safe route constants for Navigation-Compose. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PRECHECK = "precheck"
    const val DRIVING = "driving"
    const val SUMMARY = "summary/{sessionId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"

    fun summary(sessionId: Long) = "summary/$sessionId"
}
