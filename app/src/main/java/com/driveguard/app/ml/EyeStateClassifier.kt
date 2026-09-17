package com.driveguard.app.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Thin, single-instance wrapper around the LiteRT interpreter for the bundled
 * eye-state model.
 *
 * Guarantees:
 *  - one interpreter per process, created lazily, reused for every frame;
 *  - inference never allocates a new input buffer — a reusable direct
 *    [ByteBuffer] is refilled per call;
 *  - every failure mode degrades to a thrown [ModelInferenceException]
 *    (callers translate it into a reduced-capability state, never a crash);
 *  - [close] is idempotent.
 */
class EyeStateClassifier private constructor(
    private val interpreter: Interpreter,
) : Closeable {

    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(EyeModelContract.ELEMENTS_PER_FRAME * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
    private val outputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(EyeModelContract.OUTPUT_SIZE * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
    private val closed = AtomicBoolean(false)

    /**
     * Runs the model on a 64×64 RGB image where each pixel channel is a float
     * in [0,1], laid out row-major, channel-last (exactly the contract in
     * docs/MODEL_CONTRACT.md).
     *
     * @param rgbFloats 12,288 floats in RGB channel order.
     * @return P(eye closed) in [0,1].
     * @throws ModelInferenceException if the interpreter fails.
     */
    fun classify(rgbFloats: FloatArray): Float {
        check(!closed.get()) { "classifier already closed" }
        require(rgbFloats.size == EyeModelContract.ELEMENTS_PER_FRAME) {
            "expected ${EyeModelContract.ELEMENTS_PER_FRAME} floats, got ${rgbFloats.size}"
        }
        return try {
            inputBuffer.rewind()
            inputBuffer.asFloatBuffer().put(rgbFloats)
            outputBuffer.rewind()
            interpreter.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()
            val p = outputBuffer.asFloatBuffer().get(0)
            p.isFinite().let { finite ->
                if (!finite) throw ModelInferenceException("non-finite model output")
            }
            p.coerceIn(0f, 1f)
        } catch (e: ModelInferenceException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "inference failed", e)
            throw ModelInferenceException("inference failed: ${e.message}", e)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                interpreter.close()
            } catch (e: Throwable) {
                Log.w(TAG, "interpreter close failed", e)
            }
        }
    }

    class ModelInferenceException(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {
        private const val TAG = "EyeStateClassifier"

        /**
         * Loads and allocates the bundled model. CPU + XNNPACK by default; the
         * model is tiny (2.6 MB fp32) and GPU/NNAPI delegates add latency and
         * compatibility risk without measured benefit at 64×64 (see contract §7).
         *
         * @throws ModelInferenceException if the asset is missing or corrupt.
         */
        fun create(context: Context, numThreads: Int = 2): EyeStateClassifier {
            return try {
                val model = loadModel(context)
                val options = Interpreter.Options().apply {
                    setNumThreads(numThreads.coerceIn(1, 4))
                }
                EyeStateClassifier(Interpreter(model, options))
            } catch (e: ModelInferenceException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "model load failed", e)
                throw ModelInferenceException("model load failed: ${e.message}", e)
            }
        }

        private fun loadModel(context: Context): ByteBuffer {
            context.assets.openFd(EyeModelContract.MODEL_PATH).use { fd ->
                // Asset compression is disabled for .tflite in androidResources
                // so the file can be mapped directly (fast startup, low RAM).
                java.io.FileInputStream(fd.fileDescriptor).channel.use { ch ->
                    return ch.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        }

        /** Sanity helper used by tests: max over per-eye probabilities. */
        fun combineEyes(pLeft: Float, pRight: Float): Float = max(pLeft, pRight)

        /** Sanity helper used by tests: clamp helper mirrors classify(). */
        fun clamp01(v: Float): Float = min(1f, max(0f, v))
    }
}
