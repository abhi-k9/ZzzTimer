package io.github.abhik9.zzztimer.timer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.abhik9.zzztimer.core.StartResult
import io.github.abhik9.zzztimer.sleepTimer
import io.github.abhik9.zzztimer.system.message
import io.github.abhik9.zzztimer.system.toast

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
        private const val EXTRA_DEADLINE = "io.github.abhik9.zzztimer.extra.DEADLINE"

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
    }

    override fun onReceive(context: Context, intent: Intent) {
        val timer = context.sleepTimer()
        val result = when (intent.action) {
            ACTION_EXTEND -> timer.extend()
            ACTION_REDUCE -> timer.reduce()
            ACTION_STOP -> timer.stop()
            // The sleep itself is triggered by the exact alarm: only dismissals matter here.
            ACTION_DISMISSED -> timer.onDeadline(intent.getLongExtra(EXTRA_DEADLINE, 0L))
            else -> null
        }
        if (result is StartResult.Blocked) context.toast(result.requirement.message)
    }
}
