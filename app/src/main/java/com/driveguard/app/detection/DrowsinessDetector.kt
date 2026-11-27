package com.driveguard.app.detection

/**
 * Minimal frame verdict from the camera/face pipeline.
 *
 * [pClosed] is the combined eye-closure probability across both eyes — from
 * ML Kit's per-eye openness probability (1 − openness), combined by
 * [EyeStateClassifier.combineEyes]. Only meaningful when [hasFace] is true.
 */
data class FrameVerdict(
    val hasFace: Boolean,
    val pClosed: Float,
)

/**
 * Observation categories the detector can emit per frame.
 */
enum class Observation {
    /** Model confidently sees open eyes this frame. */
    EYES_OPEN,

    /** Model sees closed eyes this frame. */
    EYES_CLOSED,

    /** Model output in the hysteresis band — insufficient evidence either way. */
    UNCERTAIN,

    /** No face in frame — explicitly NOT drowsiness. */
    NO_FACE,

    /** Classifier failed this frame (model unavailable / inference error). */
    CLASSIFIER_FAILED,
}

/**
 * Face tracking status as seen by the decision engine.
 *
 * [TEMPORARILY_LOST] covers detection gaps inside the grace window: the
 * tracked face is expected to reappear, all evidence is frozen, and the UI
 * shows a warning rather than a failure.
 */
enum class FaceTrackStatus { TRACKED, TEMPORARILY_LOST, LOST }

/**
 * Temporal state machine converting per-frame P(closed) into a stable
 * drowsiness decision. Pure Kotlin, deterministic, clock injected.
 *
 * Design rules:
 *  1. A single frame can never trigger an alert — evidence accumulates over
 *     [DrowsinessConfig.closedDurationMs] of continuously closed eyes.
 *  2. Hysteresis on both edges: closed requires p >= confidenceThreshold,
 *     open requires p <= openThreshold, in between is UNCERTAIN (which is NOT
 *     closure and breaks an accumulated run).
 *  3. Release hysteresis: drowsiness/alert clears only once no confidently
 *     closed frame has been seen for [DrowsinessConfig.releaseDurationMs]
 *     (time, not frames) — blinking while drowsy never clears the state, but
 *     squinting or looking around does.
 *  4. NO_FACE is NOT drowsiness: inside [DrowsinessConfig.faceLostGraceMs]
 *     the machine FREEZES (keeps accumulated evidence, holds the current
 *     state, no UI flicker); beyond it, evidence is discarded and the state
 *     is lost. Sustained loss ([DrowsinessConfig.faceSustainedLostMs]) also
 *     silences any active alert — an empty seat must not alarm.
 *  5. Alerts latch for alertHoldMs and re-fire at most once per
 *     alertCooldownMs.
 */
class DrowsinessDetector(
    @Volatile var config: DrowsinessConfig = DrowsinessConfig(),
) {
    /** UI-facing state bands. */
    enum class State { AWAKE, EYES_CLOSING, DROWSY_ALERT, RECOVERING }

    private var state: State = State.AWAKE
    private var closedSinceMs: Long? = null

    /**
     * When a confidently closed frame was last seen. Release is measured from
     * here rather than from "openSince": frames inside the hysteresis band are
     * not closure, so a driver squinting or hovering between bands recovers
     * instead of holding an alert latched forever (which also blocked every
     * later alert).
     */
    private var lastClosedAtMs: Long? = null
    private var lastAlertFiredAtMs: Long? = null
    private var alertActive = false
    private var alertStartedAtMs: Long? = null
    private var alertFired = false
    private var recoveringUntilMs: Long? = null

    private var faceLostSinceMs: Long? = null
    private var faceSustainedLost = false
    private var lastFaceStatus = FaceTrackStatus.TRACKED

    /**
     * Advance the machine by one frame.
     * Call [tick] (with the same timestamp) afterwards to update latching.
     */
    fun update(verdict: FrameVerdict, nowMs: Long): Observation {
        if (!verdict.hasFace) {
            if (faceLostSinceMs == null) faceLostSinceMs = nowMs
            val lostFor = nowMs - faceLostSinceMs!!
            lastFaceStatus = when {
                lostFor >= config.faceSustainedLostMs || faceSustainedLost -> {
                    // Driver left: discard evidence, silence alert, mark lost.
                    if (!faceSustainedLost) {
                        faceSustainedLost = true
                        discardDrowsinessEvidence()
                        alertActive = false
                    }
                    FaceTrackStatus.LOST
                }
                lostFor >= config.faceLostGraceMs -> {
                    // Grace over: freeze turns into reset, but the alert
                    // itself stays latched (a short look-away during an
                    // alert must not drop the alarm).
                    discardDrowsinessEvidence()
                    FaceTrackStatus.LOST
                }
                else -> FaceTrackStatus.TEMPORARILY_LOST
            }
            return Observation.NO_FACE // evidence frozen or already discarded
        }

        // Face present again.
        if (faceLostSinceMs != null) {
            faceLostSinceMs = null
            faceSustainedLost = false
        }
        lastFaceStatus = FaceTrackStatus.TRACKED

        val observation = when {
            verdict.pClosed >= config.confidenceThreshold -> Observation.EYES_CLOSED
            verdict.pClosed <= config.openThreshold -> Observation.EYES_OPEN
            else -> Observation.UNCERTAIN
        }

        when (observation) {
            Observation.EYES_CLOSED -> {
                lastClosedAtMs = nowMs
                if (closedSinceMs == null) closedSinceMs = nowMs
            }

            Observation.EYES_OPEN, Observation.UNCERTAIN -> {
                // Only a confidently closed frame keeps a run alive. A frame in
                // the hysteresis band is not closure: a half lid, a squint or
                // one bad eye crop must not extend an accumulated run, which
                // is what turned partial blinks into false alarms.
                closedSinceMs = null
            }

            Observation.NO_FACE, Observation.CLASSIFIER_FAILED -> Unit
        }
        return observation
    }

    /**
     * Advance alert lifecycle / latching. Must be called after [update]
     * with the same logical timestamp.
     */
    fun tick(nowMs: Long) {
        // Fire alert?
        if (!alertActive && closedSinceMs != null &&
            nowMs - closedSinceMs!! >= config.closedDurationMs
        ) {
            val canFire = lastAlertFiredAtMs == null ||
                nowMs - lastAlertFiredAtMs!! >= config.alertCooldownMs
            if (canFire) {
                alertActive = true
                alertStartedAtMs = nowMs
                lastAlertFiredAtMs = nowMs
                alertFired = true
            }
        }

        // Release alert? Requires alertHoldMs to have passed AND no
        // confidently closed frame for releaseDurationMs (blink-proof, but
        // squinting or looking around does let go).
        if (alertActive) {
            val startedAt = alertStartedAtMs
            val holdOk = startedAt != null && nowMs - startedAt >= config.alertHoldMs
            val lastClosed = lastClosedAtMs
            val notClosedRecently = lastClosed == null ||
                nowMs - lastClosed >= config.releaseDurationMs
            if (holdOk && notClosedRecently) {
                alertActive = false
                recoveringUntilMs = nowMs + config.recoveringBandMs
            }
        }

        // Clear the RECOVERING band once stabilized.
        recoveringUntilMs = recoveringUntilMs?.takeIf { nowMs < it }

        // UI band selection.
        state = when {
            alertActive -> State.DROWSY_ALERT
            closedSinceMs != null -> State.EYES_CLOSING
            recoveringUntilMs != null -> State.RECOVERING
            else -> State.AWAKE
        }
    }

    private fun discardDrowsinessEvidence() {
        closedSinceMs = null
        lastClosedAtMs = null
        recoveringUntilMs = null
    }

    /** True exactly once per fired alert; the orchestrator polls and clears it. */
    fun pollAlertFired(): Boolean {
        val fired = alertFired
        alertFired = false
        return fired
    }

    fun isAlertActive(): Boolean = alertActive

    fun currentState(): State = state

    fun faceStatus(): FaceTrackStatus = lastFaceStatus

    fun closedDurationMs(nowMs: Long): Long = closedSinceMs?.let { nowMs - it } ?: 0L

    fun reset() {
        state = State.AWAKE
        closedSinceMs = null
        lastClosedAtMs = null
        lastAlertFiredAtMs = null
        alertActive = false
        alertStartedAtMs = null
        alertFired = false
        recoveringUntilMs = null
        faceLostSinceMs = null
        faceSustainedLost = false
        lastFaceStatus = FaceTrackStatus.TRACKED
    }
}
