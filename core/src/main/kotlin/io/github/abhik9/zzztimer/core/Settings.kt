package io.github.abhik9.zzztimer.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * User configurable durations, in whole minutes.
 */
enum class DurationSetting(val defaultMinutes: Int, val range: IntRange) {
    /** Duration of a timer started without an explicit duration, e.g. from the Quick Settings tile. */
    INITIAL(defaultMinutes = 30, range = 1..12 * 60),

    /** Step of the "extend" action. */
    INCREMENT(defaultMinutes = 10, range = 1..120),

    /** Step of the "reduce" action. */
    DECREMENT(defaultMinutes = 10, range = 1..120),
    ;

    /** Brings a stored or user provided value back into [range]. */
    fun sanitize(minutes: Int): Int = minutes.coerceIn(range)
}

/**
 * Snapshot of the durations used by [SleepTimer].
 */
data class TimerSettings(val initial: Duration, val increment: Duration, val decrement: Duration) {
    companion object {
        fun ofMinutes(minutes: (DurationSetting) -> Int) = TimerSettings(
            initial = DurationSetting.INITIAL.sanitize(minutes(DurationSetting.INITIAL)).minutes,
            increment = DurationSetting.INCREMENT.sanitize(minutes(DurationSetting.INCREMENT)).minutes,
            decrement = DurationSetting.DECREMENT.sanitize(minutes(DurationSetting.DECREMENT)).minutes,
        )

        val DEFAULT = ofMinutes { it.defaultMinutes }
    }
}

/**
 * How long the volume fades out when a timer ends, in whole seconds, see [SleepRoutine]. `0` pauses playback right away.
 * At most a minute: before Android 17 the sleep runs in a background service, which the system stops soon after.
 */
object FadeSetting {
    const val DEFAULT_SECONDS = 30
    const val STEP_SECONDS = 5
    val range = 0..60

    /** Brings a stored or user provided value back into [range]. */
    fun sanitize(seconds: Int): Int = seconds.coerceIn(range)
}
