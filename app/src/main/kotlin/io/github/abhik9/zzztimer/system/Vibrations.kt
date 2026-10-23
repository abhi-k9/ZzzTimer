package io.github.abhik9.zzztimer.system

import android.content.Context
import android.media.AudioAttributes
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.abhik9.zzztimer.core.WarningVibration
import io.github.abhik9.zzztimer.diagnostics.diagnostics

/**
 * Vibrates [vibration], when the device can. As an alarm: background apps may only vibrate for alarms, and the timer
 * ending soon is one.
 */
fun Context.vibrate(vibration: WarningVibration) {
    if (vibration == WarningVibration.OFF) return
    try {
        val vibrator = vibrator()
        if (!vibrator.hasVibrator()) return
        val amplitude = if (vibrator.hasAmplitudeControl()) vibration.amplitude else VibrationEffect.DEFAULT_AMPLITUDE
        val effect = VibrationEffect.createOneShot(vibration.millis, amplitude)
        if (SDK_INT >= TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            vibrateAsAlarm(vibrator, effect)
        }
        diagnostics.record { "vibration: $vibration" }
    } catch (e: RuntimeException) {
        diagnostics.warn("vibration: failed", e)
    }
}

@Suppress("DEPRECATION")
private fun Context.vibrator(): Vibrator =
    if (SDK_INT >= S) getSystemService(VibratorManager::class.java).defaultVibrator else getSystemService(Vibrator::class.java)

@Suppress("DEPRECATION")
private fun vibrateAsAlarm(vibrator: Vibrator, effect: VibrationEffect) =
    vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
