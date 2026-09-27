package io.github.abhik9.zzztimer.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.NotificationManager.IMPORTANCE_NONE
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION.SDK_INT_FULL
import android.os.Build.VERSION_CODES.BAKLAVA
import android.os.Build.VERSION_CODES_FULL
import android.os.Bundle
import android.text.format.DateFormat
import io.github.abhik9.zzztimer.EXTRA_DEADLINE
import io.github.abhik9.zzztimer.R
import io.github.abhik9.zzztimer.core.DurationSetting
import io.github.abhik9.zzztimer.core.Timer
import io.github.abhik9.zzztimer.core.TimerDisplay
import io.github.abhik9.zzztimer.settings.SettingsStore
import io.github.abhik9.zzztimer.ui.MainActivity
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The ongoing notification of the running timer, removed by the system when the timer ends
 * ([Notification.Builder.setTimeoutAfter]). It is the source of truth of the timer, see [TimerDisplay].
 */
internal class TimerNotification(
    private val context: Context,
    private val trigger: AndroidSleepTrigger,
    private val settings: SettingsStore,
) : TimerDisplay {

    companion object {
        const val CHANNEL_ID = "timer"
        private const val NOTIFICATION_TAG = "timer"
        private const val ID = 1

        /** Idempotent: the channel is only created once, and later calls never override the user's choices. */
        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), IMPORTANCE_LOW).apply {
                description = context.getString(R.string.notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun isAvailable(): Boolean =
        manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL_ID)?.importance != IMPORTANCE_NONE

    override fun current(): Timer? {
        val notification =
            manager.activeNotifications.firstOrNull { it.tag == NOTIFICATION_TAG && it.id == ID }?.notification ?: return null
        val deadline = notification.extras.getLong(EXTRA_DEADLINE, 0L)
        return if (deadline > 0L) Timer(deadline = deadline, endsAt = notification.`when`) else null
    }

    override fun show(timer: Timer, timeout: Duration) {
        createChannel(context)
        manager.notify(NOTIFICATION_TAG, ID, build(timer, timeout))
    }

    // Cancelling the notification from the app does not send its `deleteIntent`.
    override fun hide() = manager.cancel(NOTIFICATION_TAG, ID)

    private fun build(timer: Timer, timeout: Duration): Notification {
        val increment = settings.minutes(DurationSetting.INCREMENT)
        val decrement = settings.minutes(DurationSetting.DECREMENT)
        val canReduce = timeout > decrement.minutes
        return Notification.Builder(context, CHANNEL_ID)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_tile)
            // A title is required for Live Updates: https://developer.android.com/develop/ui/views/notifications/live-update
            .setContentTitle(context.getString(R.string.app_name))
            .setSubText(context.getString(R.string.notification_ends_at, DateFormat.getTimeFormat(context).format(Date(timer.endsAt))))
            .setShowWhen(true)
            .setWhen(timer.endsAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(timeout.inWholeMilliseconds)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(MainActivity.pendingIntent(context))
            .setDeleteIntent(trigger.deleteIntent(timer))
            .addExtras(Bundle().apply { putLong(EXTRA_DEADLINE, timer.deadline) })
            .addAction(action(TimerActionReceiver.ACTION_EXTEND, context.getString(R.string.action_extend, increment)))
            // Hidden rather than ending the timer without pausing playback; it can become stale as time goes by, which
            // SleepTimer.reduce() handles.
            .apply {
                if (canReduce) addAction(action(TimerActionReceiver.ACTION_REDUCE, context.getString(R.string.action_reduce, decrement)))
            }
            .addAction(action(TimerActionReceiver.ACTION_STOP, context.getString(R.string.action_stop)))
            .apply {
                // Live Updates (promoted ongoing notifications) are only available since Android 16 QPR2.
                if (SDK_INT >= BAKLAVA && SDK_INT_FULL >= VERSION_CODES_FULL.BAKLAVA_1) setRequestPromotedOngoing(true)
            }
            .build()
    }

    private fun action(action: String, title: String) = Notification.Action.Builder(
        Icon.createWithResource(context, R.drawable.ic_tile),
        title,
        TimerActionReceiver.pendingIntent(context, action),
    ).build()
}
