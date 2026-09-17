package com.driveguard.app.data

/**
 * Accumulates per-session metrics with zero UI coupling.
 *
 * Interruptions count face-loss episodes longer than the reset threshold and
 * classifier failures — never fabricated, only observable events.
 */
class SessionRecorder(
    private val faceLostEpisodeMs: Long = DEFAULT_FACE_LOST_EPISODE_MS,
) {
    private var startedAtMs: Long = 0
    private var endedAtMs: Long = 0
    private var alertCount = 0
    private var faceLostSinceMs: Long? = null
    private var faceLostEpisodes = 0
    private var classifierFailures = 0
    private var totalInferenceMs = 0L
    private var inferenceSamples = 0L

    fun start(nowMs: Long) {
        startedAtMs = nowMs
        endedAtMs = nowMs
        alertCount = 0
        faceLostSinceMs = null
        faceLostEpisodes = 0
        classifierFailures = 0
        totalInferenceMs = 0
        inferenceSamples = 0
    }

    fun onFaceLostStart(nowMs: Long) {
        if (faceLostSinceMs == null) faceLostSinceMs = nowMs
    }

    fun onFaceFound(nowMs: Long) {
        faceLostSinceMs?.let { lostAt ->
            if (nowMs - lostAt >= faceLostEpisodeMs) faceLostEpisodes += 1
            faceLostSinceMs = null
        }
    }

    fun onClassifierFailure() {
        classifierFailures += 1
    }

    fun onAlert() {
        alertCount += 1
    }

    fun onInferenceSample(inferenceMs: Long) {
        totalInferenceMs += inferenceMs
        inferenceSamples += 1
    }

    fun end(nowMs: Long) {
        endedAtMs = nowMs
    }

    fun build(startedAt: Long, endedAt: Long): SessionEntity = SessionEntity(
        startedAtMs = startedAt,
        endedAtMs = endedAt,
        durationMs = (endedAt - startedAt).coerceAtLeast(0),
        alertCount = alertCount,
        monitoringInterruptions = faceLostEpisodes + classifierFailures,
        avgInferenceMs = if (inferenceSamples > 0) totalInferenceMs.toFloat() / inferenceSamples else 0f,
    )

    fun alertCount(): Int = alertCount

    companion object {
        const val DEFAULT_FACE_LOST_EPISODE_MS = 1_500L
    }
}
