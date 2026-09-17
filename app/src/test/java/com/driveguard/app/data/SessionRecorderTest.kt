package com.driveguard.app.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionRecorderTest {

    @Test
    fun `clean session has zero alerts and interruptions`() {
        val r = SessionRecorder()
        r.start(0)
        r.end(60_000)
        val s = r.build(0, 60_000)
        assertThat(s.durationMs).isEqualTo(60_000)
        assertThat(s.alertCount).isEqualTo(0)
        assertThat(s.monitoringInterruptions).isEqualTo(0)
    }

    @Test
    fun `short face loss is not an interruption`() {
        val r = SessionRecorder()
        r.start(0)
        r.onFaceLostStart(1_000)
        r.onFaceFound(2_000) // 1 s < 1.5 s episode threshold
        r.end(3_000)
        val s = r.build(0, 3_000)
        assertThat(s.monitoringInterruptions).isEqualTo(0)
    }

    @Test
    fun `long face loss counts as one interruption`() {
        val r = SessionRecorder()
        r.start(0)
        r.onFaceLostStart(1_000)
        r.onFaceFound(3_000) // 2 s >= 1.5 s
        r.end(4_000)
        val s = r.build(0, 4_000)
        assertThat(s.monitoringInterruptions).isEqualTo(1)
    }

    @Test
    fun `alerts are counted`() {
        val r = SessionRecorder()
        r.start(0)
        repeat(3) { r.onAlert() }
        r.end(10_000)
        val s = r.build(0, 10_000)
        assertThat(s.alertCount).isEqualTo(3)
    }

    @Test
    fun `inference latency averages correctly`() {
        val r = SessionRecorder()
        r.start(0)
        r.onInferenceSample(10)
        r.onInferenceSample(20)
        r.onInferenceSample(30)
        r.end(1_000)
        val s = r.build(0, 1_000)
        assertThat(s.avgInferenceMs).isWithin(0.01f).of(20f)
    }

    @Test
    fun `no inference samples yields zero not NaN`() {
        val r = SessionRecorder()
        r.start(0)
        r.end(1_000)
        val s = r.build(0, 1_000)
        assertThat(s.avgInferenceMs).isEqualTo(0f)
    }
}
