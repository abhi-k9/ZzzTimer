package io.github.abhik9.zzztimer.core

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class EventLogTest {

    private class RecordingLog : EventLog {
        val messages = mutableListOf<String>()
        override fun record(message: () -> String) {
            messages += message()
        }
    }

    private val clock = FakeClock()
    private val display = FakeDisplay()
    private val log = RecordingLog()
    private val timer = SleepTimer(display, FakeTrigger(), clock, { TimerSettings.DEFAULT }, log = log)

    @Test
    fun `timer operations and decisions are recorded`() {
        val started = (timer.start(20.minutes) as StartResult.Started).timer
        timer.extend()
        clock.advance(5.minutes)
        display.hide() // dismissed by the user
        timer.onDeadline(started.deadline)
        timer.stop()

        assertTrue(log.messages.first().startsWith("start(20m): Started"), log.messages.first())
        assertTrue(log.messages.any { it.startsWith("adjust(10m") })
        assertTrue(log.messages.any { it.startsWith("deadline(${started.deadline})") && "early=true" in it })
        assertEquals("stop", log.messages.last())
    }

    @Test
    fun `blocked starts are recorded`() {
        display.available = false
        timer.start(5.minutes)
        assertEquals(listOf("start(5m): Blocked(requirement=NOTIFICATIONS)"), log.messages)
    }

    @Test
    fun `the sleep routine is recorded`() = runTest {
        SleepRoutine(FakeAudio(volume = 2), log = log).run()
        assertEquals(
            listOf(
                "sleep: volume=2 min=0 playing=true fixed=false fade=30s",
                "fade: 2 steps of 15s",
                "sleep: pause requested, playing=false",
                "restore: volume 2 requested, now 2",
            ),
            log.messages,
        )
    }

    @Test
    fun `messages are never built without a log`() {
        var built = false
        EventLog.NONE.record {
            built = true
            ""
        }
        assertTrue(!built)
    }
}
