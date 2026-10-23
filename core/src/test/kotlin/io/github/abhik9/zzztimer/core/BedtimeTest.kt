package io.github.abhik9.zzztimer.core

import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedtimeTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val bedtime = Bedtime(startMinute = 23 * 60)

    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)

    @Test
    fun `the window opens at bedtime, for 3 hours, across midnight`() {
        assertFalse(bedtime.isWithin(at(10, 22, 59)))
        assertTrue(bedtime.isWithin(at(10, 23)))
        assertTrue(bedtime.isWithin(at(11, 1, 59)))
        assertFalse(bedtime.isWithin(at(11, 2)))
        assertFalse(bedtime.isWithin(at(11, 12)))
    }

    @Test
    fun `checks every 5 minutes during the window, then at the next bedtime`() {
        assertEquals(at(10, 23), bedtime.nextCheck(at(10, 12)))
        assertEquals(at(10, 23, 5), bedtime.nextCheck(at(10, 23)))
        assertEquals(at(11, 0, 2), bedtime.nextCheck(at(10, 23, 57)))
        assertEquals(at(11, 23), bedtime.nextCheck(at(11, 1, 56)))
        assertEquals(at(11, 23), bedtime.nextCheck(at(11, 2)))
    }

    @Test
    fun `starts a timer only when something plays without one, during the window`() {
        val check = bedtime.check(at(10, 23, 30), timerRunning = false, playing = true)
        assertEquals(BedtimeCheck(startTimer = true, next = at(10, 23, 35)), check)
        assertFalse(bedtime.check(at(10, 23, 30), timerRunning = true, playing = true).startTimer)
        assertFalse(bedtime.check(at(10, 23, 30), timerRunning = false, playing = false).startTimer)
        assertFalse(bedtime.check(at(10, 21), timerRunning = false, playing = true).startTimer)
    }

    @Test
    fun `local time is followed across daylight saving time changes`() {
        // Clocks go back at 3:00 on 2026-10-25 in Paris: bedtime is still 23:00 local time.
        assertEquals(at(25, 23), bedtime.nextStart(at(25, 12)))
        assertEquals(at(26, 23), bedtime.nextStart(at(25, 23, 30)))
    }

    @Test
    fun `a window after midnight`() {
        val early = Bedtime(startMinute = 30)
        assertTrue(early.isWithin(at(11, 0, 30)))
        assertTrue(early.isWithin(at(11, 3, 29)))
        assertFalse(early.isWithin(at(10, 23, 59)))
        assertEquals(at(11, 0, 30), early.nextCheck(at(10, 23, 59)))
    }
}
