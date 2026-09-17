package com.driveguard.app.detection

/**
 * Central tuning constants for the whole detection pipeline.
 *
 * Every temporal value here is injected into [DrowsinessConfig] / the frame
 * analyzer so no magic numbers are scattered through the code. Rationale for
 * each value is documented inline; the defaults are evidence-based, not
 * arbitrary (see docs/MODEL_CONTRACT.md §6 for the model-side calibration).
 */
object DetectionTuning {

    // ------------------------------------------------------------------ face

    /**
     * ML Kit classification confidence. 0.5 is the detector's own default and
     * the documented "balanced" value — deliberately NOT lowered to chase
     * recall: below ~0.4 ML Kit returns unstable boxes that corrupt eye crops.
     * Robustness to missed frames comes from tracking + a grace window
     * ([FACE_LOST_GRACE_MS]), not from a low threshold.
     */
    const val FACE_DETECTION_CONFIDENCE = 0.50f

    /**
     * ML Kit minimum face proportion of the frame short side. Slightly widened
     * from 0.15 → 0.12: at typical front-camera FOV a driver at arm's length
     * measures ~0.13–0.20, so 0.15 intermittently rejected perfectly usable
     * faces. 0.12 still excludes background bystanders.
     */
    const val MLKIT_MIN_FACE_SIZE = 0.12f

    /** Face shorter than this many pixels is "too far away" for reliable eye crops. */
    const val MIN_FACE_PX = 96

    /**
     * Consecutive missing-face detections tolerated by the analyzer-level
     * tracker before it drops the tracked box. At the ~15–30 Hz analysis
     * cadence this holds a face through 1–2 dropped detections; anything
     * longer hands over to the time-based grace window below.
     */
    const val TRACK_MAX_MISSES = 2

    /**
     * FACE_LOST_GRACE_MS — when the face disappears (occlusion, quick head
     * turn, detector hiccup) the decision engine FREEZES all drowsiness
     * evidence for this long instead of resetting it. One or a few missed
     * frames therefore never clear accumulated closure or flip the UI to
     * "Face not detected". Chosen ≈ 3× the longest normal blink: long enough
     * to cover detector gaps, short enough that a genuinely departed driver
     * is flagged quickly.
     */
    const val FACE_LOST_GRACE_MS = 2_000L

    /**
     * Face absence longer than this is treated as "driver left the frame":
     * all closure evidence is discarded and any active alert is silenced
     * (no alarm should sound at an empty seat). Must be > [FACE_LOST_GRACE_MS].
     */
    const val FACE_SUSTAINED_LOST_MS = 5_000L

    // ------------------------------------------------------------ drowsiness

    /**
     * DROWSY_START_THRESHOLD_MS — continuous eye closure before an alert
     * fires. Default 2 000 ms, reduced from the previous 3 000 ms:
     *
     *  - A voluntary blink lasts 100–400 ms (≈500 ms for a very slow one);
     *    2 s is a ≥4× margin, so normal blinking can never accumulate enough
     *    continuous closure to alert.
     *  - Drowsiness research (PERCLOS-lineage work, e.g. Wierwille & Ellsworth
     *    1994; EuroNCAP drowsiness protocols) treats closures of ~1–2 s and
     *    beyond as microsleeps; 2 s sits at the conservative end of that
     *    evidence, halving detection latency versus 3 s.
     *  - The value is exposed to the user through the sensitivity presets
     *    (HIGH 1.5 s / MEDIUM 2 s / LOW 3 s), never hard-coded at call sites.
     */
    const val DROWSY_START_THRESHOLD_MS = 2_000L

    /**
     * DROWSY_RELEASE_THRESHOLD_MS — eyes must remain *open* for this long
     * (time-based, not frame-count-based) before a drowsy state or latched
     * alert clears. 700 ms is well above the longest blink (~0.5 s), which is
     * exactly the hysteresis that stops DROWSY → NORMAL → DROWSY flapping:
     * blinking while drowsy does not clear the state, sustained wakefulness
     * does.
     */
    const val DROWSY_RELEASE_THRESHOLD_MS = 700L

    /**
     * Continuous UNCERTAIN observations (hysteresis band) decay accumulated
     * closure evidence after this long. Time-based rather than frame-counted
     * so behaviour is independent of the analysis frame rate.
     */
    const val UNCERTAIN_RESET_MS = 500L

    /** Post-alert stabilization band shown in the UI after an alert releases. */
    const val RECOVERING_BAND_MS = 2_000L

    // ---------------------------------------------------------------- alerts

    /** Minimum time an alert stays latched before it may clear. */
    const val ALERT_HOLD_MS = 2_000L

    /**
     * ALERT_COOLDOWN_MS — minimum spacing between two alert firings. With the
     * 2 s trigger this gives "alert → recover → still drowsy → re-alert at
     * most every 8 s", which escalates without becoming spam.
     */
    const val ALERT_COOLDOWN_MS = 8_000L

    /**
     * Desired analysis period (~15 Hz). NOT enforced as a frame gate: on
     * current hardware the full pipeline (YUV→RGB + face detection + 2 eye
     * classifications) already runs in 20–30 ms, and only one detection cycle
     * is ever in flight — surplus camera frames are dropped by
     * STRATEGY_KEEP_ONLY_LATEST instead of queueing. Kept as the documented
     * budget and for tests/tools.
     */
    const val ANALYSIS_INTERVAL_MS = 66L
}
