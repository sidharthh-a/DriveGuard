package com.driveguard.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.driveguard.app.detection.DrowsinessDetector
import com.driveguard.app.util.TimeFormat

// Overlay palette: everything sits on top of the live camera, so it is
// deliberately monochrome with one accent that carries the state.
private val Glass = Color(0x8C0B0F0E)
private val GlassStrong = Color(0xCC0B0F0E)
private val OnGlass = Color(0xFFF4F6F5)
private val OnGlassSoft = Color(0xB3F4F6F5)

private val Calm = Color(0xFF7FD8C4)
private val Caution = Color(0xFFF0B357)
private val Alarm = Color(0xFFFF8A7A)

/**
 * The active-monitoring overlay: current state, the eye reading, the timer and
 * one stop control. The camera preview is composed *behind* this by the
 * navigation route.
 */
@Composable
fun DrivingScreen(
    monitoringState: MonitoringUiState,
    onStop: () -> Unit,
) {
    val accent by animateColorAsState(
        targetValue = when (monitoringState.state) {
            DrowsinessDetector.State.AWAKE -> Calm
            DrowsinessDetector.State.EYES_CLOSING -> Caution
            DrowsinessDetector.State.DROWSY_ALERT -> Alarm
            DrowsinessDetector.State.RECOVERING -> Calm
        },
        animationSpec = tween(300),
        label = "accent",
    )
    val statusText = when (monitoringState.state) {
        DrowsinessDetector.State.AWAKE -> "Monitoring"
        DrowsinessDetector.State.EYES_CLOSING -> "Stay alert"
        DrowsinessDetector.State.DROWSY_ALERT -> "Drowsiness detected"
        DrowsinessDetector.State.RECOVERING -> "Recovering"
    }
    val attentionText = when (monitoringState.faceState) {
        FaceState.FACE_OK -> monitoringState.eyeStateLabel
        FaceState.FACE_TEMPORARILY_LOST -> "Face temporarily lost"
        FaceState.NO_FACE -> "Face not detected"
        FaceState.FACE_TOO_SMALL -> "Move phone closer"
    }
    val alerting = monitoringState.state == DrowsinessDetector.State.DROWSY_ALERT

    if (monitoringState.inPipMode) {
        PipMonitoringContent(monitoringState, statusText, accent, attentionText)
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Glass,
                    modifier = Modifier.semantics {
                        contentDescription = "Monitoring status: $statusText"
                    },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(accent, CircleShape),
                        )
                        Spacer(Modifier.size(9.dp))
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelLarge,
                            color = OnGlass,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Glass,
                    modifier = Modifier.semantics {
                        contentDescription = "Face status: $attentionText"
                    },
                ) {
                    Text(
                        attentionText,
                        style = MaterialTheme.typography.labelLarge,
                        color = OnGlassSoft,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (alerting) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = GlassStrong,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "Eyes have been closed",
                            style = MaterialTheme.typography.titleLarge,
                            color = Alarm,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Open your eyes to silence the alarm.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnGlassSoft,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        TimeFormat.duration(monitoringState.elapsedMs),
                        style = MaterialTheme.typography.displayMedium,
                        color = OnGlass,
                    )
                    Text(
                        "ELAPSED",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnGlassSoft,
                    )
                }
                Button(
                    onClick = onStop,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OnGlass,
                        contentColor = Color(0xFF0B0F0E),
                    ),
                    elevation = null,
                ) {
                    Text("Stop", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * Compact picture-in-picture layout: state and timer, readable at PiP size.
 * Interaction stays in the full-screen UI / remote actions.
 */
@Composable
private fun PipMonitoringContent(
    monitoringState: MonitoringUiState,
    statusText: String,
    accent: Color,
    attentionText: String,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = RoundedCornerShape(100.dp), color = GlassStrong) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(accent, CircleShape))
                    Spacer(Modifier.size(7.dp))
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelLarge,
                        color = OnGlass,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${TimeFormat.duration(monitoringState.elapsedMs)} · $attentionText",
                style = MaterialTheme.typography.labelMedium,
                color = OnGlass,
                modifier = Modifier
                    .background(Glass, RoundedCornerShape(100.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Face tracking status for the UI, mapped 1:1 onto the detector's
 * [com.driveguard.app.detection.FaceTrackStatus]:
 *  - FACE_OK: face tracked (+ eye state)
 *  - FACE_TEMPORARILY_LOST: grace window active, evidence frozen
 *  - NO_FACE: grace expired or driver gone
 */
enum class FaceState { FACE_OK, FACE_TEMPORARILY_LOST, NO_FACE, FACE_TOO_SMALL }

data class MonitoringUiState(
    val state: DrowsinessDetector.State = DrowsinessDetector.State.AWAKE,
    val faceState: FaceState = FaceState.FACE_OK,
    val eyeStateLabel: String = "Eyes open",
    val elapsedMs: Long = 0,
    val modelError: Boolean = false,
    /** True once at least one frame has been processed (hides initial flash). */
    val faceSeenOnce: Boolean = false,
    /** Wall-clock duration of the last analysis cycle, for the perf readout. */
    val lastAnalysisMs: Long = 0,
    /** True when the compact picture-in-picture variant should render. */
    val inPipMode: Boolean = false,
)
