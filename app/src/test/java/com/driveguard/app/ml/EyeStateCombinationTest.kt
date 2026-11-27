package com.driveguard.app.ml

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * One-eye evidence must never stand in for a microsleep — taking the worse eye
 * outright is what raised alarms while the driver's eyes were still open.
 */
class EyeStateCombinationTest {

    @Test
    fun `reference openness cutoff maps to the medium threshold`() {
        // The validated reference rule is "average eye openness < 0.40 = closed".
        // The pipeline reports 1 - openness per eye, so that rule must land
        // exactly on the MEDIUM preset's closed threshold.
        val config = com.driveguard.app.detection.Sensitivity.MEDIUM.toConfig()

        val partlyClosed = EyeStateClassifier.combineEyes(1f - 0.35f, 1f - 0.32f)
        assertThat(partlyClosed).isAtLeast(config.confidenceThreshold)

        val clearlyOpen = EyeStateClassifier.combineEyes(1f - 0.55f, 1f - 0.50f)
        assertThat(clearlyOpen).isLessThan(config.confidenceThreshold)
    }

    @Test
    fun `agreeing closed eyes report the worse eye`() {
        assertThat(EyeStateClassifier.combineEyes(0.95f, 0.90f)).isEqualTo(0.95f)
    }

    @Test
    fun `one closed eye against one open eye is not closure`() {
        val p = EyeStateClassifier.combineEyes(0.95f, 0.02f)
        assertThat(p).isGreaterThan(0.30f) // above "open"
        assertThat(p).isLessThan(0.50f) // below every "closed" threshold
    }

    @Test
    fun `one unclassified eye falls back to the eye that was classified`() {
        assertThat(EyeStateClassifier.combineEyes(null, 0.93f)).isEqualTo(0.93f)
        assertThat(EyeStateClassifier.combineEyes(0.04f, null)).isEqualTo(0.04f)
        assertThat(EyeStateClassifier.combineEyes(null, null)).isEqualTo(0f)
    }

    @Test
    fun `both eyes open stays open`() {
        assertThat(EyeStateClassifier.combineEyes(0.05f, 0.03f)).isEqualTo(0.05f)
    }
}
