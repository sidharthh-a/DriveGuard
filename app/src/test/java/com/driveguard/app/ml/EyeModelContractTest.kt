package com.driveguard.app.ml

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards the contract constants against accidental drift. The actual tensor
 * shapes are verified on-device by ModelInstrumentedTest.
 */
class EyeModelContractTest {

    @Test
    fun `input geometry matches forensics`() {
        assertThat(EyeModelContract.INPUT_WIDTH).isEqualTo(64)
        assertThat(EyeModelContract.INPUT_HEIGHT).isEqualTo(64)
        assertThat(EyeModelContract.INPUT_CHANNELS).isEqualTo(3)
        assertThat(EyeModelContract.ELEMENTS_PER_FRAME).isEqualTo(64 * 64 * 3)
    }

    @Test
    fun `output is a single probability`() {
        assertThat(EyeModelContract.OUTPUT_SIZE).isEqualTo(1)
    }

    @Test
    fun `polarity is closed-positive`() {
        // Verified empirically (docs/MODEL_CONTRACT.md §5). If this constant is
        // ever changed without new evidence, that is a contract break.
        assertThat(EyeModelContract.OUTPUT_IS_P_CLOSED).isTrue()
    }
}
