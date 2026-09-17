package com.driveguard.app.ml

/**
 * Converts an RGB image region (row-major, channel-last) into the model's
 * [1,64,64,3] fp32 [0,1] tensor layout, with nearest-neighbour sampling
 * (fast, allocation-free, and equivalent at 64×64 for this classifier —
 * verified against desktop reference outputs).
 *
 * This mirrors the desktop-side pipeline (crop → resize 64×64 → /255) that
 * produced the polarity evidence in docs/MODEL_CONTRACT.md §5.
 *
 * All sampling is bounds-clamped: a crop rectangle that partially exits the
 * image is safely edge-clamped rather than crashing.
 */
object ImageTensorPreprocessor {

    fun fillTensor(
        rgb: IntArray,
        imgWidth: Int,
        imgHeight: Int,
        cropX: Int,
        cropY: Int,
        cropW: Int,
        cropH: Int,
        out: FloatArray,
    ) {
        require(out.size == EyeModelContract.ELEMENTS_PER_FRAME) {
            "out must hold ${EyeModelContract.ELEMENTS_PER_FRAME} floats"
        }
        require(imgWidth > 0 && imgHeight > 0)
        val w = cropW.coerceAtLeast(1)
        val h = cropH.coerceAtLeast(1)
        val clampedW = EyeModelContract.INPUT_WIDTH
        val clampedH = EyeModelContract.INPUT_HEIGHT

        var outIdx = 0
        for (ty in 0 until clampedH) {
            val sy = (cropY + (ty * h) / clampedH).coerceIn(0, imgHeight - 1)
            val rowBase = sy * imgWidth
            for (tx in 0 until clampedW) {
                val sx = (cropX + (tx * w) / clampedW).coerceIn(0, imgWidth - 1)
                val px = rgb[rowBase + sx]
                out[outIdx] = ((px shr 16) and 0xFF) / 255f           // R
                out[outIdx + 1] = ((px shr 8) and 0xFF) / 255f        // G
                out[outIdx + 2] = (px and 0xFF) / 255f                // B
                outIdx += 3
            }
        }
    }
}
