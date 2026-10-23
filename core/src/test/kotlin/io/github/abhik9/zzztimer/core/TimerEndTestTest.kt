package io.github.abhik9.zzztimer.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class TimerEndTestTest {

    private val report = SleepReport(
        wasPlaying = true,
        volumeBefore = 8,
        fadedTo = 0,
        volumeRefused = false,
        paused = true,
        rewoundTo = null,
    )

    @Test
    fun `a working timer end`() {
        assertEquals(listOf(TestFinding.Faded(from = 8, to = 0), TestFinding.Paused), report.findings())
        assertEquals(
            listOf(TestFinding.Faded(from = 8, to = 0), TestFinding.Paused, TestFinding.Rewound(42.seconds)),
            report.copy(rewoundTo = 42.seconds).findings(),
        )
    }

    @Test
    fun `nothing playing says only that`() {
        assertEquals(listOf(TestFinding.NothingPlaying), report.copy(wasPlaying = false, fadedTo = null).findings())
    }

    @Test
    fun `refused volume changes and an ignored pause`() {
        assertEquals(
            listOf(TestFinding.VolumeRefused, TestFinding.NotPaused),
            report.copy(fadedTo = 8, volumeRefused = true, paused = false).findings(),
        )
    }

    @Test
    fun `no fade`() {
        assertEquals(listOf(TestFinding.NoFade, TestFinding.Paused), report.copy(fadedTo = null).findings())
    }
}
