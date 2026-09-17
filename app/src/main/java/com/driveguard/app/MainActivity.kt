package com.driveguard.app

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.LocalActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.driveguard.app.data.AppSettings
import com.driveguard.app.ui.HistoryViewModel
import com.driveguard.app.ui.SettingsViewModel
import com.driveguard.app.ui.navigation.Routes
import com.driveguard.app.ui.screens.AboutScreen
import com.driveguard.app.ui.screens.DrivingRoute
import com.driveguard.app.ui.screens.HistoryScreen
import com.driveguard.app.ui.screens.HomeScreen
import com.driveguard.app.ui.screens.OnboardingScreen
import com.driveguard.app.ui.screens.PreCheckScreen
import com.driveguard.app.ui.screens.PrivacyScreen
import com.driveguard.app.ui.screens.SettingsScreen
import com.driveguard.app.ui.screens.SummaryScreen
import com.driveguard.app.ui.theme.DriveGuardTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_STOP_MONITORING = "com.driveguard.app.action.PIP_STOP_MONITORING"
        private const val REQ_STOP_MONITORING = 1001
    }

    private var permissionCallback: ((Boolean) -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val cameraGranted = grants[Manifest.permission.CAMERA] == true
            val cb = permissionCallback
            permissionCallback = null
            cb?.invoke(cameraGranted)
        }

    // ---------------------------------------------------------------------
    // Picture-in-picture support (mini floating monitoring window)
    // ---------------------------------------------------------------------

    /** True while a monitoring session is active (set by DrivingRoute). */
    @Volatile var monitoringActive = false

    /** Pending explicit-stop hook set by DrivingRoute while monitoring. */
    var monitoringStopRequest: (() -> Unit)? = null

    private val _pipMode = mutableStateOf(false)

    /** Compose-visible PiP flag; DrivingRoute reads it to swap layouts. */
    val pipMode: State<Boolean> get() = _pipMode

    /** Build PiP params (9:16, auto-enter on S+, stop remote action). */
    fun updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(9, 16))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(monitoringActive)
        }
        if (monitoringActive) {
            builder.setActions(
                listOf(
                    RemoteAction(
                        Icon.createWithResource(this, R.drawable.ic_stat_monitor),
                        getString(R.string.pip_action_stop),
                        getString(R.string.pip_action_stop),
                        PendingIntent.getActivity(
                            this,
                            REQ_STOP_MONITORING,
                            Intent(this, MainActivity::class.java).apply {
                                action = ACTION_STOP_MONITORING
                                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            },
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    ),
                ),
            )
        }
        try {
            setPictureInPictureParams(builder.build())
        } catch (_: IllegalStateException) {
            // Can throw before the activity is fully resumed; harmless.
        }
    }

    /** Explicit entry point used by Back while monitoring is active. */
    fun requestEnterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!monitoringActive) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) return
        try {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build(),
            )
        } catch (_: IllegalStateException) {
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPip: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        _pipMode.value = isInPip
        if (!isInPip) {
            // Returned to full screen (tap on the mini window or expanded).
            // Nothing to rebind: the camera stayed composed the whole time.
            updatePipParams()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Remote "Stop" action from the PiP window.
        if (intent.action == ACTION_STOP_MONITORING) {
            monitoringStopRequest?.invoke()
            monitoringStopRequest = null
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Pre-S devices: Home/app-switch while monitoring → enter PiP
        // (auto-enter covers S+). A real leave (e.g. another app steals
        // focus) also lands here; PiP keeps monitoring alive.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && monitoringActive) {
            requestEnterPip()
        }
    }

    override fun onStop() {
        super.onStop()
        // If we end up fully backgrounded without PiP (PiP unsupported on
        // this device), the camera-type foreground service keeps monitoring
        // alive; the notification remains the visible cue.
        if (!isInPictureInPictureMode) {
            // no-op: monitoring intentionally continues in background
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsVm: SettingsViewModel = viewModel()
            val settings by settingsVm.settings.collectAsStateWithLifecycle()
            DriveGuardTheme(appearanceMode = settings.appearance) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DriveGuardApp(settingsVm, settings)
                }
            }
        }
    }

    /** Requests CAMERA (+notifications on 33+); resumes via callback. */
    fun requestCriticalPermissions(onResult: (cameraGranted: Boolean) -> Unit) {
        val needed = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onResult(true)
        } else {
            permissionCallback = onResult
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@Composable
fun DriveGuardApp(settingsVm: SettingsViewModel, settings: AppSettings) {
    val navController = rememberNavController()
    val historyVm: HistoryViewModel = viewModel()
    val context = LocalContext.current

    // Permission request handled idiomatically at composition level.
    var permissionContinuation by remember {
        mutableStateOf<((Boolean) -> Unit)?>(null)
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        permissionContinuation?.invoke(grants[Manifest.permission.CAMERA] == true)
        permissionContinuation = null
    }
    fun requestCriticalPermissions(onResult: (Boolean) -> Unit) {
        val needed = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) onResult(true) else {
            permissionContinuation = onResult
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    val startDest = if (settings.onboardingCompleted) Routes.HOME else Routes.ONBOARDING

    NavHost(navController, startDestination = startDest) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                settingsVm.completeOnboarding()
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }

        composable(Routes.HOME) {
            val sessions by historyVm.sessions.collectAsStateWithLifecycle()
            HomeScreen(
                latestSession = sessions.firstOrNull(),
                onStartDriving = {
                    requestCriticalPermissions { granted ->
                        if (granted) {
                            navController.navigate(
                                if (settings.precheckDismissed) Routes.DRIVING else Routes.PRECHECK
                            )
                        }
                        // On denial we stay on home — the camera is only needed
                        // for monitoring, and each camera screen explains why.
                    }
                },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }

        composable(Routes.PRECHECK) {
            PreCheckScreen(
                onStartMonitoring = {
                    settingsVm.dismissPrecheck()
                    navController.navigate(Routes.DRIVING) { popUpTo(Routes.HOME) }
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(Routes.DRIVING) {
            DrivingRoute(onStopped = { sessionId ->
                if (sessionId >= 0) {
                    navController.navigate(Routes.summary(sessionId)) {
                        popUpTo(Routes.HOME)
                    }
                } else {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            })
        }

        composable(
            Routes.SUMMARY,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("sessionId") ?: -1L
            val sessions by historyVm.sessions.collectAsStateWithLifecycle()
            SummaryScreen(
                session = sessions.firstOrNull { it.id == id },
                onDone = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }

        composable(Routes.HISTORY) {
            val sessions by historyVm.sessions.collectAsStateWithLifecycle()
            HistoryScreen(
                sessions = sessions,
                onDelete = { historyVm.delete(it) },
                onClearAll = { historyVm.clearAll() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                onSensitivityChange = { settingsVm.setSensitivity(it) },
                onSoundChange = { settingsVm.setSound(it) },
                onVibrationChange = { settingsVm.setVibration(it) },
                onAppearanceChange = { settingsVm.setAppearance(it) },
                onClearHistory = { historyVm.clearAll() },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }

        composable(Routes.ABOUT) {
            val context = LocalContext.current
            AboutScreen(
                appVersion = try {
                    context.packageManager
                        .getPackageInfo(context.packageName, 0).versionName ?: "?"
                } catch (e: Exception) { "?" },
            )
        }

        composable(Routes.PRIVACY) { PrivacyScreen() }
    }
}
