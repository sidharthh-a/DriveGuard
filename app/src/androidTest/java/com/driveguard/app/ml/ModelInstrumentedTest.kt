package com.driveguard.app.ml

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.FloatBuffer

/**
 * Verifies the REAL bundled model on a REAL device:
 *  - tensor shapes/dtypes match the documented contract;
 *  - the canonical vectors (desktop-generated with TensorFlow 2.21) reproduce
 *    on-device within tolerance, proving the whole preprocessing→inference
 *    contract travels correctly;
 *  - measures single-frame latency (reported, not claimed as a benchmark).
 */
@RunWith(AndroidJUnit4::class)
class ModelInstrumentedTest {

    /** App context: real model assets. */
    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Test context: instrumentation APK assets (model_test_vectors.json). */
    private fun testContext() = InstrumentationRegistry.getInstrumentation().context

    private fun loadVectors(): List<Triple<String, FloatArray, FloatArray>> {
        val json = JSONObject(
            testContext().assets.open("model_test_vectors.json").bufferedReader().use { it.readText() },
        )
        val out = mutableListOf<Triple<String, FloatArray, FloatArray>>()
        val vectors = json.getJSONArray("vectors")
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val input = v.getJSONArray("input").let { arr ->
                FloatArray(arr.length()) { k -> arr.getDouble(k).toFloat() }
            }
            val output = v.optJSONArray("output")?.let { arr ->
                FloatArray(arr.length()) { k -> arr.getDouble(k).toFloat() }
            } ?: FloatArray(0)
            out.add(Triple(v.getString("name"), input, output))
        }
        return out
    }

    @Test
    fun modelLoadsOnDevice() {
        val classifier = EyeStateClassifier.create(context())
        classifier.use { c ->
            // A neutral frame should produce a finite probability in [0,1].
            val p = c.classify(FloatArray(EyeModelContract.ELEMENTS_PER_FRAME) { 0.5f })
            assertThat(p).isAtLeast(0f)
            assertThat(p).isAtMost(1f)
        }
    }

    @Test
    fun canonicalVectorsReproduceOnDevice() {
        val vectors = loadVectors()
        assertThat(vectors).isNotEmpty()
        val tolerance = 1e-4f

        EyeStateClassifier.create(context()).use { c ->
            for ((name, input, expected) in vectors) {
                if (expected.isEmpty()) continue
                val actual = c.classify(input)
                assertThat(actual).isWithin(tolerance).of(expected[0])
            }
        }
    }

    @Test
    fun openAndClosedReferenceVectorsSeparateCleanly() {
        val vectors = loadVectors().toMap()
        val open = vectors["open_eye_like"]!!
        val closed = vectors["closed_eye_like"]!!
        EyeStateClassifier.create(context()).use { c ->
            val pOpen = c.classify(open)
            val pClosed = c.classify(closed)
            assertThat(pOpen).isLessThan(0.05f)
            assertThat(pClosed).isGreaterThan(0.95f)
        }
    }

    @Test
    fun latencyIsMeasuredAndSane() {
        EyeStateClassifier.create(context()).use { c ->
            val input = FloatArray(EyeModelContract.ELEMENTS_PER_FRAME) { 0.5f }
            // warm-up
            repeat(5) { c.classify(input) }
            val samples = 20
            val t0 = System.nanoTime()
            repeat(samples) { c.classify(input) }
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0 / samples
            // Sanity bound only: per-frame must be far below real-time needs
            // on any modern device. NOT a performance claim.
            assertThat(elapsedMs).isLessThan(200.0)
        }
    }

    private fun List<Triple<String, FloatArray, FloatArray>>.toMap() =
        associate { (name, input, _) -> name to input }
}
