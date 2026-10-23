package io.github.abhik9.zzztimer.core

/** How the sleep service runs in the foreground. */
enum class ForegroundMode {
    /**
     * For as long as needed: held for a whole timer. Started from a user interaction, it may lower the volume, which
     * Android 17 only allows such a foreground service.
     */
    LONG,

    /** For the sleep only, started by the alarm at the deadline: limited to a few minutes. */
    SHORT,
}

/**
 * The lifecycle of the Android sleep service: what keeps it running, and how it runs in the foreground. Kept apart from
 * the platform code to be unit tested. Main thread only.
 *
 * Since Android 17 the service is held in the foreground while a timer runs (see `SleepService.hold`), and it runs
 * while sleeps (fade out, then pause) run.
 */
class ServiceLifecycle {
    /** Held in the foreground for a running timer. */
    var held = false
        private set

    var runningSleeps = 0
        private set

    /** Whether nothing keeps the service running. */
    val canStop: Boolean get() = !held && runningSleeps == 0

    /** A timer asked to hold the service: [inForeground] is whether it could enter the foreground. */
    fun onHold(inForeground: Boolean) {
        held = inForeground
    }

    /** The timer was stopped or dismissed: running sleeps keep the service running. */
    fun onRelease() {
        held = false
    }

    /**
     * The deadline of a timer was signaled. The hold ends with it: the service stops once its sleep is done.
     * @param requested whether the service was started as a foreground service, and must enter the foreground.
     * @return how to be in the foreground for the sleep, `null` when it isn't needed.
     */
    fun onDeadline(requested: Boolean): ForegroundMode? {
        val mode = when {
            held -> ForegroundMode.LONG
            requested -> ForegroundMode.SHORT
            else -> null
        }
        held = false
        return mode
    }

    fun onSleepStarted() {
        runningSleeps++
    }

    fun onSleepFinished() {
        check(runningSleeps > 0) { "No sleep is running" }
        runningSleeps--
    }
}
