package com.driveguard.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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

/**
 * The active-monitoring overlay. Intentionally minimal: a single large status
 * band, the session timer and one obvious stop control. The camera preview is
 * composed *behind* this overlay by the navigation route.
 */
@Composable
fun DrivingScreen(
    monitoringState: MonitoringUiState,
    onStop: () -> Unit,
) {
    // Animated colors keep transitions smooth — no banding/flicker.
    val statusColor by animateColorAsState(
        targetValue = when (monitoringState.state) {
            DrowsinessDetector.State.AWAKE -> Color(0xCC1565C0)
            DrowsinessDetector.State.EYES_CLOSING -> Color(0xCCFF8F00)
            DrowsinessDetector.State.DROWSY_ALERT -> Color(0xCCD32F2F)
            DrowsinessDetector.State.RECOVERING -> Color(0xCC00695C)
        },
        animationSpec = tween(300),
        label = "statusColor",
    )
    val statusText = when (monitoringState.state) {
        DrowsinessDetector.State.AWAKE -> "Monitoring"
        DrowsinessDetector.State.EYES_CLOSING -> "Stay alert"
        DrowsinessDetector.State.DROWSY_ALERT -> "DROWSINESS DETECTED"
        DrowsinessDetector.State.RECOVERING -> "Recovering"
    }
    val attentionText = when (monitoringState.faceState) {
        FaceState.FACE_OK -> monitoringState.eyeStateLabel
        FaceState.FACE_TEMPORARILY_LOST -> "Face temporarily lost"
        FaceState.NO_FACE -> "Face not detected"
        FaceState.FACE_TOO_SMALL -> "Move phone closer for reliable monitoring"
    }
    val faceGlyph = when (monitoringState.faceState) {
        FaceState.FACE_OK -> "✓"
        FaceState.FACE_TEMPORARILY_LOST -> "⚠"
        else -> "✕"
    }

    if (monitoringState.inPipMode) {
        PipMonitoringContent(monitoringState, statusText, statusColor, faceGlyph)
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Spacer(Modifier.weight(1f))
            // Face-status chip — distinct ✓ / ⚠ / ✕ states, animated color,
            // stable content description for TalkBack.
            val chipColor by animateColorAsState(
                targetValue = when (monitoringState.faceState) {
                    FaceState.FACE_OK -> Color(0xCC2E7D32)
                    FaceState.FACE_TEMPORARILY_LOST -> Color(0xCCF9A825)
                    else -> Color(0xCCB71C1C)
                },
                animationSpec = tween(250),
                label = "chipColor",
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = chipColor,
                modifier = Modifier.semantics {
                    contentDescription = "Face status: $attentionText"
                },
            ) {
                Text(
                    "$faceGlyph  $attentionText",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = statusColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Monitoring status: $statusText" },
            ) {
                Column(
                    Modifier.padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                    )
                    Text(
                        attentionText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    TimeFormat.duration(monitoringState.elapsedMs),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Button(onClick = onStop) {
                    Text("Stop monitoring")
                }
            }
        }
    }
}

/**
 * Compact layout for the picture-in-picture window: status text large enough
 * to read at PiP size, the face glyph, and the timer — nothing else. All
 * interaction (stop) stays in the full-screen UI / remote actions.
 */
@Composable
private fun PipMonitoringContent(
    monitoringState: MonitoringUiState,
    statusText: String,
    statusColor: Color,
    faceGlyph: String,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = statusColor,
            ) {
                Text(
                    statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "$faceGlyph ${TimeFormat.duration(monitoringState.elapsedMs)}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .background(Color(0x88000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Face tracking status for the UI. The three states map 1:1 onto the
 * detector's [com.driveguard.app.detection.FaceTrackStatus]:
 *
 *  - FACE_OK              → "✓ Face detected" (+ eye state)
 *  - FACE_TEMPORARILY_LOST → "⚠ Face temporarily lost" (grace window active,
 *                           drowsiness evidence is frozen — not a failure)
 *  - NO_FACE              → "✕ Face not detected" (grace expired or driver gone)
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
