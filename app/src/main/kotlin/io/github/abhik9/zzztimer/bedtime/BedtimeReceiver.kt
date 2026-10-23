package io.github.abhik9.zzztimer.bedtime

import android.app.AlarmManager
import android.app.AlarmManager.RTC_WAKEUP
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_NO_CREATE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import io.github.abhik9.zzztimer.core.Bedtime
import io.github.abhik9.zzztimer.diagnostics.diagnostics
import io.github.abhik9.zzztimer.settings.SettingsStore
import io.github.abhik9.zzztimer.sleepTimer
import io.github.abhik9.zzztimer.system.exactAlarmsAllowed
import java.time.ZonedDateTime

/**
 * Bedtime, see [Bedtime]: checks when its alarm fires, and schedules it again whenever it may have been lost (after a
 * reboot or an update) or moved (time or time zone changes). Not exported: the system still delivers its broadcasts.
 */
class BedtimeReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_CHECK = "io.github.abhik9.zzztimer.bedtime.CHECK"

        private fun pendingIntent(context: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, BedtimeReceiver::class.java).setAction(ACTION_CHECK),
            FLAG_IMMUTABLE or flags,
        )

        /** Schedules the next check, or cancels it when bedtime is off. */
        fun schedule(context: Context) {
            val settings = SettingsStore.from(context)
            if (settings.bedtimeEnabled) {
                schedule(context, Bedtime(settings.bedtimeMinute).nextCheck(ZonedDateTime.now()))
            } else {
                pendingIntent(context, FLAG_NO_CREATE)?.let(context.getSystemService(AlarmManager::class.java)::cancel)
            }
        }

        private fun schedule(context: Context, at: ZonedDateTime) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            val operation = checkNotNull(pendingIntent(context, FLAG_UPDATE_CURRENT))
            val millis = at.toInstant().toEpochMilli()
            try {
                if (context.exactAlarmsAllowed()) {
                    alarms.setExactAndAllowWhileIdle(RTC_WAKEUP, millis, operation)
                } else {
                    // May come a few minutes late: good enough to find media playing.
                    alarms.setAndAllowWhileIdle(RTC_WAKEUP, millis, operation)
                }
                context.diagnostics.record { "bedtime: next check at $at" }
            } catch (e: SecurityException) {
                context.diagnostics.warn("bedtime: alarm denied", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        context.diagnostics.record { "bedtime: ${intent.action}" }
        if (intent.action == ACTION_CHECK) check(context) else schedule(context)
    }

    private fun check(context: Context) {
        val settings = SettingsStore.from(context)
        if (!settings.bedtimeEnabled) return
        val timer = context.sleepTimer()
        val playing = context.getSystemService(AudioManager::class.java).isMusicActive
        val check = Bedtime(settings.bedtimeMinute).check(ZonedDateTime.now(), timerRunning = timer.current() != null, playing = playing)
        context.diagnostics.record { "bedtime: playing=$playing, $check" }
        if (check.startTimer) {
            val result = timer.start()
            context.diagnostics.record { "bedtime: $result" }
        }
        schedule(context, check.next)
    }
}
