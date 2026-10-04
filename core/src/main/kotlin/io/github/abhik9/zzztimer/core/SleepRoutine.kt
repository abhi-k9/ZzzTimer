package io.github.abhik9.zzztimer.core

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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

/** Where a player is in what it plays. */
data class PlaybackPoint(
    /** What is playing (e.g. a track or an episode), `null` when the player doesn't say. */
    val item: String?,
    /** Position in [item], in media time: 10 seconds at 2× speed move it by 20 seconds. */
    val position: Duration,
)

/**
 * The media session playing when the timer ends, available when the user allows it (Notification access).
 */
interface PlayingMedia {
    /** Where the player is now, `null` when it can't tell or can't seek, e.g. a live stream. */
    fun current(): PlaybackPoint?

    fun seekTo(position: Duration)
}

/**
 * What happens when a timer ends: the volume is gradually lowered, playback is paused, then the volume is restored.
 * When the [media] session is available, playback is also rewound to where the fade started, see [rewindTarget].
 */
class SleepRoutine(
    private val audio: MediaAudio,
    private val media: () -> PlayingMedia? = { null },
    private val maxFade: Duration = 30.seconds,
    private val maxFadeStep: Duration = 1.seconds,
    private val restoreDelay: Duration = 2.seconds,
    private val maxRewind: Duration = 1.minutes,
    private val log: EventLog = EventLog.NONE,
) {
    private companion object {
        /** Rewinds a bit before the fade started: the listener may have missed its beginning already. */
        const val REWIND_MARGIN = 0.05
    }

    /**
     * Playback is paused and the volume restored even when cancelled (e.g. when the system stops the service). The volume
     * is restored even when a step fails.
     */
    suspend fun run() {
        // Read at the time of the fade: the user may have changed the volume while the timer was running.
        val volume = audio.volume
        val minVolume = audio.minVolume
        log.record { "sleep: volume=$volume min=$minVolume playing=${audio.isPlaying} fixed=${audio.isVolumeFixed}" }
        // The session playing when the fade started, and where it was. Without a fade, nothing is rewound.
        var fadeStart: Pair<PlayingMedia, PlaybackPoint>? = null
        try {
            // Pointless when nothing is playing locally (e.g. when casting), or when the volume can't go any lower.
            if (audio.isPlaying && !audio.isVolumeFixed && volume > minVolume) {
                media()?.let { session ->
                    val point = session.current()
                    log.record { "rewind: fade starts at $point" }
                    if (point != null) fadeStart = session to point
                }
                fadeOut(minVolume)
            }
        } finally {
            withContext(NonCancellable) {
                try {
                    audio.pause()
                    log.record { "sleep: pause requested, playing=${audio.isPlaying}" }
                    // Lets the player pause before reading where it stopped, and before the volume goes back up.
                    if (fadeStart != null || audio.volume != volume) delay(restoreDelay)
                    fadeStart?.let { (session, start) -> rewind(session, start) }
                } finally {
                    // Whatever failed before: the volume must never stay low.
                    restore(volume)
                }
            }
        }
    }

    /**
     * Where playback resumes: where the fade started, minus [REWIND_MARGIN] of the fade, and at most [maxRewind] before
     * [end]. Positions are in media time, so the playback speed is accounted for.
     * @return `null` when there is nothing to rewind, e.g. when another item started playing during the fade.
     */
    fun rewindTarget(start: PlaybackPoint, end: PlaybackPoint): Duration? {
        if (start.item != end.item) return null
        val faded = end.position - start.position
        // Moved back during the fade, e.g. by the user.
        if (faded <= Duration.ZERO) return null
        val rewind = minOf(faded * (1 + REWIND_MARGIN), maxRewind)
        return (end.position - rewind).coerceAtLeast(Duration.ZERO)
    }

    private fun rewind(session: PlayingMedia, start: PlaybackPoint) {
        // The player ignored the pause: rewinding would only replay what is being heard.
        if (audio.isPlaying) {
            log.record { "rewind: skipped, still playing" }
            return
        }
        val end = session.current()
        val target = end?.let { rewindTarget(start, it) }
        log.record { "rewind: from $end to $target" }
        if (target != null) session.seekTo(target)
    }

    /**
     * Delay between two volume steps: one step per [maxFadeStep], but the whole fade never exceeds [maxFade].
     * Devices exposing a lot of volume steps would otherwise take minutes to fade out.
     */
    fun fadeStepDelay(steps: Int): Duration = if (steps <= 0) Duration.ZERO else minOf(maxFadeStep, maxFade / steps)

    private suspend fun fadeOut(min: Int) {
        val steps = audio.volume - min
        val stepDelay = fadeStepDelay(steps)
        log.record { "fade: $steps steps of $stepDelay" }
        // Bounded: the volume may never reach `min` (user interaction, OEM policies…).
        repeat(steps) {
            audio.lowerVolume()
            delay(stepDelay)
            if (audio.volume <= min) return
        }
        log.record { "fade: stopped at volume ${audio.volume}, above the minimum $min" }
    }

    private fun restore(volume: Int) {
        if (audio.volume == volume) return
        // The player ignored the pause request: keep it quiet rather than blasting audio at full volume.
        if (audio.isPlaying) {
            log.record { "restore: skipped, still playing at volume ${audio.volume}" }
            return
        }
        audio.restoreVolume(volume)
        log.record { "restore: volume $volume requested, now ${audio.volume}" }
    }
}
