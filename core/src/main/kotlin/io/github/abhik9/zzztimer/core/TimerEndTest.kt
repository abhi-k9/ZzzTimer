package io.github.abhik9.zzztimer.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A test of the timer end: a short timer that runs the whole sleep (in the background, as at night), then reports what
 * it could do.
 */
object TimerEndTest {
    /** Long enough to lock the screen. */
    val DURATION: Duration = 10.seconds
}

/** What a test of the timer end found, in the order to report it. */
sealed interface TestFinding {
    /** Nothing played on this device: nothing to fade out or pause. */
    data object NothingPlaying : TestFinding

    data class Faded(val from: Int, val to: Int) : TestFinding

    /** Volume changes were ignored, e.g. by the Android 17 background audio restrictions. */
    data object VolumeRefused : TestFinding

    /** No fade: turned off, fixed volume, or already at the minimum. */
    data object NoFade : TestFinding

    data object Paused : TestFinding

    data object NotPaused : TestFinding

    data class Rewound(val position: Duration) : TestFinding
}

fun SleepReport.findings(): List<TestFinding> {
    if (!wasPlaying) return listOf(TestFinding.NothingPlaying)
    return buildList {
        add(
            when {
                volumeRefused -> TestFinding.VolumeRefused
                fadedTo != null -> TestFinding.Faded(from = volumeBefore, to = fadedTo)
                else -> TestFinding.NoFade
            },
        )
        add(if (paused) TestFinding.Paused else TestFinding.NotPaused)
        rewoundTo?.let { add(TestFinding.Rewound(it)) }
    }
}
