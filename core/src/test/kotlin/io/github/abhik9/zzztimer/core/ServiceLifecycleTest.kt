package io.github.abhik9.zzztimer.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceLifecycleTest {

    private val lifecycle = ServiceLifecycle()

    @Test
    fun `stops when nothing keeps it running`() {
        assertTrue(lifecycle.canStop)
        lifecycle.onHold(inForeground = false)
        assertTrue(lifecycle.canStop)
    }

    @Test
    fun `a held service runs until released`() {
        lifecycle.onHold(inForeground = true)
        assertFalse(lifecycle.canStop)
        lifecycle.onRelease()
        assertTrue(lifecycle.canStop)
    }

    @Test
    fun `a held service fades out in the long foreground, then stops`() {
        lifecycle.onHold(inForeground = true)
        assertEquals(ForegroundMode.LONG, lifecycle.onDeadline(requested = true))
        assertFalse(lifecycle.held)
        lifecycle.onSleepStarted()
        assertFalse(lifecycle.canStop)
        lifecycle.onSleepFinished()
        assertTrue(lifecycle.canStop)
    }

    @Test
    fun `an alarm alone gets a short foreground, a notification timeout none`() {
        assertEquals(ForegroundMode.SHORT, lifecycle.onDeadline(requested = true))
        assertNull(lifecycle.onDeadline(requested = false))
    }

    @Test
    fun `a release during a sleep lets it finish`() {
        lifecycle.onHold(inForeground = true)
        lifecycle.onDeadline(requested = true)
        lifecycle.onSleepStarted()
        lifecycle.onRelease()
        assertFalse(lifecycle.canStop)
        lifecycle.onSleepFinished()
        assertTrue(lifecycle.canStop)
    }

    @Test
    fun `a new timer during a sleep keeps the service once the sleep is done`() {
        lifecycle.onDeadline(requested = true)
        lifecycle.onSleepStarted()
        lifecycle.onHold(inForeground = true)
        lifecycle.onSleepFinished()
        assertFalse(lifecycle.canStop)
    }

    @Test
    fun `watching keeps the service in the long foreground until the sleep is done`() {
        lifecycle.onWatch(deadline = 42)
        assertFalse(lifecycle.canStop)
        assertEquals(ForegroundMode.LONG, lifecycle.onDeadline(requested = false))
        lifecycle.onSleepStarted()
        assertEquals(42L, lifecycle.watched)
        lifecycle.onSleepFinished()
        assertNull(lifecycle.watched)
        assertTrue(lifecycle.canStop)
    }

    @Test
    fun `a shake before the deadline extends the watched timer only`() {
        lifecycle.onWatch(deadline = 42)
        assertEquals(ShakeAction.EXTEND, lifecycle.onShake(current = 42))
        assertTrue(lifecycle.canStop)
        lifecycle.onWatch(deadline = 42)
        assertEquals(ShakeAction.NONE, lifecycle.onShake(current = 99))
        assertEquals(ShakeAction.NONE, lifecycle.onShake(current = null))
    }

    @Test
    fun `a shake during the fade stops it`() {
        lifecycle.onWatch(deadline = 42)
        lifecycle.onDeadline(requested = true)
        lifecycle.onSleepStarted()
        assertEquals(ShakeAction.STOP_SLEEP, lifecycle.onShake(current = null))
        // Once only.
        assertEquals(ShakeAction.NONE, lifecycle.onShake(current = null))
    }

    @Test
    fun `watching ends with a release, a dismissal, or when it expires`() {
        lifecycle.onWatch(deadline = 42)
        lifecycle.onRelease()
        assertTrue(lifecycle.canStop)
        lifecycle.onWatch(deadline = 42)
        lifecycle.onNoSleep()
        assertTrue(lifecycle.canStop)
        lifecycle.onWatch(deadline = 42)
        lifecycle.onWatchExpired(deadline = 7)
        assertFalse(lifecycle.canStop)
        lifecycle.onWatchExpired(deadline = 42)
        assertTrue(lifecycle.canStop)
    }

    @Test
    fun `an expired watch lets a running sleep be stopped`() {
        lifecycle.onWatch(deadline = 42)
        lifecycle.onSleepStarted()
        lifecycle.onWatchExpired(deadline = 42)
        assertEquals(ShakeAction.STOP_SLEEP, lifecycle.onShake(current = null))
    }

    @Test
    fun `sleeps are counted`() {
        lifecycle.onSleepStarted()
        lifecycle.onSleepStarted()
        lifecycle.onSleepFinished()
        assertEquals(1, lifecycle.runningSleeps)
        lifecycle.onSleepFinished()
        assertFailsWith<IllegalStateException> { lifecycle.onSleepFinished() }
    }
}
