package io.github.abhik9.zzztimer.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.abhik9.zzztimer.core.StartResult
import io.github.abhik9.zzztimer.core.TimerCommand
import io.github.abhik9.zzztimer.core.durationOfSeconds
import io.github.abhik9.zzztimer.settings.SettingsStore
import io.github.abhik9.zzztimer.sleepTimer
import io.github.abhik9.zzztimer.system.message
import io.github.abhik9.zzztimer.system.toast
import kotlin.time.Duration

/**
 * Public automation API, used by tools like Tasker or `adb` (see README). Any app can send these broadcasts, so:
 * - it can be disabled from the app settings,
 * - it only exposes timer operations (no settings, no data),
 * - every input is validated and clamped.
 */
class AutomationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AutomationReceiver"
        private const val PREFIX = "io.github.abhik9.zzztimer.action."
        const val ACTION_START = PREFIX + "START"
        const val ACTION_STOP = PREFIX + "STOP"
        const val ACTION_TOGGLE = PREFIX + "TOGGLE"
        const val ACTION_UPDATE = PREFIX + "UPDATE"
        const val ACTION_INCREMENT = PREFIX + "INCREMENT"
        const val ACTION_DECREMENT = PREFIX + "DECREMENT"

        /** Duration in seconds, as a `long` or an `int` extra. */
        const val EXTRA_DURATION = "duration"

        fun parse(action: String?, durationSeconds: Long?): TimerCommand? {
            val duration = durationSeconds?.let(::durationOfSeconds)
            return when (action) {
                ACTION_START -> TimerCommand.Start(duration)
                ACTION_STOP -> TimerCommand.Stop
                ACTION_TOGGLE -> TimerCommand.Toggle
                ACTION_UPDATE -> TimerCommand.Adjust(duration ?: Duration.ZERO)
                ACTION_INCREMENT -> TimerCommand.Extend
                ACTION_DECREMENT -> TimerCommand.Reduce
                else -> null
            }
        }

        /**
         * Extras of an external intent are untrusted: unparcelling them can throw.
         * @return the duration extra in seconds, or `null` when missing or invalid.
         */
        private fun Intent.durationSeconds(): Long? = try {
            when {
                !hasExtra(EXTRA_DURATION) -> null
                else -> getLongExtra(EXTRA_DURATION, 0L).takeIf { it != 0L } ?: getIntExtra(EXTRA_DURATION, 0).toLong()
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Invalid extras", e)
            null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!SettingsStore.from(context).automationEnabled) {
            Log.i(TAG, "Automation is disabled, ignoring ${intent.action}")
            return
        }
        val command = parse(intent.action, intent.durationSeconds()) ?: return
        val result = context.sleepTimer().execute(command)
        if (result is StartResult.Blocked) context.toast(result.requirement.message)
    }
}
