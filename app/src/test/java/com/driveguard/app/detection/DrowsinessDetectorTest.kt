package com.driveguard.app.detection

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Deterministic tests for the temporal decision engine. The detector is pure:
 * every timestamp is provided by the test, so no real time is involved.
 *
 * Frame cadence: 33 ms (~30 fps analysis), matching the analyzer budget.
 */
class DrowsinessDetectorTest {

    private lateinit var detector: DrowsinessDetector

    @Before
    fun setUp() {
        detector = DrowsinessDetector()
    }

    private fun closedFrame(p: Float = 0.99f) = FrameVerdict(hasFace = true, pClosed = p)
    private fun openFrame(p: Float = 0.01f) = FrameVerdict(hasFace = true, pClosed = p)

    private fun runClosed(fromMs: Long, untilMs: Long, stepMs: Long = 33): Long {
        var t = fromMs
        while (t < untilMs) {
            detector.update(closedFrame(), t)
            detector.tick(t)
            t += stepMs
        }
        return t
    }

    private fun runOpen(fromMs: Long, untilMs: Long, stepMs: Long = 33): Long {
        var t = fromMs
        while (t < untilMs) {
            detector.update(openFrame(), t)
            detector.tick(t)
            t += stepMs
        }
        return t
    }

    // --- basic classification -------------------------------------------------

    @Test
    fun `single closed frame does not fire alert`() {
        detector.update(closedFrame(), 0)
        detector.tick(0)
        assertThat(detector.pollAlertFired()).isFalse()
        assertThat(detector.currentState()).isEqualTo(DrowsinessDetector.State.EYES_CLOSING)
    }

    @Test
    fun `continuous closure below threshold duration does not fire`() {
        // 1.9 s at 30 fps — just under the 2 s default
        runClosed(0, 1_900)
        assertThat(detector.pollAlertFired()).isFalse()
    }

    @Test
    fun `two seconds of closure fires alert exactly once`() {
        var t = 0L
        var firedAt: Long? = null
        while (t <= 2_200) {
            detector.update(closedFrame(), t)
            detector.tick(t)
            if (firedAt == null && detector.pollAlertFired()) firedAt = t
            t += 33
        }
        assertThat(firedAt).isNotNull()
        assertThat(firedAt).isAtLeast(1_900)
        assertThat(firedAt).isAtMost(2_200)
        // no second fire while still closed (cooldown)
        var secondFire = false
        while (t <= 5_000) {
            detector.update(closedFrame(), t)
            detector.tick(t)
            if (detector.pollAlertFired()) secondFire = true
            t += 33
        }
        assertThat(secondFire).isFalse()
    }

    // --- blink safety -----------------------------------------------------------

    @Test
    fun `normal blink never fires alert`() {
        // Blink pattern: 150 ms closed every 2 s, over 60 s.
        var t = 0L
        var anyFire = false
        while (t < 60_000) {
            val closed = (t % 2_000) < 150
            detector.update(
                FrameVerdict(hasFace = true, pClosed = if (closed) 0.99f else 0.01f),
                t,
            )
            detector.tick(t)
            if (detector.pollAlertFired()) anyFire = true
            t += 33
        }
        assertThat(anyFire).isFalse()
        assertThat(detector.currentState()).isEqualTo(DrowsinessDetector.State.AWAKE)
    }

    @Test
    fun `slow blink never fires alert`() {
        // Even an exaggerated 500 ms blink every 3 s is far below 2 s.
        var t = 0L
        var anyFire = false
        while (t < 60_000) {
            val closed = (t % 3_000) < 500
            detector.update(
                FrameVerdict(hasFace = true, pClosed = if (closed) 0.99f else 0.01f),
                t,
            )
            detector.tick(t)
            if (detector.pollAlertFired()) anyFire = true
            t += 33
        }
        assertThat(anyFire).isFalse()
    }

    // --- release hysteresis ------------------------------------------------------

    @Test
    fun `blink during alert does not clear it before hold expires`() {
        var t = runClosed(0, 2_100)
        assertThat(detector.isAlertActive()).isTrue()
        // eyes open again after 1 s (well under release 700 ms? no — 1 s open
        // exceeds release, but alert hold is 2 s): still latched.
        t = runOpen(t, t + 1_000)
        assertThat(detector.isAlertActive()).isTrue()
        // Keep eyes open past hold + release: alert clears.
        t = runOpen(t, t + 2_500)
        assertThat(detector.isAlertActive()).isFalse()
    }

    @Test
    fun `eyes closing again before release does not flap`() {
        var t = runClosed(0, 2_100)
        assertThat(detector.pollAlertFired()).isTrue() // first fire, drained
        assertThat(detector.isAlertActive()).isTrue()
        // blink open for 300 ms (< release 700 ms), then closed again:
        // alert must remain active continuously — no second fire, no oscillation.
        t = runOpen(t, t + 300)
        t = runClosed(t, t + 2_000)
        assertThat(detector.isAlertActive()).isTrue()
        assertThat(detector.pollAlertFired()).isFalse() // no re-fire during flap
    }

    @Test
    fun `recovery requires sustained openness not a single frame`() {
        var t = runClosed(0, 2_100)
        // single open frame then closed: no clear
        t = runOpen(t, t + 33)
        t = runClosed(t, t + 1_000)
        assertThat(detector.isAlertActive()).isTrue()
        // sustained openness clears it
        t = runOpen(t, t + 3_000)
        assertThat(detector.isAlertActive()).isFalse()
    }

    // --- face loss ---------------------------------------------------------------

    @Test
    fun `no face never fires alert and preserves no drowsiness state`() {
        var t = 0L
        repeat(200) {
            detector.update(FrameVerdict(false, 0f), t)
            detector.tick(t)
            assertThat(detector.pollAlertFired()).isFalse()
            t += 33
        }
        assertThat(detector.currentState()).isEqualTo(DrowsinessDetector.State.AWAKE)
    }

    @Test
    fun `brief face loss inside grace window freezes evidence instead of resetting`() {
        // 1 s of closure accumulated
        var t = runClosed(0, 1_000)
        assertThat(detector.closedDurationMs(t)).isAtLeast(900)
        // face lost for 1.5 s (< 2 s grace)
        var lost = t
        while (lost < t + 1_500) {
            detector.update(FrameVerdict(false, 0f), lost)
            detector.tick(lost)
            assertThat(detector.faceStatus()).isEqualTo(FaceTrackStatus.TEMPORARILY_LOST)
            lost += 33
        }
        // face returns — the 1 s of closure evidence must still be there
        detector.update(closedFrame(), lost)
        detector.tick(lost)
        // grace froze evidence: total closed duration ≈ 1 s + grace (frozen),
        // i.e. at least the original 0.9 s, and alert fires shortly after
        // (frozen wall-clock means closure continued during the gap).
        assertThat(detector.closedDurationMs(lost)).isAtLeast(2_000)
    }

    @Test
    fun `face loss beyond grace window resets evidence`() {
        var t = runClosed(0, 1_000)
        // face lost for 2.5 s (> 2 s grace)
        var lost = t
        while (lost < t + 2_500) {
            detector.update(FrameVerdict(false, 0f), lost)
            detector.tick(lost)
            lost += 33
        }
        assertThat(detector.faceStatus()).isEqualTo(FaceTrackStatus.LOST)
        // face returns: evidence was discarded (duration restarts at zero)
        detector.update(closedFrame(), lost)
        detector.tick(lost)
        assertThat(detector.closedDurationMs(lost)).isEqualTo(0)
    }

    @Test
    fun `sustained face loss silences an active alert`() {
        var t = runClosed(0, 2_100)
        assertThat(detector.isAlertActive()).isTrue()
        // driver leaves the frame for > 5 s
        var lost = t
        while (lost < t + 5_200) {
            detector.update(FrameVerdict(false, 0f), lost)
            detector.tick(lost)
            lost += 33
        }
        assertThat(detector.isAlertActive()).isFalse()
        assertThat(detector.faceStatus()).isEqualTo(FaceTrackStatus.LOST)
    }

    @Test
    fun `face return after sustained loss resumes monitoring cleanly`() {
        var t = runClosed(0, 2_100)
        var lost = t
        while (lost < t + 5_200) {
            detector.update(FrameVerdict(false, 0f), lost)
            detector.tick(lost)
            lost += 33
        }
        // face returns with open eyes
        var t2 = lost
        t2 = runOpen(t2, t2 + 1_000)
        assertThat(detector.faceStatus()).isEqualTo(FaceTrackStatus.TRACKED)
        assertThat(detector.currentState()).isEqualTo(DrowsinessDetector.State.AWAKE)
        assertThat(detector.isAlertActive()).isFalse()
    }

    // --- hysteresis (confidence band) ---------------------------------------------

    @Test
    fun `uncertain band does not accumulate evidence`() {
        detector.update(FrameVerdict(true, 0.5f), 0)
        detector.tick(0)
        assertThat(detector.currentState()).isEqualTo(DrowsinessDetector.State.AWAKE)
        assertThat(detector.closedDurationMs(1_000)).isEqualTo(0)
    }

    @Test
    fun `hysteresis boundary values classify correctly`() {
        val d = DrowsinessDetector(DrowsinessConfig(confidenceThreshold = 0.7f, openThreshold = 0.3f))
        d.update(FrameVerdict(true, 0.70f), 0)
        d.tick(0)
        assertThat(d.currentState()).isEqualTo(DrowsinessDetector.State.EYES_CLOSING)

        d.update(FrameVerdict(true, 0.30f), 100)
        d.tick(100)
        assertThat(d.currentState()).isEqualTo(DrowsinessDetector.State.AWAKE)
    }

    @Test
    fun `uncertain frames break an accumulated closure run`() {
        // Regression: partial closure (half-shut lids) used to FREEZE the
        // closure timer for 500 ms, so 1.2 s closed + 0.3 s half-closed +
        // 0.9 s closed still fired. Now only confident closure counts.
        var t = runClosed(0, 1_200)
        var u = t
        while (u < t + 300) {
            detector.update(FrameVerdict(true, 0.5f), u)
            detector.tick(u)
            u += 33
        }
        assertThat(detector.closedDurationMs(u)).isEqualTo(0)
        runClosed(u, u + 900)
        assertThat(detector.pollAlertFired()).isFalse()
    }

    @Test
    fun `alert releases while the eyes hover in the hysteresis band`() {
        // Regression: release used to require confidently OPEN frames, so a
        // squinting driver kept the alert latched forever — and a latched
        // alert blocks every later firing, which is why the alarm appeared to
        // work exactly once.
        var t = runClosed(0, 2_100)
        assertThat(detector.pollAlertFired()).isTrue()
        assertThat(detector.isAlertActive()).isTrue()

        var u = t
        while (u < t + 6_000) {
            detector.update(FrameVerdict(true, 0.5f), u) // half-closed, not open
            detector.tick(u)
            u += 33
        }
        assertThat(detector.isAlertActive()).isFalse()

        // And a fresh episode alerts again once the cooldown has elapsed.
        var t2 = u
        t2 = runClosed(t2, t2 + 2_100)
        assertThat(detector.pollAlertFired()).isTrue()
    }

    // --- cooldown -----------------------------------------------------------------

    @Test
    fun `second episode after cooldown can fire again`() {
        var t = 0L
        // first episode fires at ~2.0 s
        t = runClosed(t, 2_100)
        assertThat(detector.pollAlertFired()).isTrue()
        // recover
        t = runOpen(t, 6_000)
        t = runOpen(t, 10_100) // cooldown 8 s from first fire elapses
        // second episode
        t = runClosed(t, t + 2_100)
        assertThat(detector.isAlertActive()).isTrue()
    }

    @Test
    fun `second episode within cooldown is suppressed until cooldown elapses`() {
        var t = 0L
        t = runClosed(t, 2_100)
        assertThat(detector.pollAlertFired()).isTrue()
        t = runOpen(t, 5_000)   // recover at 5 s (cooldown from 2 s ends at 10 s)
        t = runClosed(t, 11_500) // keep eyes closed: fires as soon as cooldown ends
        assertThat(detector.isAlertActive()).isTrue()
    }

    // --- reset --------------------------------------------------------------------

    @Test
    fun `reset clears all evidence`() {
        detector.update(closedFrame(), 0)
        detector.tick(0)
        detector.reset()
        assertThat(detector.currentState()).isEqualTo(DrowsinessDetector.State.AWAKE)
        assertThat(detector.isAlertActive()).isFalse()
        assertThat(detector.closedDurationMs(1_000)).isEqualTo(0)
        assertThat(detector.faceStatus()).isEqualTo(FaceTrackStatus.TRACKED)
    }

    // --- config validation ----------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `config requires open threshold below closed threshold`() {
        DrowsinessConfig(confidenceThreshold = 0.3f, openThreshold = 0.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `config requires sustained loss at least grace`() {
        DrowsinessConfig(faceLostGraceMs = 5_000, faceSustainedLostMs = 2_000)
    }

    // --- sensitivity integration ------------------------------------------------------

    @Test
    fun `high sensitivity alert threshold stays blink-safe`() {
        val d = DrowsinessDetector(Sensitivity.HIGH.toConfig())
        // 1.4 s of closure must NOT fire
        var t = 0L
        while (t < 1_400) {
            d.update(closedFrame(), t)
            d.tick(t)
            t += 33
        }
        assertThat(d.pollAlertFired()).isFalse()
        // 1.6 s must fire
        while (t < 1_700) {
            d.update(closedFrame(), t)
            d.tick(t)
            t += 33
        }
        assertThat(d.pollAlertFired()).isTrue()
    }
}
