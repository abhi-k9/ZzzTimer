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
 * @property warningLead how long before its end the user is warned that a timer ends soon (vibration, shake to extend),
 * `null` when not warned.
 */
data class TimerSettings(
    val initial: Duration,
    val increment: Duration,
    val decrement: Duration,
    val warningLead: Duration? = null,
) {
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

/** How long before its end the user is warned that a timer ends soon, in whole minutes, see [TimerSettings.warningLead]. */
object WarningSetting {
    const val DEFAULT_MINUTES = 1
    val range = 1..10

    /** Brings a stored or user provided value back into [range]. */
    fun sanitize(minutes: Int): Int = minutes.coerceIn(range)
}

/** The vibration warning that a timer ends soon: small, and stronger only on request. */
enum class WarningVibration(
    /** Vibration amplitude, from 1 to 255, on devices that can control it. */
    val amplitude: Int,
    /** Length of the vibration: devices without amplitude control only vibrate longer. */
    val millis: Long,
) {
    OFF(amplitude = 0, millis = 0),
    LIGHT(amplitude = 60, millis = 120),
    MEDIUM(amplitude = 140, millis = 220),
    STRONG(amplitude = 255, millis = 400),
    ;

    companion object {
        val DEFAULT = LIGHT
    }
}
