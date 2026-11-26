package com.driveguard.app.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.driveguard.app.detection.DetectionTuning
import com.driveguard.app.detection.FrameVerdict
import com.driveguard.app.ml.EyeModelContract
import com.driveguard.app.ml.EyeStateClassifier
import com.driveguard.app.ml.ImageTensorPreprocessor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** Result categories emitted per analyzed frame. */
sealed interface FrameResult {
    /** Face found (or tracked through a brief gap), eye crops classified. */
    data class Processed(val verdict: FrameVerdict, val faceBox: FaceBox) : FrameResult

    /** No face (and no live track) — grace handling happens downstream. */
    data object NoFace : FrameResult

    /** Face present but eye crops could not be classified (e.g. inference error). */
    data object ClassifierFailed : FrameResult

    /** Frame unusable (format/rotation unsupported). */
    data object FrameUnsupported : FrameResult
}

/**
 * Face bounding box in UPRIGHT, MIRRORED analysis-frame coordinates — the same
 * space as the RGB buffer fed to the classifier and what the user sees in the
 * (mirrored) preview, so overlays align without extra transforms.
 */
data class FaceBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * CameraX analyzer gluing: frame → ML Kit face → eye ROI → model → verdict.
 *
 * Root-cause fixes for the historical "face not detected" flakiness:
 *
 *  1. **Correct InputImage geometry.** The detector previously received the
 *     ROTATED dimensions with rotation 0; ML Kit then searched a squashed
 *     image and missed faces at any head tilt. We now pass the sensor-space
 *     dimensions WITH the rotation tag, so ML Kit uprights the frame itself
 *     and returns boxes in upright coordinates.
 *  2. **Synchronous detection.** Detection used a fire-and-forget callback
 *     whose success handler read a shared, already-reused RGB buffer — a race
 *     that intermittently classified garbage. Detection now completes within
 *     the analyze() call (Tasks.await) on the dedicated analyzer thread.
 *  3. **Mirrored-box mapping.** Face boxes arrive in un-mirrored upright
 *     coordinates while the RGB buffer (and preview) are mirrored for the
 *     front camera; boxes are mapped into the mirrored space so eye crops and
 *     overlays always line up.
 *  4. **Analyzer-level tracking.** Up to [DetectionTuning.TRACK_MAX_MISSES]
 *     consecutive empty detections reuse the last good box (the face has not
 *     teleported; the detector merely blinked) instead of immediately
 *     reporting "no face". Longer gaps flow to the time-based grace window in
 *     [com.driveguard.app.detection.DrowsinessDetector].
 *
 * Backpressure: STRATEGY_KEEP_ONLY_LATEST — under load we drop stale frames
 * instead of queueing them. Detection is synchronous, so at most one cycle is
 * ever in flight; ML Kit internally drops work when it is still busy.
 *
 * Threading: CameraX invokes [analyze] on a dedicated background executor.
 * Everything runs there — never on the main thread.
 */
class DrowsinessFrameAnalyzer(
    private val classifier: EyeStateClassifier,
    private val onResult: (FrameResult) -> Unit,
    private val frontCamera: Boolean = true,
) : ImageAnalysis.Analyzer {

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            // Sensible defaults — NOT lowered to chase recall; stability comes
            // from tracking + grace windows (see DetectionTuning rationale).
            .setMinFaceSize(DetectionTuning.MLKIT_MIN_FACE_SIZE)
            .enableTracking()
            .build(),
    )

    // Reusable buffers (no per-frame allocation in steady state).
    private var rgbBuf = IntArray(0)
    private var tensorBuf = FloatArray(EyeModelContract.ELEMENTS_PER_FRAME)

    // Analyzer-level track: last good face box + consecutive missed detections.
    private var trackedBox: FaceBox? = null
    private var missedDetections = 0

    @Volatile var lastFaceBox: FaceBox? = null
        private set

    @Volatile var processing = false
        private set

    /** Wall-clock duration of the last full analyze() cycle, for perf metrics. */
    @Volatile var lastAnalysisMs: Long = 0
        private set

    // Throttled pipeline stats (logged every LOG_EVERY_N cycles).
    private var framesIn = 0L
    private var facesSeen = 0L
    private var trackRescues = 0L
    private var noFaceFrames = 0L
    private var classifyFailures = 0L
    private var lastLoggedAtMs = 0L

    override fun analyze(image: ImageProxy) {
        processing = true
        val startedAt = System.currentTimeMillis()
        try {
            processFrame(image)
        } catch (t: Throwable) {
            Log.e(TAG, "frame processing failed", t)
            onResult(FrameResult.ClassifierFailed)
        } finally {
            lastAnalysisMs = System.currentTimeMillis() - startedAt
            processing = false
            image.close()
        }
    }

    private fun processFrame(image: ImageProxy) {
        framesIn++
        val rotation = image.imageInfo.rotationDegrees
        val mirror = frontCamera

        // 1. Rotate/mirror YUV to upright RGB (allocation-free buffer reuse).
        val rot90 = rotation == 90 || rotation == 270
        val outW = if (rot90) image.height else image.width
        val outH = if (rot90) image.width else image.height
        if (rgbBuf.size < outW * outH) rgbBuf = IntArray(outW * outH)
        val ok = YuvToRgbConverter.convert(image, rgbBuf, outW, outH, rotation, mirror)
        if (!ok) {
            onResult(FrameResult.FrameUnsupported)
            return
        }

        // 2. Face detection — sensor-space dims + rotation tag so ML Kit
        //    uprights the frame itself (fix for the historical dimension bug).
        val inputImage = InputImage.fromByteArray(
            yuvToNv21(image),
            image.width,
            image.height,
            rotation,
            InputImage.IMAGE_FORMAT_NV21,
        )

        // Synchronous wait on the analyzer thread: eliminates the race where
        // the async callback read a shared buffer already overwritten by the
        // next frame.
        val faces: List<Face>? = try {
            Tasks.await(faceDetector.process(inputImage), DETECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            if (framesIn % LOG_EVERY_N == 0L || e !is java.util.concurrent.TimeoutException) {
                Log.w(TAG, "face detection failed/timeout", e)
            }
            null
        }

        if (faces == null) {
            // Detector hiccup: same path as a missed detection (tracker/grace).
            handleNoDetection()
            return
        }
        handleDetections(faces, outW, outH)
    }

    private fun handleDetections(faces: List<Face>, uprightW: Int, uprightH: Int) {
        if (faces.isEmpty()) {
            handleNoDetection()
            return
        }
        missedDetections = 0
        facesSeen++

        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
        val box = mapToMirroredUpright(face.boundingBox, uprightW, uprightH)
        trackedBox = box
        lastFaceBox = box

        classifyWithBox(box, uprightW, uprightH)
    }

    /** Empty detection result: reuse the tracked box for a couple of misses. */
    private fun handleNoDetection() {
        val tracked = trackedBox
        if (tracked != null && missedDetections < DetectionTuning.TRACK_MAX_MISSES) {
            missedDetections++
            trackRescues++
            classifyWithBox(tracked, trackedWidth(tracked), trackedHeight(tracked))
            return
        }
        trackedBox = null
        lastFaceBox = null
        noFaceFrames++
        onResult(FrameResult.NoFace)
    }

    // The tracked box is reused against the last known upright canvas size;
    // crop coordinates are clamped by the preprocessor, so stale canvas size
    // is safe — we simply pass the box extents as the canvas.
    private fun trackedWidth(b: FaceBox) = b.left.toInt() + b.width.toInt() + 1
    private fun trackedHeight(b: FaceBox) = b.top.toInt() + b.height.toInt() + 1

    /**
     * Maps an ML Kit box (upright, un-mirrored sensor coordinates) into the
     * mirrored upright space of the RGB buffer / preview.
     */
    private fun mapToMirroredUpright(
        fb: android.graphics.Rect,
        uprightW: Int,
        uprightH: Int,
    ): FaceBox {
        val left = if (frontCamera) {
            uprightW - (fb.left + fb.width())
        } else {
            fb.left
        }
        return FaceBox(
            left = left.toFloat().coerceIn(0f, uprightW.toFloat()),
            top = fb.top.toFloat().coerceIn(0f, uprightH.toFloat()),
            width = fb.width().toFloat(),
            height = fb.height().toFloat(),
        )
    }

    private fun classifyWithBox(box: FaceBox, w: Int, h: Int) {
        // Reliability gate: face too small → treat as "too far" (not drowsiness).
        val minSide = min(box.width, box.height)
        if (minSide < DetectionTuning.MIN_FACE_PX) {
            onResult(FrameResult.NoFace)
            return
        }

        val pLeft = classifyEye(box, w, h, leftEye = true)
        val pRight = classifyEye(box, w, h, leftEye = false)

        val verdict = when {
            pLeft == null && pRight == null -> null // both failed
            // Agreement-aware: a single closed-looking eye cannot stand in
            // for both (see EyeStateClassifier.combineEyes).
            else -> FrameVerdict(
                hasFace = true,
                pClosed = EyeStateClassifier.combineEyes(pLeft, pRight),
            )
        }

        when (verdict) {
            null -> {
                classifyFailures++
                onResult(FrameResult.ClassifierFailed)
            }
            else -> onResult(FrameResult.Processed(verdict, box))
        }
        logThrottled(box, verdict?.pClosed)
    }

    /** Returns P(closed) for one eye, or null if the crop could not be classified. */
    private fun classifyEye(box: FaceBox, w: Int, h: Int, leftEye: Boolean): Float? {
        return try {
            val crop = EyeCropGeometry.eyeCrop(
                box.left.toInt(), box.top.toInt(),
                box.width.toInt(), box.height.toInt(),
                leftEye,
            )
            ImageTensorPreprocessor.fillTensor(rgbBuf, w, h, crop.x, crop.y, crop.w, crop.h, tensorBuf)
            classifier.classify(tensorBuf)
        } catch (e: EyeStateClassifier.ModelInferenceException) {
            Log.w(TAG, "eye classification failed", e)
            null
        }
    }

    /**
     * Compact pipeline trace (Camera frame → preprocess → face detect →
     * classify → verdict) emitted at most every [LOG_EVERY_N] cycles to keep
     * logcat usable. Contains no personal data — only counts and P values.
     */
    private fun logThrottled(box: FaceBox?, pClosed: Float?) {
        val now = System.currentTimeMillis()
        if (now - lastLoggedAtMs < LOG_PERIOD_MS) return
        lastLoggedAtMs = now
        Log.d(
            TAG,
            "pipeline: frames=$framesIn faces=$facesSeen rescued=$trackRescues " +
                "noFace=$noFaceFrames clsFail=$classifyFailures " +
                "box=${box?.let { "${it.width.toInt()}x${it.height.toInt()}" } ?: "none"} " +
                "pClosed=${pClosed ?: Float.NaN} analysisMs=$lastAnalysisMs",
        )
    }

    private fun yuvToNv21(image: ImageProxy): ByteArray {
        // ML Kit accepts NV21; CameraX gives us YUV_420_888 planes. Build NV21
        // per frame (unavoidable small allocation for the detector input).
        val w = image.width
        val h = image.height
        val ySize = w * h
        val nv21 = ByteArray(ySize + 2 * (ySize / 4))
        val yPlane = image.planes[0]
        val yBuf = yPlane.buffer
        val yRowStride = yPlane.rowStride
        var pos = 0
        if (yRowStride == w) {
            yBuf.position(0)
            yBuf.get(nv21, 0, ySize)
            pos = ySize
        } else {
            for (row in 0 until h) {
                yBuf.position(row * yRowStride)
                yBuf.get(nv21, pos, w)
                pos += w
            }
        }
        // Interleave VU (NV21 order).
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val uRowStride = uPlane.rowStride
        val uPixStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixStride = vPlane.pixelStride
        val chromaHeight = h / 2
        val chromaWidth = w / 2
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val uIdx = row * uRowStride + col * uPixStride
                val vIdx = row * vRowStride + col * vPixStride
                nv21[pos++] = vPlane.buffer.get(vIdx)
                nv21[pos++] = uPlane.buffer.get(uIdx)
            }
        }
        return nv21
    }

    fun close() {
        faceDetector.close()
    }

    companion object {
        private const val TAG = "DrowsinessFrameAnalyzer"

        /** Upper bound for one ML Kit detection round-trip (typ. 5–20 ms). */
        private const val DETECTION_TIMEOUT_MS = 250L

        /** Pipeline stats cadence (both frame count and wall-clock gated). */
        private const val LOG_EVERY_N = 45L
        private const val LOG_PERIOD_MS = 4_000L
    }
}
