package com.driveguard.app.detection

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SensitivityTest {

    @Test
    fun `medium uses the 2s default from DetectionTuning`() {
        val cfg = Sensitivity.MEDIUM.toConfig()
        assertThat(cfg.closedDurationMs).isEqualTo(DetectionTuning.DROWSY_START_THRESHOLD_MS)
        assertThat(cfg.closedDurationMs).isEqualTo(2_000)
        assertThat(cfg.confidenceThreshold).isEqualTo(0.70f)
    }

    @Test
    fun `high sensitivity shortens duration and lowers threshold`() {
        val cfg = Sensitivity.HIGH.toConfig()
        assertThat(cfg.closedDurationMs).isLessThan(Sensitivity.MEDIUM.toConfig().closedDurationMs)
        assertThat(cfg.confidenceThreshold).isLessThan(Sensitivity.MEDIUM.toConfig().confidenceThreshold)
    }

    @Test
    fun `low sensitivity lengthens duration and raises threshold`() {
        val cfg = Sensitivity.LOW.toConfig()
        assertThat(cfg.closedDurationMs).isGreaterThan(Sensitivity.MEDIUM.toConfig().closedDurationMs)
        assertThat(cfg.confidenceThreshold).isGreaterThan(Sensitivity.MEDIUM.toConfig().confidenceThreshold)
    }

    @Test
    fun `all presets keep a valid hysteresis band`() {
        Sensitivity.entries.forEach { s ->
            val cfg = s.toConfig()
            assertThat(cfg.openThreshold).isLessThan(cfg.confidenceThreshold)
        }
    }

    @Test
    fun `every preset stays blink-safe - at least 3x the longest blink`() {
        // Longest voluntary blink ≈ 500 ms; require >= 3x margin.
        val longestBlinkMs = 500L
        Sensitivity.entries.forEach { s ->
            val cfg = s.toConfig()
            assertThat(cfg.closedDurationMs).isAtLeast(3 * longestBlinkMs)
        }
    }

    @Test
    fun `face grace windows are independent of sensitivity presets`() {
        // Presets must not touch face-tracking windows.
        val base = DrowsinessConfig()
        Sensitivity.entries.forEach { s ->
            val cfg = s.toConfig()
            assertThat(cfg.faceLostGraceMs).isEqualTo(base.faceLostGraceMs)
            assertThat(cfg.faceSustainedLostMs).isEqualTo(base.faceSustainedLostMs)
        }
    }

    @Test
    fun `fromName falls back to medium on unknown`() {
        assertThat(Sensitivity.fromName(null)).isEqualTo(Sensitivity.MEDIUM)
        assertThat(Sensitivity.fromName("BOGUS")).isEqualTo(Sensitivity.MEDIUM)
        assertThat(Sensitivity.fromName("HIGH")).isEqualTo(Sensitivity.HIGH)
    }
}
