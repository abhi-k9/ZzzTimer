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

    /** Removes the displayed timer, if any, and what it holds. This must not trigger the sleep. */
    fun hide()
}

/**
 * Triggers the sleep (fade out, then pause playback) at the deadline of a timer.
 */
interface SleepTrigger {
    /** Whether the permissions needed to trigger the sleep on time are granted. */
    fun isPermitted(): Boolean

    /**
     * Schedules the sleep at the [Timer.deadline], and the warning that it ends soon at [warnAt] (when not `null`), on
     * the same timeline, replacing any previously armed timer.
     * @return `false` when the sleep could not be scheduled.
     */
    fun arm(timer: Timer, warnAt: Long?): Boolean

    /** Cancels the scheduled sleep and warning, if any. */
    fun disarm()
}

/** Something the user must allow before a timer can run, in the order they must be resolved. */
enum class Requirement { NOTIFICATIONS, EXACT_ALARMS }

/**
 * The media audio stream of the device.
 */
interface MediaAudio {
    /** Current volume index. */
    val volume: Int
    val minVolume: Int
    val isPlaying: Boolean

    /** Whether the volume can't be changed (e.g. fixed volume devices). */
    val isVolumeFixed: Boolean

    /** Lowers the volume by one step. */
    fun lowerVolume()
    fun restoreVolume(volume: Int)

    /** Asks the active media player to pause, see also [PlayingMedia.pause]. */
    fun pause()
}

/** Where a player is in what it plays. */
data class PlaybackPoint(
    /** What is playing (e.g. a track or an episode), `null` when the player doesn't say. */
    val item: String?,
    /** Position in [item], in media time: 10 seconds at 2× speed move it by 20 seconds. */
    val position: Duration,
)

/**
 * The media session playing when the timer ends, available when the user allows it (Notification access).
 */
interface PlayingMedia {
    /** Where the player is now, `null` when it can't tell or can't seek, e.g. a live stream. */
    fun current(): PlaybackPoint?

    fun seekTo(position: Duration)

    /**
     * Asks this player to pause. More reliable than [MediaAudio.pause], whose pause button reaches the player that
     * last played, which may not be the one heard.
     */
    fun pause()
}
