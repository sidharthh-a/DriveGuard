package com.driveguard.app.ml

import kotlin.math.max
import kotlin.math.min

/**
 * Agreement-aware combination of the two per-eye P(closed) values ML Kit
 * reports for a face.
 *
 * This is all that is left of the former bundled-model wrapper: eye state now
 * comes entirely from ML Kit's own openness signal, so no interpreter, asset or
 * tensor preprocessing exists behind it.
 */
object EyeStateClassifier {

    /**
     * Per-eye disagreement above which the two eyes are not describing the
     * same eye state (one reading is occluded, mis-tracked or squinting).
     */
    private const val EYE_AGREEMENT_MARGIN = 0.35f

    /**
     * Combines the two per-eye probabilities into the frame's P(closed).
     *
     * Taking the max outright made one unusable reading (hair, a hand, a
     * reflection, a single lid caught half-down) read as a microsleep while the
     * other eye was plainly open — the main source of false alarms. The worse
     * eye only speaks for the frame when both eyes agree; otherwise the average
     * lands in the hysteresis band, so no closure is recorded from a one-eyed
     * frame.
     *
     * A missing eye falls back to the eye that was reported.
     */
    fun combineEyes(pLeft: Float?, pRight: Float?): Float {
        if (pLeft == null) return pRight ?: 0f
        if (pRight == null) return pLeft
        val worse = max(pLeft, pRight)
        val better = min(pLeft, pRight)
        return if (worse - better <= EYE_AGREEMENT_MARGIN) worse else (worse + better) / 2f
    }
}
