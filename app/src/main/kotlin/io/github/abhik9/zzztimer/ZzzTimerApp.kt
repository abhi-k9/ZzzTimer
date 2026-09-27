package io.github.abhik9.zzztimer

import android.app.Application
import android.content.Context
import android.os.SystemClock
import io.github.abhik9.zzztimer.core.DeviceClock
import io.github.abhik9.zzztimer.core.SleepTimer
import io.github.abhik9.zzztimer.diagnostics.diagnostics
import io.github.abhik9.zzztimer.settings.SettingsStore
import io.github.abhik9.zzztimer.tile.requestTileUpdate
import io.github.abhik9.zzztimer.timer.AndroidSleepTrigger
import io.github.abhik9.zzztimer.timer.TimerNotification

/** Extra holding a [io.github.abhik9.zzztimer.core.Timer.deadline], in intents and in the timer notification. */
internal const val EXTRA_DEADLINE = "io.github.abhik9.zzztimer.extra.DEADLINE"

private object SystemDeviceClock : DeviceClock {
    override fun wallMillis() = System.currentTimeMillis()
    override fun elapsedMillis() = SystemClock.elapsedRealtime()
}

/**
 * Entry point of all timer operations, wired to their Android implementations.
 * Cheap to create: the state lives in the system (notification, alarm) and the preferences.
 */
fun Context.sleepTimer(): SleepTimer {
    val context = applicationContext
    val settings = SettingsStore.from(context)
    val trigger = AndroidSleepTrigger.create(context)
    return SleepTimer(
        display = TimerNotification(context, trigger, settings),
        trigger = trigger,
        clock = SystemDeviceClock,
        settings = settings::timerSettings,
        onChange = context::requestTileUpdate,
        log = context.diagnostics,
    )
}

class ZzzTimerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        recordCrashes()
        TimerNotification.createChannel(this)
    }

    /** Crashes are recorded in the diagnostics log (when enabled), then handled as usual. */
    private fun recordCrashes() {
        val log = diagnostics
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Never let diagnostics get in the way of the regular crash handling.
            runCatching { log.recordNow { "crash in ${thread.name}: ${error.stackTraceToString()}" } }
            default?.uncaughtException(thread, error)
        }
    }
}
