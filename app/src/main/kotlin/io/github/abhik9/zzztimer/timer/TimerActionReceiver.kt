package io.github.abhik9.zzztimer.timer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.abhik9.zzztimer.EXTRA_DEADLINE
import io.github.abhik9.zzztimer.diagnostics.diagnostics
import io.github.abhik9.zzztimer.settings.SettingsStore
import io.github.abhik9.zzztimer.sleep.SleepService
import io.github.abhik9.zzztimer.sleepTimer
import io.github.abhik9.zzztimer.system.reportBlocked
import io.github.abhik9.zzztimer.system.vibrate

/**
 * Handles the notification actions. Not exported: only reachable through the app's own [PendingIntent]s.
 */
class TimerActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_EXTEND = "io.github.abhik9.zzztimer.timer.EXTEND"
        const val ACTION_REDUCE = "io.github.abhik9.zzztimer.timer.REDUCE"
        const val ACTION_STOP = "io.github.abhik9.zzztimer.timer.STOP"

        /** The notification has been removed by a timeout or the user, see [dismissIntent]. */
        private const val ACTION_DISMISSED = "io.github.abhik9.zzztimer.timer.DISMISSED"

        /** The timer ends soon, see [warningIntent]. */
        private const val ACTION_WARN = "io.github.abhik9.zzztimer.timer.WARN"

        private fun intent(context: Context, action: String) = Intent(context, TimerActionReceiver::class.java).setAction(action)

        fun pendingIntent(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(context, 0, intent(context, action), PendingIntent.FLAG_IMMUTABLE)

        /**
         * There is only ever one such [PendingIntent]: [PendingIntent.FLAG_UPDATE_CURRENT] updates the deadline of the
         * instance already referenced by the posted notification.
         */
        fun dismissIntent(context: Context, deadline: Long): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent(context, ACTION_DISMISSED).putExtra(EXTRA_DEADLINE, deadline),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Sent by the [WarningAlarm]. There is only ever one, like the [dismissIntent]. */
        fun warningIntent(context: Context, deadline: Long): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent(context, ACTION_WARN).putExtra(EXTRA_DEADLINE, deadline),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun existingWarningIntent(context: Context): PendingIntent? = PendingIntent.getBroadcast(
            context,
            0,
            intent(context, ACTION_WARN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        context.diagnostics.record { "notification: ${intent.action}" }
        val timer = context.sleepTimer()
        when (intent.action) {
            ACTION_EXTEND -> context.reportBlocked(timer.extend())
            ACTION_REDUCE -> context.reportBlocked(timer.reduce())
            ACTION_STOP -> timer.stop()
            // The sleep itself is triggered by the exact alarm: only dismissals matter here.
            ACTION_DISMISSED -> timer.onDeadline(intent.getLongExtra(EXTRA_DEADLINE, 0L))
            ACTION_WARN -> {
                val deadline = intent.getLongExtra(EXTRA_DEADLINE, 0L)
                if (timer.onWarning(deadline)) {
                    val settings = SettingsStore.from(context)
                    context.vibrate(settings.warningVibration)
                    if (settings.shakeEnabled) SleepService.watch(context, deadline)
                }
            }
        }
    }
}
