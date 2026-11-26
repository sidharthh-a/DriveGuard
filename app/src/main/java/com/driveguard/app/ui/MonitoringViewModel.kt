package com.driveguard.app.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.driveguard.app.alerts.AlertEngine
import com.driveguard.app.alerts.AlertEvent
import com.driveguard.app.alerts.AlertPlayer
import com.driveguard.app.camera.DrowsinessFrameAnalyzer
import com.driveguard.app.camera.FrameResult
import com.driveguard.app.detection.DrowsinessConfig
import com.driveguard.app.detection.DrowsinessDetector
import com.driveguard.app.detection.FaceTrackStatus
import com.driveguard.app.detection.Observation
import com.driveguard.app.detection.DetectionTuning
import com.driveguard.app.detection.Sensitivity
import com.driveguard.app.ml.EyeStateClassifier
import com.driveguard.app.service.MonitoringService
import com.driveguard.app.ui.screens.FaceState
import com.driveguard.app.ui.screens.MonitoringUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Orchestrates a monitoring session:
 * CameraX binding → frame analyzer → detector/alert engine → UI state.
 *
 * Lifecycle-safe: [start] binds to the passed [LifecycleOwner]; [stop] unbinds,
 * releases the analyzer and persists the session exactly once — even if
 * invoked twice (explicit stop + composition disposal during PiP exit).
 */
class MonitoringViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()

    private val detector = DrowsinessDetector()
    private val alertEngine = AlertEngine()
    private var classifier: EyeStateClassifier? = null
    private var analyzer: DrowsinessFrameAnalyzer? = null
    private var alertPlayer: AlertPlayer? = null

    /** Test-only window onto the player lifetime (see MonitoringViewModelLifecycleTest). */
    @androidx.annotation.VisibleForTesting
    internal val activeAlertPlayer: AlertPlayer?
        get() = alertPlayer
    private var analysisExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    private val recorder = com.driveguard.app.data.SessionRecorder()
    private var sessionStartMs = 0L
    @Volatile private var stopping = false

    private val _uiState = MutableStateFlow(MonitoringUiState())
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    private val _sessionFinished = MutableStateFlow<Long?>(null)
    val sessionFinished: StateFlow<Long?> = _sessionFinished.asStateFlow()

    @Volatile private var soundEnabled = true
    @Volatile private var vibrationEnabled = true

    /** True while an alert's sound/vibration is still going. */
    @Volatile private var alarmSounding = false
    private val lastInferenceMs = AtomicLong(0)

    /** Loads the model and settings. Reports whether the model is usable. */
    fun prepare(onModelReady: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                classifier = EyeStateClassifier.create(context)
                onModelReady(true)
            } catch (e: Exception) {
                Log.e(TAG, "model prepare failed", e)
                onModelReady(false)
            }
        }
        viewModelScope.launch {
            val s = com.driveguard.app.data.SettingsRepository(context).settings.first()
            soundEnabled = s.soundEnabled
            vibrationEnabled = s.vibrationEnabled
            val sens = Sensitivity.fromName(s.sensitivityName)
            detector.config = sens.toConfig(DrowsinessConfig())
        }
    }

    /** Binds the camera and starts a session. */
    fun start(lifecycleOwner: LifecycleOwner, previewView: androidx.camera.view.PreviewView) {
        if (sessionStartMs != 0L) return
        // [stop] releases the player, so a subsequent session must create a
        // fresh one; otherwise sound and vibration would be silently dead.
        if (alertPlayer == null) {
            alertPlayer = AlertPlayer.create(context)
        }
        val now = System.currentTimeMillis()
        sessionStartMs = now
        stopping = false
        recorder.start(now)
        detector.reset()
        alertEngine.reset()

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                val cls = classifier ?: throw IllegalStateException("model not prepared")
                val frameAnalyzer = DrowsinessFrameAnalyzer(
                    classifier = cls,
                    onResult = { result -> handleFrameResult(result) },
                )
                analyzer = frameAnalyzer
                analysis.setAnalyzer(analysisExecutor, frameAnalyzer)

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis,
                )
            } catch (e: Exception) {
                Log.e(TAG, "camera binding failed", e)
                _uiState.value = _uiState.value.copy(modelError = true)
            }
        }, ContextCompat.getMainExecutor(context))

        MonitoringService.start(context)

        timerJob = viewModelScope.launch {
            while (sessionStartMs != 0L) {
                val now = System.currentTimeMillis()
                detector.tick(now)
                _uiState.value = _uiState.value.copy(elapsedMs = now - sessionStartMs)
                delay(250)
            }
        }
    }

    private var timerJob: Job? = null

    /** Stops any alert still playing, once per alert. */
    private fun silenceAlarm() {
        if (!alarmSounding) return
        alarmSounding = false
        alertPlayer?.silence()
    }

    /** Starts the sound/vibration for an alert (a new one or a re-arm). */
    private fun startRinging(event: AlertEvent) {
        val player = alertPlayer ?: return
        player.play(event, soundEnabled, vibrationEnabled)
        alarmSounding = true
    }

    private fun handleFrameResult(result: FrameResult) {
        val now = System.currentTimeMillis()
        when (result) {
            is FrameResult.Processed -> {
                recorder.onFaceFound(now)
                recorder.onInferenceSample(analyzer?.lastAnalysisMs ?: 0L)
                val observation = detector.update(result.verdict, now)
                detector.tick(now)
                // An alarm must not outlive the drowsiness that caused it: the
                // moment the model sees open eyes again, cut the sound and
                // vibration instead of letting the ringtone play out.
                if (observation == Observation.EYES_OPEN) {
                    silenceAlarm()
                }
                if (detector.pollAlertFired()) {
                    recorder.onAlert()
                    alertEngine.onDrowsinessStart(now)?.let { evt -> startRinging(evt) }
                } else if (
                    detector.isAlertActive() &&
                    !alarmSounding &&
                    detector.closedDurationMs(now) >= DetectionTuning.ALARM_REARM_MS
                ) {
                    // The episode is still latched but an open-eye frame cut the
                    // alarm; the eyes are closing again, so sound it once more
                    // without counting a new episode. Without this a single
                    // glance up left the rest of the episode silent.
                    startRinging(AlertEvent(alertEngine.currentLevel(), now))
                }
                if (!detector.isAlertActive()) {
                    // Released (or the driver left the frame): nothing left to
                    // alarm about. This also re-arms the next episode.
                    silenceAlarm()
                    alertEngine.onDrowsinessEnd(now)
                }
                _uiState.value = _uiState.value.copy(
                    state = detector.currentState(),
                    faceState = FaceState.FACE_OK,
                    eyeStateLabel = if (observation == Observation.EYES_CLOSED)
                        "Eyes closed" else "Eyes open",
                )
            }

            FrameResult.NoFace -> {
                recorder.onFaceLostStart(now)
                detector.update(
                    com.driveguard.app.detection.FrameVerdict(hasFace = false, pClosed = 0f),
                    now,
                )
                detector.tick(now)
                // Grace-aware UI mapping: a momentary gap shows "temporarily
                // lost" (warning), a real departure shows "not detected".
                val faceState = when (detector.faceStatus()) {
                    FaceTrackStatus.TEMPORARILY_LOST -> FaceState.FACE_TEMPORARILY_LOST
                    FaceTrackStatus.LOST -> FaceState.NO_FACE
                    FaceTrackStatus.TRACKED -> FaceState.FACE_TEMPORARILY_LOST
                }
                _uiState.value = _uiState.value.copy(faceState = faceState)
            }

            FrameResult.ClassifierFailed -> {
                recorder.onClassifierFailure()
                _uiState.value = _uiState.value.copy(modelError = true)
            }

            FrameResult.FrameUnsupported -> {
                // Not a model problem — the frame format is unusable. Keep the
                // session alive; the user still sees the live preview.
                Log.w(TAG, "unsupported frame format received")
            }
        }
    }

    /**
     * Stops monitoring and persists the session. Idempotent: repeated calls
     * (explicit stop, PiP dismissal, composition disposal) persist once.
     */
    fun stop() {
        val now = System.currentTimeMillis()
        if (sessionStartMs == 0L || stopping) return
        stopping = true
        timerJob?.cancel()
        timerJob = null
        recorder.end(now)
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "unbind failed", e)
        }
        analyzer?.close()
        analyzer = null
        alarmSounding = false
        alertPlayer?.release()
        alertPlayer = null
        MonitoringService.stop(context)

        val entity = recorder.build(sessionStartMs, now)
        sessionStartMs = 0L
        // Application-scoped, not viewModelScope: clearing the ViewModel must
        // not discard a session the user just finished.
        val app = getApplication<com.driveguard.app.DriveGuardApplication>()
        app.applicationScope.launch {
            val dao = com.driveguard.app.data.AppDatabase.get(context).sessionDao()
            val id = dao.insert(entity)
            _sessionFinished.value = id
        }
    }

    fun consumeSessionFinished() {
        _sessionFinished.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Session persistence (if any) already handled by stop(); just free
        // hardware resources.
        analysisExecutor.shutdown()
        classifier?.close()
        classifier = null
    }

    companion object {
        private const val TAG = "MonitoringViewModel"
    }
}
