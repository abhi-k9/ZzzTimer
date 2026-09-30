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
import kotlin.time.Duration.Companion.milliseconds
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
    fun `rewinds to where the fade started, plus a margin`() = runTest {
        val audio = FakeAudio(volume = 3)
        val media = FakeMedia(audio, clock = { currentTime })
        SleepRoutine(audio, media = { media }).run()

        assertEquals(listOf("lower", "lower", "lower", "pause", "seek", "set:3"), audio.events)
        // Faded from 100 s to 103 s: back to 100 s, minus 5% of those 3 s.
        assertEquals(listOf(99_850.milliseconds), media.seeks)
    }

    @Test
    fun `rewind accounts for the playback speed`() = runTest {
        val audio = FakeAudio(volume = 3)
        val media = FakeMedia(audio, clock = { currentTime }, speed = 2.0)
        SleepRoutine(audio, media = { media }).run()
        // 3 s of fade at 2× moved from 100 s to 106 s.
        assertEquals(listOf(99_700.milliseconds), media.seeks)
    }

    @Test
    fun `rewind is at most a minute`() = runTest {
        val audio = FakeAudio(volume = 30)
        val media = FakeMedia(audio, clock = { currentTime }, speed = 3.0)
        SleepRoutine(audio, media = { media }).run()
        // 30 s of fade at 3× moved from 100 s to 190 s.
        assertEquals(listOf(130.seconds), media.seeks)
    }

    @Test
    fun `rewind stops at the beginning`() = runTest {
        val audio = FakeAudio(volume = 3)
        val media = FakeMedia(audio, clock = { currentTime }, start = 100.milliseconds)
        SleepRoutine(audio, media = { media }).run()
        assertEquals(listOf(Duration.ZERO), media.seeks)
    }

    @Test
    fun `no rewind without a fade`() = runTest {
        val audio = FakeAudio(isVolumeFixed = true)
        val media = FakeMedia(audio, clock = { currentTime })
        SleepRoutine(audio, media = { media }).run()
        assertEquals(listOf("pause"), audio.events)
        assertTrue(media.seeks.isEmpty())
    }

    @Test
    fun `no rewind when the player can't tell its position`() = runTest {
        val audio = FakeAudio(volume = 3)
        val media = FakeMedia(audio, clock = { currentTime }, seekable = false)
        SleepRoutine(audio, media = { media }).run()
        assertEquals(listOf("lower", "lower", "lower", "pause", "set:3"), audio.events)
        assertTrue(media.seeks.isEmpty())
    }

    @Test
    fun `no rewind when another item played, or playback moved back`() {
        val routine = SleepRoutine(FakeAudio())
        assertEquals(null, routine.rewindTarget(PlaybackPoint("a", 100.seconds), PlaybackPoint("b", 103.seconds)))
        assertEquals(null, routine.rewindTarget(PlaybackPoint("a", 100.seconds), PlaybackPoint("a", 90.seconds)))
        assertEquals(99.seconds, routine.rewindTarget(PlaybackPoint(null, 100.seconds), PlaybackPoint(null, 120.seconds)))
    }

    @Test
    fun `no rewind when the player ignores the pause`() = runTest {
        val audio = FakeAudio(volume = 2, pausable = false)
        val media = FakeMedia(audio, clock = { currentTime })
        SleepRoutine(audio, media = { media }).run()
        assertEquals(listOf("lower", "lower", "pause"), audio.events)
        assertTrue(media.seeks.isEmpty())
    }

    @Test
    fun `cancellation still pauses, rewinds and restores`() = runTest {
        val audio = FakeAudio(volume = 10)
        val media = FakeMedia(audio, clock = { currentTime })
        val job = launch { SleepRoutine(audio, media = { media }).run() }
        advanceTimeBy(2.5.seconds)
        job.cancel()
        runCurrent()
        advanceTimeBy(3.seconds)

        assertEquals(listOf("lower", "lower", "lower", "pause", "seek", "set:10"), audio.events)
        // Faded from 100 s to 102.5 s.
        assertEquals(listOf(99_875.milliseconds), media.seeks)
        assertTrue(job.isCancelled)
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
