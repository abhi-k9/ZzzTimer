package io.github.abhik9.zzztimer.core

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SleepTimerTest {

    private val clock = FakeClock()
    private val display = FakeDisplay()
    private val trigger = FakeTrigger()
    private var settings = TimerSettings(initial = 30.minutes, increment = 10.minutes, decrement = 5.minutes)
    private var changes = 0
    private val timer = SleepTimer(display, trigger, clock, { settings }, onChange = { changes++ })

    private fun StartResult?.started(): Timer = assertIs<StartResult.Started>(this).timer

    @Nested
    inner class StartAndStop {
        @Test
        fun `start displays and arms the timer`() {
            val started = timer.start(20.minutes).started()

            assertEquals(
                Timer(deadline = clock.elapsed + 20.minutes.inWholeMilliseconds, endsAt = clock.wall + 20.minutes.inWholeMilliseconds),
                started,
            )
            assertEquals(started, display.shown)
            assertEquals(20.minutes, display.timeout)
            assertEquals(started, trigger.armed)
            assertEquals(1, changes)
        }

        @Test
        fun `start uses the default duration`() {
            timer.start()
            assertEquals(30.minutes, display.timeout)
        }

        @Test
        fun `start is capped`() {
            timer.start(Duration.INFINITE)
            assertEquals(MAX_TIMER_DURATION, display.timeout)
        }

        @Test
        fun `non positive duration stops the timer`() {
            timer.start()
            assertEquals(StartResult.Stopped, timer.start(Duration.ZERO))
            assertNull(display.shown)
            assertEquals(1, trigger.disarms)
        }

        @Test
        fun `duration under a millisecond stops the timer`() {
            timer.start()
            assertEquals(StartResult.Stopped, timer.start(500.microseconds))
            assertNull(display.shown)
            assertEquals(1, trigger.disarms)
        }

        @Test
        fun `stop hides and disarms`() {
            timer.start()
            timer.stop()

            assertNull(display.shown)
            assertNull(trigger.armed)
            assertNull(timer.current())
            assertEquals(2, changes)
        }

        @Test
        fun `toggle starts then stops`() {
            timer.toggle().started()
            assertEquals(StartResult.Stopped, timer.toggle())
            assertNull(display.shown)
        }
    }

    @Nested
    inner class Requirements {
        @Test
        fun `nothing is missing`() = assertNull(timer.missingRequirement())

        @Test
        fun `notifications come first`() {
            display.available = false
            trigger.permitted = false
            assertEquals(Requirement.NOTIFICATIONS, timer.missingRequirement())
        }

        @Test
        fun `exact alarms are required by the trigger`() {
            trigger.permitted = false
            assertEquals(Requirement.EXACT_ALARMS, timer.missingRequirement())
        }

        @Test
        fun `start is blocked by a missing requirement`() {
            display.available = false
            assertEquals(StartResult.Blocked(Requirement.NOTIFICATIONS), timer.start())
            assertNull(display.shown)
            assertNull(trigger.armed)
            assertEquals(0, changes)
        }

        @Test
        fun `timer is never displayed when the trigger can't be armed`() {
            trigger.armable = false
            assertEquals(StartResult.Blocked(Requirement.EXACT_ALARMS), timer.start())
            assertNull(display.shown)
        }

        @Test
        fun `stop is never blocked`() {
            timer.start()
            display.available = false
            timer.stop()
            assertNull(display.shown)
        }
    }

    @Nested
    inner class Adjustments {
        @Test
        fun `remaining time follows the monotonic clock`() {
            timer.start(20.minutes)
            clock.advance(5.minutes)
            clock.wall += 3_600_000 // wall clock changes don't matter
            assertEquals(15.minutes, timer.remaining())
        }

        @Test
        fun `extend adds the increment to the remaining time`() {
            timer.start(20.minutes)
            clock.advance(5.minutes)
            timer.extend().started()
            assertEquals(25.minutes, display.timeout)
        }

        @Test
        fun `extend is capped`() {
            timer.start(MAX_TIMER_DURATION)
            timer.extend()
            assertEquals(MAX_TIMER_DURATION, display.timeout)
        }

        @Test
        fun `reduce subtracts the decrement from the remaining time`() {
            timer.start(20.minutes)
            timer.reduce()
            assertEquals(15.minutes, display.timeout)
        }

        @Test
        fun `reduce never ends the timer`() {
            timer.start(3.minutes)
            timer.reduce().started()
            assertEquals(3.minutes, display.timeout)
            assertEquals(0, trigger.disarms)
        }

        @Test
        fun `adjust can end the timer`() {
            timer.start(3.minutes)
            assertEquals(StartResult.Stopped, timer.adjust(-5.minutes))
            assertNull(display.shown)
            assertEquals(1, trigger.disarms)
        }

        @Test
        fun `adjustments are ignored without timer`() {
            assertNull(timer.extend())
            assertNull(timer.reduce())
            assertNull(timer.refresh())
            assertNull(display.shown)
            assertEquals(0, changes)
        }

        @Test
        fun `adjustments are ignored once the deadline is reached`() {
            timer.start(1.minutes)
            clock.advance(1.minutes + 1.seconds)
            assertNull(timer.refresh())
            assertNull(timer.adjust(-5.minutes))
            assertEquals(0, trigger.disarms)
        }

        @Test
        fun `refresh re-displays the running timer`() {
            timer.start(20.minutes)
            clock.advance(1.minutes)
            timer.refresh().started()
            assertEquals(19.minutes, display.timeout)
        }

        @Test
        fun `adjustments read the latest settings`() {
            timer.start(20.minutes)
            settings = settings.copy(increment = 1.minutes)
            timer.extend()
            assertEquals(21.minutes, display.timeout)
        }
    }

    @Nested
    inner class Deadline {
        @Test
        fun `reaching the deadline triggers the sleep`() {
            val started = timer.start(20.minutes).started()
            clock.advance(20.minutes)
            display.hide() // timed out
            assertEquals(DeadlineOutcome.SLEEP, timer.onDeadline(started.deadline))
            assertEquals(0, trigger.disarms)
            assertEquals(2, changes)
        }

        @Test
        fun `a trigger firing before the display timed out removes it`() {
            val started = timer.start(20.minutes).started()
            clock.advance(20.minutes)
            assertEquals(DeadlineOutcome.SLEEP, timer.onDeadline(started.deadline))
            assertNull(display.shown)
            assertEquals(0, trigger.disarms)
        }

        @Test
        fun `a late signal never removes a newer timer`() {
            val old = timer.start(1.minutes).started()
            clock.advance(2.minutes)
            val new = timer.start(30.minutes).started()
            assertEquals(DeadlineOutcome.SLEEP, timer.onDeadline(old.deadline))
            assertEquals(new, display.shown)
            assertEquals(new, trigger.armed)
        }

        @Test
        fun `a signal within the tolerance triggers the sleep`() {
            val started = timer.start(20.minutes).started()
            clock.advance(20.minutes - SleepTimer.DEADLINE_TOLERANCE)
            assertEquals(DeadlineOutcome.SLEEP, timer.onDeadline(started.deadline))
        }

        @Test
        fun `a signal without deadline triggers the sleep`() = assertEquals(DeadlineOutcome.SLEEP, timer.onDeadline(0L))

        @Test
        fun `dismissal before the deadline cancels the timer`() {
            val started = timer.start(20.minutes).started()
            clock.advance(5.minutes)
            display.hide() // dismissed by the user
            assertEquals(DeadlineOutcome.DISMISSED, timer.onDeadline(started.deadline))
            assertEquals(1, trigger.disarms)
            assertNull(trigger.armed)
        }

        @Test
        fun `dismissal does not cancel a timer started in the meantime`() {
            val old = timer.start(20.minutes).started()
            display.hide() // dismissed by the user…
            val new = timer.start(30.minutes).started() // …then a new timer is started before the dismissal is handled
            assertEquals(DeadlineOutcome.DISMISSED, timer.onDeadline(old.deadline))
            assertEquals(0, trigger.disarms)
            assertEquals(new, trigger.armed)
        }
    }

    @Nested
    inner class Commands {
        @Test
        fun `start with and without duration`() {
            timer.execute(TimerCommand.Start(10.minutes))
            assertEquals(10.minutes, display.timeout)
            timer.execute(TimerCommand.Start(null))
            assertEquals(30.minutes, display.timeout)
        }

        @Test
        fun `adjust, extend, reduce`() {
            timer.execute(TimerCommand.Start(20.minutes))
            timer.execute(TimerCommand.Adjust(-1.minutes))
            assertEquals(19.minutes, display.timeout)
            timer.execute(TimerCommand.Extend)
            assertEquals(29.minutes, display.timeout)
            timer.execute(TimerCommand.Reduce)
            assertEquals(24.minutes, display.timeout)
        }

        @Test
        fun `toggle and stop`() {
            timer.execute(TimerCommand.Toggle).started()
            assertEquals(StartResult.Stopped, timer.execute(TimerCommand.Stop))
            assertNull(display.shown)
        }
    }
}
