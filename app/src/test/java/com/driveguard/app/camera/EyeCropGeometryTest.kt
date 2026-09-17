package com.driveguard.app.camera

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EyeCropGeometryTest {

    @Test
    fun `crops fall inside the face upper band`() {
        val r = EyeCropGeometry.eyeCrop(faceX = 100, faceY = 200, faceW = 200, faceH = 300, leftEye = true)
        assertThat(r.y).isAtLeast(200 + (300 * 0.2f).toInt())
        assertThat(r.y + r.h).isAtMost(200 + (300 * 0.55f).toInt())
    }

    @Test
    fun `left and right crops are horizontally separated`() {
        val left = EyeCropGeometry.eyeCrop(0, 0, 200, 300, leftEye = true)
        val right = EyeCropGeometry.eyeCrop(0, 0, 200, 300, leftEye = false)
        assertThat(left.x + left.w).isAtMost(right.x)
    }

    @Test
    fun `crop width is about 42 percent of face width`() {
        val r = EyeCropGeometry.eyeCrop(0, 0, 200, 300, leftEye = true)
        assertThat(r.w).isWithin(2).of((200 * 0.42f).toInt())
    }

    @Test
    fun `same face yields symmetric crops`() {
        val left = EyeCropGeometry.eyeCrop(0, 0, 200, 300, leftEye = true)
        val right = EyeCropGeometry.eyeCrop(0, 0, 200, 300, leftEye = false)
        val faceW = 200
        val lCenter = left.x + left.w / 2
        val rCenter = right.x + right.w / 2
        assertThat(lCenter + rCenter).isWithin(2).of(faceW) // symmetric about face middle
    }
}
