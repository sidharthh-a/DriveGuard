package com.driveguard.app.alerts

import com.driveguard.app.detection.DrowsinessDetector

/**
 * Escalating alert levels, mirroring the project spec:
 *   Level 1 "Stay Alert" → Level 2 "Drowsiness Detected" → Level 3 "Please Stop and Rest".
 */
enum class AlertLevel(val message: String) {
    LEVEL_1("Stay Alert"),
    LEVEL_2("Drowsiness detected"),
    LEVEL_3("Please stop and rest"),
}

/**
 * One discrete alert emission, consumed by the platform layer
 * ([AlertPlayer]) to produce sound/vibration/UI.
 */
data class AlertEvent(
    val level: AlertLevel,
    val timestampMs: Long,
)

/**
 * Maps detector state transitions to escalating alerts with anti-spam
 * guarantees. Pure Kotlin; all time is injected.
 *
 * Escalation rule: each *cooldown-separated* alert episode increments the
 * level (max LEVEL_3). A long awake period resets escalation.
 */
class AlertEngine(
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val escalationResetMs: Long = DEFAULT_ESCALATION_RESET_MS,
) {
    private var currentLevel: AlertLevel = AlertLevel.LEVEL_1
    private var lastAlertAtMs: Long? = null
    private var lastAlertEndedAtMs: Long? = null
    private var episodes = 0

    /**
     * Called when the detector reports a drowsiness episode starting.
     * Returns the event to emit, or null if still in cooldown.
     */
    fun onDrowsinessStart(nowMs: Long): AlertEvent? {
        lastAlertAtMs?.let { last ->
            if (nowMs - last < cooldownMs) return null
        }
        // Reset escalation after a long clean period.
        lastAlertEndedAtMs?.let { ended ->
            if (nowMs - ended >= escalationResetMs) {
                episodes = 0
                currentLevel = AlertLevel.LEVEL_1
            }
        }
        episodes++
        currentLevel = AlertLevel.entries[episodes.coerceAtMost(AlertLevel.entries.size) - 1]
        lastAlertAtMs = nowMs
        return AlertEvent(currentLevel, nowMs)
    }

    /** Called when the drowsiness episode ends (detector latched alert cleared). */
    fun onDrowsinessEnd(nowMs: Long) {
        lastAlertEndedAtMs = nowMs
    }

    /** Manual reset (session end). */
    fun reset() {
        currentLevel = AlertLevel.LEVEL_1
        lastAlertAtMs = null
        lastAlertEndedAtMs = null
        episodes = 0
    }

    fun currentLevel(): AlertLevel = currentLevel

    companion object {
        const val DEFAULT_COOLDOWN_MS = 8_000L
        const val DEFAULT_ESCALATION_RESET_MS = 5 * 60_000L
    }
}
