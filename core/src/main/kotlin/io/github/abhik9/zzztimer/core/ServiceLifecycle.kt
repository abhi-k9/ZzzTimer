package io.github.abhik9.zzztimer.core

/** How the sleep service runs in the foreground. */
enum class ForegroundMode {
    /**
     * For as long as needed: held for a whole timer, or watching its end. Started from a user interaction, it may lower
     * the volume, which Android 17 only allows such a foreground service.
     */
    LONG,

    /** For the sleep only, started by the alarm at the deadline: limited to a few minutes. */
    SHORT,
}

/**
 * The lifecycle of the Android sleep service: what keeps it running, and how it runs in the foreground. Kept apart from
 * the platform code to be unit tested. Main thread only.
 *
 * Since Android 17 the service is held in the foreground while a timer runs (see `SleepService.hold`). It also runs
 * while it watches the end of a timer, from its warning until its sleep is done (shake to keep listening), and while
 * sleeps (fade out, then pause) run.
 */
class ServiceLifecycle {
    /** Held in the foreground for a running timer. */
    var held = false
        private set

    var runningSleeps = 0
        private set

    /** Deadline of the timer whose end is watched, `null` when not watching. */
    var watched: Long? = null
        private set

    /** Whether nothing keeps the service running. */
    val canStop: Boolean get() = !held && runningSleeps == 0 && watched == null

    /** Whether the service is in the [ForegroundMode.LONG] foreground. */
    val inLongForeground: Boolean get() = held || watched != null

    /** A timer asked to hold the service: [inForeground] is whether it could enter the foreground. */
    fun onHold(inForeground: Boolean) {
        held = inForeground
    }

    /** The timer was stopped or dismissed: running sleeps keep the service running, but aren't watched anymore. */
    fun onRelease() {
        held = false
        watched = null
    }

    /** The timer of [deadline] ends soon: its end is watched until its sleep is done. */
    fun onWatch(deadline: Long) {
        watched = deadline
    }

    /** Watching the timer of [deadline] lasted long enough: the deadline was never signaled, or it slept without us. */
    fun onWatchExpired(deadline: Long) {
        if (watched == deadline && runningSleeps == 0) watched = null
    }

    /**
     * The user shook the phone.
     * @param current deadline of the running timer, `null` when there is none.
     */
    fun onShake(current: Long?): ShakeAction {
        val action = when {
            watched == null -> ShakeAction.NONE
            runningSleeps > 0 -> ShakeAction.STOP_SLEEP
            current == watched -> ShakeAction.EXTEND
            else -> ShakeAction.NONE
        }
        // Once is enough: a new timer has its own warning.
        watched = null
        return action
    }

    /**
     * The deadline of a timer was signaled. The hold ends with it: the service stops once its sleep is done.
     * @param requested whether the service was started as a foreground service, and must enter the foreground.
     * @return how to be in the foreground for the sleep, `null` when it isn't needed.
     */
    fun onDeadline(requested: Boolean): ForegroundMode? {
        val mode = when {
            inLongForeground -> ForegroundMode.LONG
            requested -> ForegroundMode.SHORT
            else -> null
        }
        held = false
        return mode
    }

    fun onSleepStarted() {
        runningSleeps++
    }

    /** No sleep follows the deadline (e.g. a dismissal): nothing to watch anymore. */
    fun onNoSleep() {
        if (runningSleeps == 0) watched = null
    }

    /** Watching ends with the sleeps: a shake can stop a fade, not a later timer. */
    fun onSleepFinished() {
        check(runningSleeps > 0) { "No sleep is running" }
        runningSleeps--
        if (runningSleeps == 0) watched = null
    }
}

/** What a shake does, see [ServiceLifecycle.onShake]. */
enum class ShakeAction {
    /** Extends the running timer. */
    EXTEND,

    /** Stops the running fade, keeps playing, and starts a new timer. */
    STOP_SLEEP,

    /** Nothing: the watched timer was stopped or changed. */
    NONE,
}
