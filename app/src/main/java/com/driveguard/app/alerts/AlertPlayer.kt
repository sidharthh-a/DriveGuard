package com.driveguard.app.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Plays alert events: alarm sound (with proper audio focus) and vibration.
 * Lives in the foreground-service layer; created/stopped with monitoring.
 *
 * DND/silent-mode behaviour (documented limitation, Phase 12):
 *  - In normal ring mode the alarm always plays at ALARM volume.
 *  - In silent mode the alarm is suppressed by the platform (ALARM stream is
 *    muted); vibration still runs if enabled. We do NOT bypass DND — waking a
 *    drowsy driver is desirable but bypassing DND without user opt-in is not
 *    appropriate for a Play-published app. This is documented in Settings.
 */
class AlertPlayer(
    private val context: Context,
    private val audioManager: AudioManager,
    private val vibrator: Vibrator?,
) {
    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    init {
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(alarmAttributes)
            .setWillPauseWhenDucked(false)
            .build()
    }

    fun play(event: AlertEvent, soundEnabled: Boolean, vibrationEnabled: Boolean) {
        if (vibrationEnabled) {
            vibrate(event.level)
        }
        if (soundEnabled) {
            playAlarm()
        }
    }

    private fun vibrate(level: AlertLevel) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val effect = when (level) {
            AlertLevel.LEVEL_1 -> VibrationEffect.createWaveform(longArrayOf(0, 220, 180, 220), -1)
            AlertLevel.LEVEL_2 -> VibrationEffect.createWaveform(longArrayOf(0, 320, 140, 320, 140, 320), -1)
            AlertLevel.LEVEL_3 -> VibrationEffect.createWaveform(
                longArrayOf(0, 420, 100, 420, 100, 420, 100, 420), -1,
            )
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
                vib.vibrate(effect, attrs)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build(),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "vibration failed", e)
        }
    }

    private fun playAlarm() {
        var player: MediaPlayer? = null
        try {
            stopAlarm()
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer()
            mediaPlayer = player
            player.setDataSource(context, uri)
            player.setAudioAttributes(alarmAttributes)
            player.isLooping = false
            // Request transient alarm focus; suppresses media ducking conflicts.
            focusRequest?.let { audioManager.requestAudioFocus(it) }
            player.setOnCompletionListener {
                stopAlarm()
            }
            player.prepare() // synchronous; small local file — acceptable on service thread
            player.start()
        } catch (e: Exception) {
            Log.w(TAG, "alarm playback failed", e)
            player?.release()
            mediaPlayer = null
        }
    }

    /**
     * Cuts everything an alert is currently doing. Used when the driver's eyes
     * are seen open again: the alarm must not outlive the drowsiness that
     * caused it, so a ringtone still playing mid-note is stopped here rather
     * than left to run to its end.
     */
    fun silence() {
        stopAlarm()
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "vibration cancel failed", e)
        }
    }

    fun stopAlarm() {
        try {
            mediaPlayer?.let { p ->
                if (p.isPlaying) p.stop()
                p.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopAlarm failed", e)
        } finally {
            mediaPlayer = null
        }
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    /** Test hook: quick vibration pattern for the pre-check screen. */
    fun testVibrate() {
        vibrate(AlertLevel.LEVEL_2)
    }

    fun release() {
        stopAlarm()
    }

    companion object {
        private const val TAG = "AlertPlayer"

        fun create(context: Context): AlertPlayer {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            return AlertPlayer(context, audioManager, vibrator)
        }
    }
}
