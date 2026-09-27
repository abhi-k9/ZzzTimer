package io.github.abhik9.zzztimer.core

import kotlin.time.Duration

/**
 * Shows the running timer to the user (the ongoing notification on Android).
 *
 * It is also the single source of truth of the timer: a timer only exists while it is displayed, and the display
 * removes itself when the timer ends. Nothing else has to be persisted, or cleaned up after a reboot.
 */
interface TimerDisplay {
    /** Whether a timer can be displayed at all (e.g. notifications are enabled). */
    fun isAvailable(): Boolean

    /** @return the displayed timer, or `null` when there is none. */
    fun current(): Timer?

    /** Displays [timer], replacing the current one. The display must remove itself after [timeout]. */
    fun show(timer: Timer, timeout: Duration)

    /** Removes the displayed timer. This must not trigger the sleep. */
    fun hide()
}

/**
 * Triggers the sleep (fade out, then pause playback) at the deadline of a timer.
 */
interface SleepTrigger {
    /** Whether the permissions needed to trigger the sleep on time are granted. */
    fun isPermitted(): Boolean

    /**
     * Schedules the sleep at the [Timer.deadline], replacing any previously armed timer.
     * @return `false` when it could not be scheduled.
     */
    fun arm(timer: Timer): Boolean

    /** Cancels the scheduled sleep, if any. */
    fun disarm()
}

/** Something the user must allow before a timer can run, in the order they must be resolved. */
enum class Requirement { NOTIFICATIONS, EXACT_ALARMS }
