package com.driveguard.app.alerts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlertEngineTest {

    @Test
    fun `first episode emits level 1`() {
        val engine = AlertEngine()
        val event = engine.onDrowsinessStart(0)
        assertThat(event).isNotNull()
        assertThat(event!!.level).isEqualTo(AlertLevel.LEVEL_1)
    }

    @Test
    fun `episode within cooldown is suppressed`() {
        val engine = AlertEngine(cooldownMs = 8_000)
        engine.onDrowsinessStart(0)
        val second = engine.onDrowsinessStart(1_000)
        assertThat(second).isNull()
    }

    @Test
    fun `episode after cooldown escalates to level 2`() {
        val engine = AlertEngine(cooldownMs = 8_000)
        engine.onDrowsinessStart(0)
        engine.onDrowsinessEnd(1_000)
        val second = engine.onDrowsinessStart(9_000)
        assertThat(second).isNotNull()
        assertThat(second!!.level).isEqualTo(AlertLevel.LEVEL_2)
    }

    @Test
    fun `third episode escalates to level 3 and stays there`() {
        val engine = AlertEngine(cooldownMs = 1_000)
        engine.onDrowsinessStart(0)
        engine.onDrowsinessEnd(500)
        engine.onDrowsinessStart(2_000)
        engine.onDrowsinessEnd(2_500)
        val third = engine.onDrowsinessStart(4_000)
        assertThat(third!!.level).isEqualTo(AlertLevel.LEVEL_3)

        engine.onDrowsinessEnd(4_500)
        val fourth = engine.onDrowsinessStart(6_000)
        assertThat(fourth!!.level).isEqualTo(AlertLevel.LEVEL_3)
    }

    @Test
    fun `long clean period resets escalation`() {
        val engine = AlertEngine(cooldownMs = 1_000, escalationResetMs = 10_000)
        engine.onDrowsinessStart(0)
        engine.onDrowsinessEnd(500)
        engine.onDrowsinessStart(2_000) // level 2
        engine.onDrowsinessEnd(2_500)
        val after = engine.onDrowsinessStart(60_000) // long clean period
        assertThat(after!!.level).isEqualTo(AlertLevel.LEVEL_1)
    }

    @Test
    fun `reset returns to initial state`() {
        val engine = AlertEngine()
        engine.onDrowsinessStart(0)
        engine.reset()
        val event = engine.onDrowsinessStart(1)
        assertThat(event!!.level).isEqualTo(AlertLevel.LEVEL_1)
    }
}
