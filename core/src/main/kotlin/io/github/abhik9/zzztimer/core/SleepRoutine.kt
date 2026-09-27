package io.github.abhik9.zzztimer.core

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The media audio stream of the device.
 */
interface MediaAudio {
    /** Current volume index. */
    val volume: Int
    val minVolume: Int
    val isPlaying: Boolean

    /** Whether the volume can't be changed (e.g. fixed volume devices). */
    val isVolumeFixed: Boolean

    /** Lowers the volume by one step. */
    fun lowerVolume()
    fun restoreVolume(volume: Int)

    /** Asks the active media player to pause. */
    fun pause()
}

/**
 * What happens when a timer ends: the volume is gradually lowered, playback is paused, then the volume is restored.
 */
class SleepRoutine(
    private val audio: MediaAudio,
    private val maxFade: Duration = 30.seconds,
    private val maxFadeStep: Duration = 1.seconds,
    private val restoreDelay: Duration = 2.seconds,
) {

    /**
     * Playback is paused and the volume restored even when cancelled (e.g. when the system stops the service).
     */
    suspend fun run() {
        // Read at the time of the fade: the user may have changed the volume while the timer was running.
        val volume = audio.volume
        try {
            // Pointless when nothing is playing locally, e.g. when casting.
            if (audio.isPlaying && !audio.isVolumeFixed) fadeOut()
        } finally {
            withContext(NonCancellable) {
                audio.pause()
                restore(volume)
            }
        }
    }

    /**
     * Delay between two volume steps: one step per [maxFadeStep], but the whole fade never exceeds [maxFade].
     * Devices exposing a lot of volume steps would otherwise take minutes to fade out.
     */
    fun fadeStepDelay(steps: Int): Duration = if (steps <= 0) Duration.ZERO else minOf(maxFadeStep, maxFade / steps)

    private suspend fun fadeOut() {
        val min = audio.minVolume
        val steps = audio.volume - min
        val stepDelay = fadeStepDelay(steps)
        // Bounded: the volume may never reach `min` (user interaction, OEM policies…).
        repeat(steps) {
            audio.lowerVolume()
            delay(stepDelay)
            if (audio.volume <= min) return
        }
    }

    private suspend fun restore(volume: Int) {
        if (audio.volume == volume) return
        // Lets the player pause before the volume goes back up.
        delay(restoreDelay)
        // The player ignored the pause request: keep it quiet rather than blasting audio at full volume.
        if (audio.isPlaying) return
        audio.restoreVolume(volume)
    }
}
