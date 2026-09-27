package io.github.abhik9.zzztimer.timer

import android.app.AlarmManager
import android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP
import android.app.PendingIntent
import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.CINNAMON_BUN
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.abhik9.zzztimer.core.SleepTrigger
import io.github.abhik9.zzztimer.core.Timer
import io.github.abhik9.zzztimer.diagnostics.diagnostics
import io.github.abhik9.zzztimer.sleep.SleepService

/**
 * Android implementations of [SleepTrigger], which also provide the `deleteIntent` of the timer notification.
 */
internal sealed interface AndroidSleepTrigger : SleepTrigger {

    /** `deleteIntent` of the notification, sent when it times out or is dismissed by the user. */
    fun deleteIntent(timer: Timer): PendingIntent

    companion object {
        /**
         * Android 17 [background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio)
         * prevents lowering the volume from the background.
         */
        fun create(context: Context): AndroidSleepTrigger =
            if (SDK_INT >= CINNAMON_BUN) ExactAlarmTrigger(context) else NotificationTimeoutTrigger(context)
    }
}

/**
 * Relies on the notification timeout: its `deleteIntent` directly starts [SleepService] in the background, which the
 * system allows while it sends the intent. No permission needed.
 */
private class NotificationTimeoutTrigger(private val context: Context) : AndroidSleepTrigger {
    override fun isPermitted() = true
    override fun arm(timer: Timer) = true
    override fun disarm() = Unit
    override fun deleteIntent(timer: Timer) = SleepService.pendingIntent(context, timer.deadline, foreground = false)
}

/**
 * Since Android 17, lowering the volume requires a foreground service, which a notification `deleteIntent` is not
 * allowed to start. An exact alarm starts [SleepService] as a foreground service instead, and the `deleteIntent` only
 * detects user dismissals (see [io.github.abhik9.zzztimer.core.SleepTimer.onDeadline]).
 */
@RequiresApi(CINNAMON_BUN)
private class ExactAlarmTrigger(private val context: Context) : AndroidSleepTrigger {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun isPermitted() = alarms.canScheduleExactAlarms()

    override fun arm(timer: Timer): Boolean {
        if (!alarms.canScheduleExactAlarms()) {
            context.diagnostics.record { "alarm: not allowed" }
            return false
        }
        return try {
            val operation = SleepService.pendingIntent(context, timer.deadline, foreground = true)
            alarms.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, timer.deadline, operation)
            context.diagnostics.record { "alarm: armed for ${timer.deadline}" }
            true
        } catch (e: SecurityException) {
            // The permission has been revoked in the meantime.
            Log.w(TAG, "Exact alarm denied", e)
            context.diagnostics.record { "alarm: denied: $e" }
            false
        }
    }

    override fun disarm() {
        val operation = SleepService.existingPendingIntent(context, foreground = true)
        context.diagnostics.record { "alarm: disarmed (armed=${operation != null})" }
        operation?.let(alarms::cancel)
    }

    override fun deleteIntent(timer: Timer) = TimerActionReceiver.dismissIntent(context, timer.deadline)

    private companion object {
        const val TAG = "ExactAlarmTrigger"
    }
}
