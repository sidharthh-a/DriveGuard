package com.driveguard.app.ml

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageTensorPreprocessorTest {

    private val W = EyeModelContract.INPUT_WIDTH
    private val H = EyeModelContract.INPUT_HEIGHT

    @Test
    fun `identity crop of exactly 64x64 preserves pixel values`() {
        val img = IntArray(W * H) { i ->
            val x = i % W
            (x shl 16) or ((255 - x) shl 8) or 0x40
        }
        val out = FloatArray(EyeModelContract.ELEMENTS_PER_FRAME)
        ImageTensorPreprocessor.fillTensor(img, W, H, 0, 0, W, H, out)

        // First pixel: R=x0, G=255, B=0x40
        assertThat(out[0]).isWithin(1e-6f).of(0f / 255f)
        assertThat(out[1]).isWithin(1e-6f).of(1f)
        assertThat(out[2]).isWithin(1e-6f).of(0x40 / 255f)
        // Second pixel: R=1
        assertThat(out[3]).isWithin(1e-6f).of(1f / 255f)
    }

    @Test
    fun `all outputs are normalized into 0_1`() {
        val img = IntArray(200 * 100) { (it * 2654435761L).toInt() }
        val out = FloatArray(EyeModelContract.ELEMENTS_PER_FRAME)
        ImageTensorPreprocessor.fillTensor(img, 200, 100, 10, 5, 80, 60, out)
        out.forEach { v ->
            assertThat(v).isAtLeast(0f)
            assertThat(v).isAtMost(1f)
        }
    }

    @Test
    fun `crop partially outside image is clamped and does not throw`() {
        val img = IntArray(64 * 64) { 0x7F7F7F }
        val out = FloatArray(EyeModelContract.ELEMENTS_PER_FRAME)
        ImageTensorPreprocessor.fillTensor(img, 64, 64, -10, -10, 100, 100, out)
        out.forEach { v ->
            assertThat(v).isWithin(1e-6f).of(0x7F / 255f)
        }
    }

    @Test
    fun `uniform gray crop produces uniform tensor`() {
        val img = IntArray(32 * 32) { 0xFF808080.toInt() }
        val out = FloatArray(EyeModelContract.ELEMENTS_PER_FRAME)
        ImageTensorPreprocessor.fillTensor(img, 32, 32, 0, 0, 32, 32, out)
        // every third value (R) equals 128/255
        for (i in out.indices step 3) {
            assertThat(out[i]).isWithin(1e-6f).of(128f / 255f)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wrong output size throws`() {
        ImageTensorPreprocessor.fillTensor(IntArray(64 * 64), 64, 64, 0, 0, 64, 64, FloatArray(1))
    }
}
