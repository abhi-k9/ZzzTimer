package io.github.abhik9.zzztimer.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class ControlTest {

    private val clock = FakeClock()
    private val display = FakeDisplay()
    private val trigger = FakeTrigger()
    private val timer = SleepTimer(display, trigger, clock, { TimerSettings.DEFAULT })

    @Test
    fun `state follows the timer`() {
        assertEquals(ControlState.Idle, ControlState.of(null))
        assertEquals(ControlState.Running(endsAt = 42L), ControlState.of(Timer(deadline = 7L, endsAt = 42L)))
    }

    @Test
    fun `a tap without timer starts the default one`() {
        val outcome = timer.toggleControl()
        assertEquals(ControlOutcome.Show(ControlState.Running(endsAt = clock.wall + 30.minutes.inWholeMilliseconds)), outcome)
        assertEquals(30.minutes, display.timeout)
    }

    @Test
    fun `a tap stops the running timer, without querying the display`() {
        timer.start()
        assertEquals(ControlOutcome.Show(ControlState.Idle), timer.toggleControl())
        assertNull(display.shown)
        assertNull(trigger.armed)
    }

    @Test
    fun `a blocked start asks to resolve the requirement`() {
        display.available = false
        assertEquals(ControlOutcome.Resolve(Requirement.NOTIFICATIONS), timer.toggleControl())
        display.available = true
        trigger.armable = false
        assertEquals(ControlOutcome.Resolve(Requirement.EXACT_ALARMS), timer.toggleControl())
        assertNull(display.shown)
    }
}
