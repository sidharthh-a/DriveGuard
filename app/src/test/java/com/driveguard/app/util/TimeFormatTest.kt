package com.driveguard.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeFormatTest {

    @Test
    fun `formats under one hour as mm_ss`() {
        assertThat(TimeFormat.duration(0)).isEqualTo("00:00")
        assertThat(TimeFormat.duration(65_000)).isEqualTo("01:05")
        assertThat(TimeFormat.duration(3_540_000)).isEqualTo("59:00")
    }

    @Test
    fun `formats one hour and above as h_mm_ss`() {
        assertThat(TimeFormat.duration(3_600_000)).isEqualTo("1:00:00")
        assertThat(TimeFormat.duration(3_725_000)).isEqualTo("1:02:05")
    }

    @Test
    fun `human readable duration`() {
        assertThat(TimeFormat.durationHuman(30_000)).isEqualTo("<1min")
        assertThat(TimeFormat.durationHuman(3 * 60_000)).isEqualTo("3min")
        assertThat(TimeFormat.durationHuman(60 * 60_000)).isEqualTo("1h")
        assertThat(TimeFormat.durationHuman(3_725_000)).isEqualTo("1h 2min")
    }
}
