package io.github.abhik9.zzztimer.timer

import android.app.AlarmManager
import android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP
import android.content.Context
import io.github.abhik9.zzztimer.diagnostics.diagnostics
import io.github.abhik9.zzztimer.system.exactAlarmsAllowed

/**
 * The alarm warning that a timer ends soon, see [io.github.abhik9.zzztimer.core.SleepTimer.onWarning]. Shared by every
 * [AndroidSleepTrigger].
 */
internal object WarningAlarm {

    /** Schedules the warning of the timer of [deadline] at [warnAt], or cancels it when `null`. */
    fun update(context: Context, deadline: Long, warnAt: Long?) {
        if (warnAt == null) return cancel(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val operation = TimerActionReceiver.warningIntent(context, deadline)
        try {
            if (context.exactAlarmsAllowed()) {
                alarms.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, warnAt, operation)
            } else {
                // May come late, or not before the deadline: it is then ignored.
                alarms.setAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, warnAt, operation)
            }
            context.diagnostics.record { "warning: armed for $warnAt" }
        } catch (e: SecurityException) {
            // The permission has been revoked in the meantime.
            context.diagnostics.warn("warning: denied", e)
        }
    }

    fun cancel(context: Context) {
        TimerActionReceiver.existingWarningIntent(context)?.let(context.getSystemService(AlarmManager::class.java)::cancel)
    }
}
