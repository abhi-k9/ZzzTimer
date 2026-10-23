package io.github.abhik9.zzztimer.core

import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/** Starting a timer automatically at bedtime, see [Bedtime]. */
object BedtimeSetting {
    /** 23:00, in minutes of the day. */
    const val DEFAULT_MINUTE = 23 * 60
    val range = 0 until 24 * 60

    /** Brings a stored or user provided value back into [range]. */
    fun sanitize(minute: Int): Int = minute.coerceIn(range)
}

/** What a [Bedtime] check does. */
data class BedtimeCheck(
    /** Whether to start a timer of the default duration now. */
    val startTimer: Boolean,
    /** When to check next. */
    val next: ZonedDateTime,
)

/**
 * Bedtime: from [startMinute] (of the day, local time) and for [window], media playing without a timer gets one. Checked
 * every [interval] during the window.
 */
class Bedtime(
    private val startMinute: Int,
    private val window: Duration = 3.hours,
    private val interval: Duration = 5.minutes,
) {
    /** The window open at [now], `null` when none is: today's, or yesterday's still open after midnight. */
    private fun openWindow(now: ZonedDateTime): ClosedRange<ZonedDateTime>? {
        val today = start(now)
        return listOf(today.minusDays(1), today)
            .map { it..it.plus(window.toJavaDuration()) }
            .firstOrNull { !now.isBefore(it.start) && now.isBefore(it.endInclusive) }
    }

    private fun start(now: ZonedDateTime): ZonedDateTime {
        val minute = BedtimeSetting.sanitize(startMinute)
        return now.toLocalDate().atTime(minute / 60, minute % 60).atZone(now.zone)
    }

    fun isWithin(now: ZonedDateTime): Boolean = openWindow(now) != null

    /** The next start of a window, after [now]. */
    fun nextStart(now: ZonedDateTime): ZonedDateTime = start(now).let { if (now.isBefore(it)) it else start(now.plusDays(1)) }

    /** During a window, in an [interval] while it lasts; otherwise at the next start. */
    fun nextCheck(now: ZonedDateTime): ZonedDateTime {
        val open = openWindow(now) ?: return nextStart(now)
        val next = now.plus(interval.toJavaDuration())
        return if (next.isBefore(open.endInclusive)) next else nextStart(now)
    }

    fun check(now: ZonedDateTime, timerRunning: Boolean, playing: Boolean) = BedtimeCheck(
        startTimer = isWithin(now) && playing && !timerRunning,
        next = nextCheck(now),
    )
}
