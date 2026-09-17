package com.driveguard.app.detection

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.driveguard.app.alerts.AlertEngine
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration-style tests of the full decision pipeline using synthetic
 * frame sequences — no camera required. These validate the exact code path
 * the analyzer feeds at runtime (detector update/tick + alert engine).
 */
@RunWith(AndroidJUnit4::class)
class DetectionFlowInstrumentedTest {

    @Test
    fun sustainedClosedEyesFireAlertAndEscalatingAlertEngineEmits() {
        val detector = DrowsinessDetector()
        val alerts = AlertEngine()
        var emitted: com.driveguard.app.alerts.AlertEvent? = null

        var t = 0L
        var fired = false
        while (t < 4_000) {
            detector.update(FrameVerdict(hasFace = true, pClosed = 0.99f), t)
            detector.tick(t)
            if (!fired && detector.pollAlertFired()) {
                fired = true
                emitted = alerts.onDrowsinessStart(t)
            }
            t += 33
        }
        assertThat(fired).isTrue()
        assertThat(emitted).isNotNull()
        assertThat(emitted!!.level).isEqualTo(com.driveguard.app.alerts.AlertLevel.LEVEL_1)
    }

    @Test
    fun intermittentClosureDoesNotReachAlert() {
        val detector = DrowsinessDetector()
        // Alternate closed/open every 500 ms — never 2 s continuous (and
        // never 700 ms of continuous openness, so no release flapping either).
        var t = 0L
        var anyFire = false
        while (t < 20_000) {
            val closed = (t / 500L) % 2L == 0L
            repeat(15) { // 500 ms at 30fps
                detector.update(
                    FrameVerdict(hasFace = true, pClosed = if (closed) 0.99f else 0.01f),
                    t,
                )
                detector.tick(t)
                if (detector.pollAlertFired()) anyFire = true
                t += 33
            }
        }
        assertThat(anyFire).isFalse()
    }

    @Test
    fun noFaceGapsDoNotTriggerAlerts() {
        val detector = DrowsinessDetector()
        var t = 0L
        var anyFire = false
        while (t < 10_000L) {
            // no face entire session
            detector.update(FrameVerdict(hasFace = false, pClosed = 0f), t)
            detector.tick(t)
            if (detector.pollAlertFired()) anyFire = true
            t += 33
        }
        assertThat(anyFire).isFalse()
        assertThat(detector.currentState()).isEqualTo(DrowsinessDetector.State.AWAKE)
    }

    @Test
    fun recoveryFlowCompletes() {
        val detector = DrowsinessDetector()
        var t = 0L
        // trigger alert (2 s default start threshold)
        while (t < 2_200) {
            detector.update(FrameVerdict(true, 0.99f), t)
            detector.tick(t)
            detector.pollAlertFired()
            t += 33
        }
        assertThat(detector.isAlertActive()).isTrue()

        // open eyes, wait past the 2 s hold
        t += 2_500
        // sustain openness past the 700 ms release threshold (~1 s of frames)
        repeat(30) {
            detector.update(FrameVerdict(true, 0.01f), t)
            detector.tick(t)
            t += 33
        }
        assertThat(detector.isAlertActive()).isFalse()
        assertThat(detector.currentState()).isAnyOf(
            DrowsinessDetector.State.RECOVERING,
            DrowsinessDetector.State.AWAKE,
        )
    }

    @Test
    fun briefFaceLossInsideGraceWindowDoesNotResetEvidence() {
        val detector = DrowsinessDetector()
        var t = 0L
        // 1 s of closure
        while (t < 1_000) {
            detector.update(FrameVerdict(true, 0.99f), t)
            detector.tick(t)
            t += 33
        }
        // face lost 1.5 s (< 2 s grace): evidence must freeze, not reset
        while (t < 2_500) {
            detector.update(FrameVerdict(false, 0f), t)
            detector.tick(t)
            t += 33
        }
        // face returns, eyes still closed → alert fires (1 s + frozen gap ≥ 2 s)
        var fired = false
        while (t < 4_000 && !fired) {
            detector.update(FrameVerdict(true, 0.99f), t)
            detector.tick(t)
            fired = detector.pollAlertFired()
            t += 33
        }
        assertThat(fired).isTrue()
    }

    @Test
    fun sustainedFaceLossSilencesActiveAlert() {
        val detector = DrowsinessDetector()
        var t = 0L
        while (t < 2_200) {
            detector.update(FrameVerdict(true, 0.99f), t)
            detector.tick(t)
            t += 33
        }
        assertThat(detector.isAlertActive()).isTrue()
        // driver leaves the frame (> 5 s sustained loss)
        while (t < 8_000) {
            detector.update(FrameVerdict(false, 0f), t)
            detector.tick(t)
            t += 33
        }
        assertThat(detector.isAlertActive()).isFalse()
        assertThat(detector.currentState()).isEqualTo(DrowsinessDetector.State.AWAKE)
    }
}
