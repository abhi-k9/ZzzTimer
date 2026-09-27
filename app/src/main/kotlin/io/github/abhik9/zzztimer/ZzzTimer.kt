package io.github.abhik9.zzztimer

import android.content.Context
import android.os.SystemClock
import io.github.abhik9.zzztimer.core.DeviceClock
import io.github.abhik9.zzztimer.core.SleepTimer
import io.github.abhik9.zzztimer.settings.SettingsStore
import io.github.abhik9.zzztimer.tile.requestTileUpdate
import io.github.abhik9.zzztimer.timer.AndroidSleepTrigger
import io.github.abhik9.zzztimer.timer.TimerNotification

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
    )
}
