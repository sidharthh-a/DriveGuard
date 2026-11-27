package com.driveguard.app.detection

import com.driveguard.app.ml.EyeModelContract

/**
 * Tunables for [DrowsinessDetector]. All defaults come from
 * [DetectionTuning] (single source of truth, documented rationale there).
 *
 * Threshold semantics:
 *  - [closedDurationMs]   = DROWSY_START_THRESHOLD_MS  (closure → drowsy)
 *  - [releaseDurationMs]  = DROWSY_RELEASE_THRESHOLD_MS (openness → clear)
 *  - [faceLostGraceMs]    = FACE_LOST_GRACE_MS (freeze evidence, no reset)
 *  - [faceSustainedLostMs]= FACE_SUSTAINED_LOST_MS (driver left → full reset)
 *
 * The classic 3-second rule was reduced to 2 s: it keeps a ≥4× margin over
 * the longest voluntary blink (~0.5 s) while halving alert latency, and is
 * user-adjustable via [Sensitivity] (1.5 / 2 / 3 s). See DetectionTuning
 * for the full evidence trail.
 */
data class DrowsinessConfig(
    /**
     * P(closed) at or above which a frame is classified "eyes closed".
     *
     * This maps directly onto an eye-openness cutoff, because the pipeline
     * reports P(closed) = 1 − mean(ML Kit eye-open probability) for the two
     * eyes. 0.60 therefore means "closed when average openness drops below
     * 0.40" — exactly the rule that was validated frame-by-frame in the
     * reference prototype. The earlier 0.85 required average openness below
     * 0.15, which real closures in dim light or behind glasses often never
     * reach, so genuinely closed eyes went undetected while half-shut lids
     * still alarmed.
     */
    val confidenceThreshold: Float = 0.60f,

    /**
     * P(closed) at or below which a frame is confidently "eyes open", i.e.
     * average openness of at least 0.60. The band between openThreshold and
     * confidenceThreshold is hysteresis: not closure, and it breaks an
     * accumulated run.
     */
    val openThreshold: Float = 0.40f,

    /** DROWSY_START_THRESHOLD_MS: continuous closure before drowsiness. */
    val closedDurationMs: Long = DetectionTuning.DROWSY_START_THRESHOLD_MS,

    /**
     * DROWSY_RELEASE_THRESHOLD_MS: continuous *openness* before drowsy
     * evidence clears. Time-based (not frame-counted) so behaviour is
     * independent of the analysis frame rate, and long enough that normal
     * blinking while drowsy never clears the state.
     */
    val releaseDurationMs: Long = DetectionTuning.DROWSY_RELEASE_THRESHOLD_MS,

    /** How long the RECOVERING UI band persists after an alert releases. */
    val recoveringBandMs: Long = DetectionTuning.RECOVERING_BAND_MS,

    /** Minimum time an alert stays latched before it may clear. */
    val alertHoldMs: Long = DetectionTuning.ALERT_HOLD_MS,

    /** Minimum spacing between two alert firings (anti-spam). */
    val alertCooldownMs: Long = DetectionTuning.ALERT_COOLDOWN_MS,

    /** Face-loss grace: evidence freezes for this long, then resets. */
    val faceLostGraceMs: Long = DetectionTuning.FACE_LOST_GRACE_MS,

    /** Sustained face loss treated as "driver gone" (also silences alerts). */
    val faceSustainedLostMs: Long = DetectionTuning.FACE_SUSTAINED_LOST_MS,

    /**
     * Face box must be at least this fraction of the frame's short side for
     * crops to be considered reliable; smaller faces degrade to "too far".
     */
    val minFaceFraction: Float = 0.18f,

    /** Derived from the model contract; kept here to avoid magic numbers in tests. */
    val modelInputSize: Int = EyeModelContract.INPUT_WIDTH,
) {
    init {
        require(openThreshold < confidenceThreshold) {
            "openThreshold must be below confidenceThreshold (hysteresis band)"
        }
        require(closedDurationMs > 0 && alertHoldMs > 0 && alertCooldownMs > 0)
        require(releaseDurationMs > 0 && faceLostGraceMs > 0)
        require(faceSustainedLostMs >= faceLostGraceMs) {
            "sustained face loss must be at least the grace window"
        }
    }
}
