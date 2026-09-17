package com.driveguard.app.camera

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy

/**
 * Converts YUV_420_888 camera frames to packed RGB (0xFFRRGGBB ints).
 *
 * Allocation-free: output is written into a caller-provided IntArray.
 * Handles both semi-planar (NV12/NV21-style, the common CameraX path) and
 * planar layouts by using per-plane rowStride/pixelStride, and applies the
 * requested rotation + mirroring in the same pass so no intermediate bitmap
 * is ever created.
 */
object YuvToRgbConverter {

    /**
     * @param image CameraX ImageProxy (YUV_420_888), already closed by caller afterwards.
     * @param out packed RGB output, length must be width*height of the OUTPUT image.
     * @param outWidth output width (after rotation/mirror)
     * @param outHeight output height
     * @param rotationDegrees clockwise rotation to apply
     * @param mirrorFront true to mirror horizontally (front camera)
     * @return false if the format is unsupported (caller degrades gracefully)
     */
    fun convert(
        image: ImageProxy,
        out: IntArray,
        outWidth: Int,
        outHeight: Int,
        rotationDegrees: Int,
        mirrorFront: Boolean,
    ): Boolean {
        if (image.format != ImageFormat.YUV_420_888) return false
        val w = image.width
        val h = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixStride = yPlane.pixelStride // 1
        val uRowStride = uPlane.rowStride
        val uPixStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixStride = vPlane.pixelStride

        // Precompute rotation/mirroring mapping for each output pixel.
        val rotate90 = rotationDegrees == 90 || rotationDegrees == 270
        val srcW = if (rotate90) outHeight else outWidth
        val srcH = if (rotate90) outWidth else outHeight

        var outIdx = 0
        for (oy in 0 until outHeight) {
            for (ox in 0 until outWidth) {
                // Map output pixel back to source coordinates.
                var sx: Int
                var sy: Int
                when (rotationDegrees) {
                    0 -> { sx = ox; sy = oy }
                    90 -> { sx = oy; sy = srcH - 1 - ox }
                    180 -> { sx = srcW - 1 - ox; sy = srcH - 1 - oy }
                    270 -> { sx = srcW - 1 - oy; sy = ox }
                    else -> { sx = ox; sy = oy }
                }
                if (mirrorFront) {
                    sx = srcW - 1 - sx
                }
                sx = sx.coerceIn(0, w - 1)
                sy = sy.coerceIn(0, h - 1)

                val y = yBuf.get(sy * yRowStride + sx * yPixStride).toInt() and 0xFF
                val uvX = sx shr 1
                val uvY = sy shr 1
                val uIdx = uvY * uRowStride + uvX * uPixStride
                val vIdx = uvY * vRowStride + uvX * vPixStride
                var u = uBuf.get(uIdx).toInt() and 0xFF
                var v = vBuf.get(vIdx).toInt() and 0xFF
                if (u < 0) u = 0
                val uf = u - 128
                val vf = v - 128
                // BT.601 full-range (JPEG-style) conversion.
                val r = (y + 1.402f * vf + 0.5f).toInt().coerceIn(0, 255)
                val g = (y - 0.344136f * uf - 0.714136f * vf + 0.5f).toInt().coerceIn(0, 255)
                val b = (y + 1.772f * uf + 0.5f).toInt().coerceIn(0, 255)
                out[outIdx++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return true
    }
}
