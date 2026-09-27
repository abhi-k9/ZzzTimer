package io.github.abhik9.zzztimer.sleep

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_NO_CREATE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
import android.media.AudioManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.IBinder
import io.github.abhik9.zzztimer.EXTRA_DEADLINE
import io.github.abhik9.zzztimer.R
import io.github.abhik9.zzztimer.core.DeadlineOutcome
import io.github.abhik9.zzztimer.core.SleepRoutine
import io.github.abhik9.zzztimer.diagnostics.diagnostics
import io.github.abhik9.zzztimer.sleepTimer
import io.github.abhik9.zzztimer.timer.TimerNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs the [SleepRoutine] when a timer ends. Started by the [io.github.abhik9.zzztimer.timer.AndroidSleepTrigger].
 */
class SleepService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val EXTRA_FOREGROUND = "io.github.abhik9.zzztimer.extra.FOREGROUND"

        private fun intent(context: Context) = Intent(context, SleepService::class.java)

        private fun pendingIntent(context: Context, intent: Intent, foreground: Boolean, flags: Int): PendingIntent? =
            if (foreground) {
                PendingIntent.getForegroundService(context, 0, intent, FLAG_IMMUTABLE or flags)
            } else {
                PendingIntent.getService(context, 0, intent, FLAG_IMMUTABLE or flags)
            }

        /**
         * There is only ever one such [PendingIntent] per [foreground] value: [FLAG_UPDATE_CURRENT] updates the
         * deadline of the instance already referenced by the notification or the alarm.
         * @param deadline `elapsedRealtime` based deadline of the timer.
         * @param foreground whether the service is started as a foreground service, and must call [startForeground].
         */
        fun pendingIntent(context: Context, deadline: Long, foreground: Boolean): PendingIntent {
            val intent = intent(context).putExtra(EXTRA_DEADLINE, deadline).putExtra(EXTRA_FOREGROUND, foreground)
            return checkNotNull(pendingIntent(context, intent, foreground, FLAG_UPDATE_CURRENT))
        }

        fun existingPendingIntent(context: Context, foreground: Boolean): PendingIntent? =
            pendingIntent(context, intent(context), foreground, FLAG_NO_CREATE)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Sleeps run one at a time, so that a volume is never restored in the middle of another fade. */
    private val mutex = Mutex()

    // Main thread only.
    private var runningSleeps = 0
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val foreground = intent?.getBooleanExtra(EXTRA_FOREGROUND, false) == true
        val deadline = intent?.getLongExtra(EXTRA_DEADLINE, 0L) ?: 0L
        diagnostics.record { "service: start #$startId, foreground=$foreground, deadline=$deadline, running=$runningSleeps" }
        // Must be called right away when started with `startForegroundService()`.
        if (foreground) enterForeground()
        // Timer operations run on the main thread, see SleepTimer.
        if (sleepTimer().onDeadline(deadline) == DeadlineOutcome.SLEEP) sleep()
        stopWhenIdle()
        return START_NOT_STICKY
    }

    private fun sleep() {
        runningSleeps++
        scope.launch {
            try {
                mutex.withLock {
                    SleepRoutine(AndroidMediaAudio(getSystemService(AudioManager::class.java), diagnostics), log = diagnostics).run()
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    runningSleeps--
                    stopWhenIdle()
                }
            }
        }
    }

    /**
     * Stops the service (and removes its notification) once every sleep is done: a signal that does not need one (e.g. a
     * dismissal) must not interrupt a running fade.
     */
    private fun stopWhenIdle() {
        if (runningSleeps > 0) return
        diagnostics.record { "service: stop #$lastStartId" }
        stopSelf(lastStartId)
    }

    /** `shortService` foreground services must stop within seconds after this callback, or the app is killed. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        diagnostics.record { "service: timeout #$startId" }
        stopSelf()
    }

    override fun onDestroy() {
        // Cancels a running fade: the routine still pauses playback and restores the volume.
        scope.cancel()
        super.onDestroy()
    }

    private fun enterForeground() {
        if (SDK_INT < UPSIDE_DOWN_CAKE) return
        val notification = Notification.Builder(this, TimerNotification.CHANNEL_ID)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_sleeping))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
        try {
            startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // Still try to pause playback: it does not require a foreground service.
            diagnostics.warn("service: foreground not allowed", e)
        }
    }
}
