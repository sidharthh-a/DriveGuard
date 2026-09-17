package com.driveguard.app.ml

/**
 * The immutable contract of the bundled eye-state model, established by direct
 * flatbuffer inspection and desktop-side inference (see docs/MODEL_CONTRACT.md).
 *
 * Architecture (decoded, not assumed):
 *   Conv(3→32,3×3)+ReLU → MaxPool → Conv(32→64)+ReLU → MaxPool →
 *   Conv(64→128)+ReLU → MaxPool → Flatten → Dense(128)+ReLU → Dense(1) → Sigmoid
 *
 * Total parameters: 683,329 (~2.6 MB fp32).
 */
object EyeModelContract {
    const val MODEL_PATH = "models/eye_model.tflite"

    const val INPUT_WIDTH = 64
    const val INPUT_HEIGHT = 64
    const val INPUT_CHANNELS = 3
    const val INPUT_BATCH = 1
    const val ELEMENTS_PER_FRAME = INPUT_WIDTH * INPUT_HEIGHT * INPUT_CHANNELS // 12288

    /** Output is a single sigmoid probability. */
    const val OUTPUT_SIZE = 1

    /**
     * Empirically established polarity (docs/MODEL_CONTRACT.md §5):
     * output = P(eye closed). Verified against sliding-window activation
     * on probe imagery and the canonical open/closed test vectors.
     */
    const val OUTPUT_IS_P_CLOSED = true
}
