package io.github.abhik9.zzztimer.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SleepRoutineTest {

    @Test
    fun `fades out, pauses, then restores the volume`() = runTest {
        val audio = FakeAudio(volume = 3)
        SleepRoutine(audio).run()

        assertEquals(listOf("lower", "lower", "lower", "pause", "set:3"), audio.events)
        assertEquals(3, audio.volume)
        // One second per step, then the restore delay.
        assertEquals(3_000L + 2_000L, currentTime)
    }

    @Test
    fun `fade never exceeds its total duration`() {
        val routine = SleepRoutine(FakeAudio())
        assertEquals(Duration.ZERO, routine.fadeStepDelay(0))
        assertEquals(1.seconds, routine.fadeStepDelay(25))
        for (steps in 1..1000) assertTrue(routine.fadeStepDelay(steps) * steps <= 30.seconds)
    }

    @Test
    fun `fade stops at the minimum volume`() = runTest {
        val audio = FakeAudio(volume = 5, minVolume = 3)
        SleepRoutine(audio).run()
        assertEquals(listOf("lower", "lower", "pause", "set:5"), audio.events)
    }

    @Test
    fun `no fade when nothing plays locally`() = runTest {
        val audio = FakeAudio(isPlaying = false)
        SleepRoutine(audio).run()
        assertEquals(listOf("pause"), audio.events)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `no fade with a fixed volume`() = runTest {
        val audio = FakeAudio(isVolumeFixed = true)
        SleepRoutine(audio).run()
        assertEquals(listOf("pause"), audio.events)
    }

    @Test
    fun `volume stays low when the player ignores the pause`() = runTest {
        val audio = FakeAudio(volume = 2, pausable = false)
        SleepRoutine(audio).run()
        assertEquals(listOf("lower", "lower", "pause"), audio.events)
        assertEquals(0, audio.volume)
    }

    @Test
    fun `cancellation still pauses and restores`() = runTest {
        val audio = FakeAudio(volume = 10)
        val job = launch { SleepRoutine(audio).run() }
        advanceTimeBy(2.5.seconds)
        job.cancel()
        runCurrent()
        advanceTimeBy(3.seconds)

        assertEquals(listOf("lower", "lower", "lower", "pause", "set:10"), audio.events)
        assertTrue(job.isCancelled)
    }
}
