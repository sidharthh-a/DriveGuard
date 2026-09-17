package com.driveguard.app.camera

import kotlin.math.roundToInt

/**
 * Pure geometry for locating eye crops inside a face bounding box.
 * Extracted from the analyzer so it can be unit-tested without Android.
 *
 * The eyes sit between ~24% and ~50% of the face height in a frontal face;
 * each crop covers one horizontal half of that band with a small margin.
 */
object EyeCropGeometry {

    /** Vertical start of the eye band, as a fraction of face height. */
    const val BAND_TOP_FRACTION = 0.24f

    /** Height of the eye band, as a fraction of face height. */
    const val BAND_HEIGHT_FRACTION = 0.26f

    /** Width of each eye crop, as a fraction of face width. */
    const val CROP_WIDTH_FRACTION = 0.42f

    /** Horizontal centers of left/right eye as fraction of face width. */
    const val LEFT_CENTER_FRACTION = 0.29f
    const val RIGHT_CENTER_FRACTION = 0.71f

    data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

    fun eyeCrop(faceX: Int, faceY: Int, faceW: Int, faceH: Int, leftEye: Boolean): Rect {
        val bandTop = faceY + (faceH * BAND_TOP_FRACTION).roundToInt()
        val bandH = (faceH * BAND_HEIGHT_FRACTION).roundToInt()
        val bandW = (faceW * CROP_WIDTH_FRACTION).roundToInt()
        val cx = if (leftEye) {
            faceX + (faceW * LEFT_CENTER_FRACTION).roundToInt()
        } else {
            faceX + (faceW * RIGHT_CENTER_FRACTION).roundToInt()
        }
        return Rect(cx - bandW / 2, bandTop, bandW, bandH)
    }
}
