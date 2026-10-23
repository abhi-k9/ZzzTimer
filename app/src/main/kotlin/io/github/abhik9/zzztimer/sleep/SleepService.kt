package io.github.abhik9.zzztimer.sleep

import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_NO_CREATE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
import android.media.AudioManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.IntentCompat
import io.github.abhik9.zzztimer.EXTRA_DEADLINE
import io.github.abhik9.zzztimer.R
import io.github.abhik9.zzztimer.core.DeadlineOutcome
import io.github.abhik9.zzztimer.core.ForegroundMode
import io.github.abhik9.zzztimer.core.ServiceLifecycle
import io.github.abhik9.zzztimer.core.ShakeAction
import io.github.abhik9.zzztimer.core.SleepRoutine
import io.github.abhik9.zzztimer.core.StillAwake
import io.github.abhik9.zzztimer.core.WarningVibration
import io.github.abhik9.zzztimer.diagnostics.diagnostics
import io.github.abhik9.zzztimer.settings.SettingsStore
import io.github.abhik9.zzztimer.sleepTimer
import io.github.abhik9.zzztimer.system.ShakeWatcher
import io.github.abhik9.zzztimer.system.vibrate
import io.github.abhik9.zzztimer.timer.TimerNotification
import io.github.abhik9.zzztimer.ui.MainActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the [SleepRoutine] when a timer ends. Started by the [io.github.abhik9.zzztimer.timer.AndroidSleepTrigger].
 * Since Android 17 it is also held in the foreground while a timer runs, see [hold], and it watches the end of a timer
 * for shakes, see [watch]. [ServiceLifecycle] decides when it runs.
 */
class SleepService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val EXTRA_FOREGROUND = "io.github.abhik9.zzztimer.extra.FOREGROUND"
        private const val EXTRA_NOTIFICATION = "io.github.abhik9.zzztimer.extra.NOTIFICATION"
        private const val EXTRA_NOTIFICATION_ID = "io.github.abhik9.zzztimer.extra.NOTIFICATION_ID"
        private const val ACTION_HOLD = "io.github.abhik9.zzztimer.sleep.HOLD"
        private const val ACTION_RELEASE = "io.github.abhik9.zzztimer.sleep.RELEASE"
        private const val ACTION_WATCH = "io.github.abhik9.zzztimer.sleep.WATCH"

        /** Watching ends at the latest that long after the deadline: the longest fade, and more. */
        private val WATCH_AFTER_DEADLINE = 2.minutes

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

        /**
         * Since Android 17, only a foreground service started while the user interacts with the app may lower the volume
         * from the background ([background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio)),
         * which an alarm firing at night isn't. So the service is held in the foreground for the whole timer, with the
         * timer [notification] (of [id]) as its own, and fades out in it at the deadline.
         *
         * It can't be started from the background (e.g. by automation): the sleep then only pauses playback.
         */
        fun hold(context: Context, id: Int, notification: Notification) {
            val intent = intent(context)
                .setAction(ACTION_HOLD)
                .putExtra(EXTRA_NOTIFICATION_ID, id)
                .putExtra(EXTRA_NOTIFICATION, notification)
            try {
                context.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException.
                context.diagnostics.warn("service: can't be held", e)
            }
        }

        /**
         * The timer of [deadline] ends soon: watches its end, until its sleep is done, so that shaking the phone keeps
         * playback going (see [ShakeAction]). Sent by the warning alarm, which may start a foreground service.
         */
        fun watch(context: Context, deadline: Long) {
            try {
                context.startForegroundService(intent(context).setAction(ACTION_WATCH).putExtra(EXTRA_DEADLINE, deadline))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: the alarm wasn't exact.
                context.diagnostics.warn("service: can't watch", e)
            }
        }

        /**
         * Ends a [hold], and the [watch] of the timer end: the service stops, with the timer notification, unless a
         * sleep is running.
         */
        fun release(context: Context) {
            try {
                context.startService(intent(context).setAction(ACTION_RELEASE))
            } catch (e: IllegalStateException) {
                // Refused from the background, when the service isn't running anyway: nothing to release.
                context.diagnostics.record { "service: nothing to release ($e)" }
            }
        }
    }

    /**
     * A failing sleep is recorded instead of crashing the app: the routine has already paused playback and restored the
     * volume as far as it could.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error -> diagnostics.warn("sleep: failed", error) },
    )

    /** Sleeps run one at a time, so that a volume is never restored in the middle of another fade. */
    private val mutex = Mutex()

    // Main thread only.
    private val lifecycle = ServiceLifecycle()
    private var lastStartId = 0

    /** The timer notification of the [hold], to stay in the foreground with it. */
    private var heldNotification: Pair<Int, Notification>? = null
    private var shakeWatcher: ShakeWatcher? = null
    private val handler = Handler(Looper.getMainLooper())
    private var watchExpiry: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_HOLD -> hold(intent, startId)
            ACTION_RELEASE -> {
                diagnostics.record { "service: release #$startId, running=${lifecycle.runningSleeps}" }
                lifecycle.onRelease()
                heldNotification = null
                afterChange()
            }
            ACTION_WATCH -> watch(intent, startId)
            else -> onDeadline(intent, startId)
        }
        return START_NOT_STICKY
    }

    private fun hold(intent: Intent, startId: Int) {
        // startForeground() must be called right away, whatever the extras.
        val notification = IntentCompat.getParcelableExtra(intent, EXTRA_NOTIFICATION, Notification::class.java)
        val id = if (notification != null) intent.getIntExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID) else NOTIFICATION_ID
        lifecycle.onHold(enterForeground(id, notification ?: sleepingNotification(), ForegroundMode.LONG))
        heldNotification = if (lifecycle.held && notification != null) id to notification else null
        diagnostics.record { "service: hold #$startId, held=${lifecycle.held}" }
        afterChange()
    }

    private fun watch(intent: Intent, startId: Int) {
        val deadline = intent.getLongExtra(EXTRA_DEADLINE, 0L)
        // startForeground() must be called right away: with the timer notification when held.
        val (id, notification) = heldNotification ?: (NOTIFICATION_ID to watchNotification())
        val inForeground = enterForeground(id, notification, ForegroundMode.LONG)
        val settings = SettingsStore.from(this)
        if (inForeground && settings.shakeEnabled) {
            lifecycle.onWatch(deadline)
            if (shakeWatcher == null) {
                shakeWatcher = ShakeWatcher(this, settings.shakeLevel, onShake = ::onShake).takeIf { it.start() }
            }
            watchExpiry?.let(handler::removeCallbacks)
            watchExpiry = Runnable {
                lifecycle.onWatchExpired(deadline)
                afterChange()
            }.also { handler.postDelayed(it, deadline - SystemClock.elapsedRealtime() + WATCH_AFTER_DEADLINE.inWholeMilliseconds) }
        }
        diagnostics.record { "service: watch #$startId, deadline=$deadline, foreground=$inForeground, shakes=${shakeWatcher != null}" }
        afterChange()
    }

    private fun onShake() {
        val action = lifecycle.onShake(current = sleepTimer().current()?.deadline)
        diagnostics.record { "shake: $action" }
        if (action != ShakeAction.NONE) vibrate(WarningVibration.LIGHT)
        when (action) {
            ShakeAction.EXTEND -> sleepTimer().extend()
            ShakeAction.STOP_SLEEP -> {
                // Playback goes on, at its volume from before the fade, with a new timer.
                scope.coroutineContext.job.children.forEach { it.cancel(StillAwake()) }
                sleepTimer().start(SettingsStore.from(this).timerSettings().increment)
            }
            ShakeAction.NONE -> Unit
        }
        afterChange()
    }

    private fun onDeadline(intent: Intent?, startId: Int) {
        val foreground = intent?.getBooleanExtra(EXTRA_FOREGROUND, false) == true
        val deadline = intent?.getLongExtra(EXTRA_DEADLINE, 0L) ?: 0L
        diagnostics.record { "service: deadline #$startId, foreground=$foreground, held=${lifecycle.held}, deadline=$deadline" }
        // Must be called right away when started with `startForegroundService()`. A held service keeps its foreground
        // type, which may fade out (see hold()). Its timer notification is replaced: the timer has ended.
        lifecycle.onDeadline(requested = foreground)?.let { enterForeground(NOTIFICATION_ID, sleepingNotification(), it) }
        heldNotification = null
        // Timer operations run on the main thread, see SleepTimer.
        if (sleepTimer().onDeadline(deadline) == DeadlineOutcome.SLEEP) sleep(deadline) else lifecycle.onNoSleep()
        afterChange()
    }

    private fun sleep(deadline: Long) {
        lifecycle.onSleepStarted()
        scope.launch {
            try {
                mutex.withLock {
                    val settings = SettingsStore.from(this@SleepService)
                    val audio = AndroidMediaAudio(getSystemService(AudioManager::class.java), diagnostics)
                    val report = SleepRoutine(
                        audio = audio,
                        media = { SessionPlayingMedia.find(this@SleepService, diagnostics) },
                        fade = settings.fadeSeconds.seconds,
                        log = diagnostics,
                    ).run()
                    diagnostics.record { "sleep: $report" }
                    if (deadline > 0 && deadline == settings.testDeadline) {
                        settings.testDeadline = 0
                        TestReportNotification.post(this@SleepService, report)
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    lifecycle.onSleepFinished()
                    afterChange()
                }
            }
        }
    }

    /** Applies a change of the [lifecycle]: stops watching shakes, or the service, when not needed anymore. */
    private fun afterChange() {
        if (lifecycle.watched == null) {
            shakeWatcher?.stop()
            shakeWatcher = null
            watchExpiry?.let(handler::removeCallbacks)
            watchExpiry = null
        }
        stopWhenIdle()
    }

    /**
     * Stops the service (and removes its notification) once every sleep is done and nothing else keeps it: a signal
     * that does not need one (e.g. a dismissal) must not interrupt a running fade.
     */
    private fun stopWhenIdle() {
        if (!lifecycle.canStop) return
        diagnostics.record { "service: stop #$lastStartId" }
        stopSelf(lastStartId)
    }

    /** `shortService` foreground services must stop within seconds after this callback, or the app is killed. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        diagnostics.record { "service: timeout #$startId" }
        stopSelf()
    }

    override fun onDestroy() {
        shakeWatcher?.stop()
        handler.removeCallbacksAndMessages(null)
        // Cancels a running fade: the routine still pauses playback and restores the volume.
        scope.cancel()
        super.onDestroy()
    }

    /** While watching the end of a timer without hold, e.g. before Android 17. */
    private fun watchNotification(): Notification = Notification.Builder(this, TimerNotification.CHANNEL_ID)
        .setCategory(Notification.CATEGORY_SERVICE)
        .setSmallIcon(R.drawable.ic_tile)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.notification_ends_soon))
        .setContentIntent(MainActivity.pendingIntent(this))
        .setOngoing(true)
        .build()

    private fun sleepingNotification(): Notification = Notification.Builder(this, TimerNotification.CHANNEL_ID)
        .setCategory(Notification.CATEGORY_SERVICE)
        .setSmallIcon(R.drawable.ic_tile)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.notification_sleeping))
        .setProgress(0, 0, true)
        .setOngoing(true)
        .build()

    /**
     * As a media playback foreground service for [ForegroundMode.LONG], a short service otherwise (only needed since
     * Android 17).
     * @return whether the service is now in the foreground.
     */
    private fun enterForeground(id: Int, notification: Notification, mode: ForegroundMode): Boolean = try {
        when {
            SDK_INT >= UPSIDE_DOWN_CAKE -> {
                val type = when (mode) {
                    ForegroundMode.LONG -> FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    ForegroundMode.SHORT -> FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                }
                startForeground(id, notification, type)
            }
            SDK_INT >= Q -> startForeground(id, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else -> startForeground(id, notification)
        }
        true
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException. Still try to pause playback: it does not require one.
        diagnostics.warn("service: foreground not allowed", e)
        false
    } catch (e: SecurityException) {
        diagnostics.warn("service: foreground type not allowed", e)
        false
    }
}
