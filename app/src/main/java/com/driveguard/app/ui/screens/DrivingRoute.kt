package com.driveguard.app.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.driveguard.app.MainActivity
import com.driveguard.app.ui.MonitoringViewModel

/**
 * Route wrapper owning the camera stack for the driving screen:
 * creates the PreviewView, wires the MonitoringViewModel and forwards the
 * saved-session id to the caller when monitoring stops.
 *
 * Picture-in-picture behaviour:
 *  - Back while monitoring (not already in PiP) enters the mini floating
 *    window instead of leaving the session. Home/app-switch enter PiP
 *    automatically via [MainActivity.setAutoEnterEnabled] / onUserLeaveHint.
 *  - The camera + detection pipeline keeps running while in PiP (the activity
 *    stays composed and the foreground service holds camera access).
 *  - Tapping the PiP window returns to the full-screen UI (platform behaviour).
 *  - Only the explicit Stop control ends the session.
 */
@Composable
fun DrivingRoute(onStopped: (Long) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vm: MonitoringViewModel = viewModel()

    val uiState by vm.uiState.collectAsState()
    val finished by vm.sessionFinished.collectAsState()

    val activity = context as? MainActivity

    // Report monitoring activity to the Activity layer (drives PiP params)
    // and clear it on dispose so PiP never triggers from other screens.
    DisposableEffect(Unit) {
        activity?.monitoringActive = true
        activity?.updatePipParams()
        onDispose {
            activity?.monitoringActive = false
            activity?.updatePipParams()
            activity?.monitoringStopRequest = null
        }
    }

    // Compact PiP layout flag straight from the platform callback.
    val pipState: State<Boolean> = activity?.pipMode ?: remember { mutableStateOf(false) }
    val inPip by pipState

    // Back during active monitoring → floating mini window, not exit.
    BackHandler(enabled = !inPip) {
        activity?.requestEnterPip()
    }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    var modelReady by remember { mutableStateOf<Boolean?>(null) }
    var cameraActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.prepare { ready ->
            modelReady = ready
            if (ready) {
                vm.start(lifecycleOwner, previewView)
                cameraActive = true
                activity?.monitoringStopRequest = { vm.stop() }
            }
        }
    }

    LaunchedEffect(finished) {
        finished?.let { id ->
            vm.consumeSessionFinished()
            onStopped(id)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Leaving the composition (explicit stop navigation, session end,
            // activity destroy) persists the session. PiP does NOT dispose:
            // the activity stays composed while the mini window shows.
            vm.stop()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        when (modelReady) {
            null -> LoadingOverlay()
            false -> ModelErrorDialog(onDismiss = { onStopped(-1L) })
            true -> DrivingScreen(
                monitoringState = uiState.copy(inPipMode = inPip),
                onStop = { vm.stop() },
            )
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(Modifier.fillMaxSize()) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
        )
    }
}

@Composable
private fun ModelErrorDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monitoring unavailable") },
        text = { Text("The detection model could not be loaded. Please try reinstalling the app.") },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } },
    )
}
