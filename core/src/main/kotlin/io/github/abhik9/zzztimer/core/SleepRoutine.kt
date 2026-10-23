package io.github.abhik9.zzztimer.core

import kotlinx.coroutines.CancellationException
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

    /** Asks the active media player to pause, see also [PlayingMedia.pause]. */
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

    /**
     * Asks this player to pause. More reliable than [MediaAudio.pause], whose pause button reaches the player that
     * last played, which may not be the one heard.
     */
    fun pause()
}

/**
 * Cancels a [SleepRoutine] because the user is still awake (e.g. they shook the phone): playback goes on, at its
 * volume before the fade.
 */
class StillAwake : CancellationException("Still awake")

/** What a [SleepRoutine] did, e.g. to report a test. */
data class SleepReport(
    /** Whether something was playing on this device when the timer ended. */
    val wasPlaying: Boolean,
    val volumeBefore: Int,
    /** The lowest volume the fade reached, `null` without a fade. */
    val fadedTo: Int?,
    /** Whether volume changes were ignored, e.g. by the Android 17 background audio restrictions. */
    val volumeRefused: Boolean,
    /** Whether nothing played anymore once paused. */
    val paused: Boolean,
    /** Where playback was rewound, `null` when it wasn't. */
    val rewoundTo: Duration?,
)

/**
 * What happens when a timer ends: the volume is gradually lowered over the [fade], playback is paused, then the volume
 * is restored. When the [media] session is available, it is paused directly, and playback is also rewound to where the
 * fade started, see [rewindTarget].
 */
class SleepRoutine(
    private val audio: MediaAudio,
    private val media: () -> PlayingMedia? = { null },
    /** How long the volume takes to go down, whatever its number of steps. Without a fade, playback is paused right away. */
    private val fade: Duration = FadeSetting.DEFAULT_SECONDS.seconds,
    private val restoreDelay: Duration = 2.seconds,
    private val log: EventLog = EventLog.NONE,
) {
    private companion object {
        /** Rewinds a bit before the fade started: the listener may have missed its beginning already. */
        const val REWIND_MARGIN = 0.05

        /**
         * Rewinds at most the [fade] played at this speed, already fast for a podcast: a jump forward during the fade
         * (e.g. the user skipping ahead) is not replayed in full.
         */
        const val MAX_REWIND_SPEED = 3
    }

    /**
     * Playback is paused and the volume restored even when cancelled (e.g. when the system stops the service), except
     * when cancelled with [StillAwake]. The volume is restored even when a step fails.
     */
    suspend fun run(): SleepReport {
        // Read at the time of the fade: the user may have changed the volume while the timer was running.
        val volume = audio.volume
        val minVolume = audio.minVolume
        val wasPlaying = audio.isPlaying
        log.record { "sleep: volume=$volume min=$minVolume playing=$wasPlaying fixed=${audio.isVolumeFixed} fade=$fade" }
        // The session heard, when playing locally (not when casting).
        val session = if (wasPlaying) media() else null
        // Where the session was when the fade started. Without a fade, nothing is rewound.
        var fadeStart: PlaybackPoint? = null
        val progress = FadeProgress(lowest = volume)
        var faded = false
        var stillAwake = false
        var paused = false
        var rewoundTo: Duration? = null
        try {
            // Turned off, or pointless when nothing is playing locally or the volume can't go any lower.
            if (fade.isPositive() && wasPlaying && !audio.isVolumeFixed && volume > minVolume) {
                if (session != null) {
                    fadeStart = session.current()
                    log.record { "rewind: fade starts at $fadeStart" }
                }
                faded = true
                fadeOut(minVolume, progress)
            }
        } catch (e: StillAwake) {
            stillAwake = true
            throw e
        } finally {
            withContext(NonCancellable) {
                try {
                    if (stillAwake) {
                        log.record { "sleep: still awake, playback goes on" }
                    } else {
                        // Both: the pause button also reaches players without session access, and is harmless otherwise.
                        session?.pause()
                        audio.pause()
                        log.record { "sleep: pause requested (session: ${session != null}), playing=${audio.isPlaying}" }
                        // Lets the player pause before reading where it stopped, and before the volume goes back up.
                        if (fadeStart != null || audio.volume != volume) delay(restoreDelay)
                        paused = !audio.isPlaying
                        rewoundTo = fadeStart?.let { start -> session?.let { rewind(it, start) } }
                    }
                } finally {
                    // Whatever failed before: the volume must never stay low.
                    restore(volume, evenIfPlaying = stillAwake)
                }
            }
        }
        return SleepReport(
            wasPlaying = wasPlaying,
            volumeBefore = volume,
            fadedTo = if (faded) progress.lowest else null,
            volumeRefused = progress.refused,
            paused = paused,
            rewoundTo = rewoundTo,
        )
    }

    /**
     * Where playback resumes: where the fade started, minus [REWIND_MARGIN] of the fade, and at most the [fade] played
     * at [MAX_REWIND_SPEED] before [end]. Positions are in media time, so the playback speed is accounted for.
     * @return `null` when there is nothing to rewind, e.g. when another item started playing during the fade.
     */
    fun rewindTarget(start: PlaybackPoint, end: PlaybackPoint): Duration? {
        if (start.item != end.item) return null
        val faded = end.position - start.position
        // Moved back during the fade, e.g. by the user.
        if (faded <= Duration.ZERO) return null
        val rewind = minOf(faded * (1 + REWIND_MARGIN), fade * MAX_REWIND_SPEED)
        return (end.position - rewind).coerceAtLeast(Duration.ZERO)
    }

    /** @return where playback was rewound, `null` when it wasn't. */
    private fun rewind(session: PlayingMedia, start: PlaybackPoint): Duration? {
        // The player ignored the pause: rewinding would only replay what is being heard.
        if (audio.isPlaying) {
            log.record { "rewind: skipped, still playing" }
            return null
        }
        val end = session.current()
        val target = end?.let { rewindTarget(start, it) }
        log.record { "rewind: from $end to $target" }
        if (target != null) session.seekTo(target)
        return target
    }

    /**
     * Delays after each of [steps] volume steps, summing up to the [fade]. They grow as the volume gets lower, the last
     * one about twice as long as the first, so that the fade is the most gradual when it gets quiet.
     */
    fun fadeStepDelays(steps: Int): List<Duration> {
        if (steps <= 0) return emptyList()
        val weights = List(steps) { 1.0 + it.toDouble() / steps }
        val total = weights.sum()
        return weights.map { fade * (it / total) }
    }

    private class FadeProgress(var lowest: Int, var refused: Boolean = false)

    private suspend fun fadeOut(min: Int, progress: FadeProgress) {
        val steps = audio.volume - min
        log.record { "fade: $steps steps over $fade" }
        // Bounded: the volume may never reach `min` (user interaction, OEM policies…).
        for (stepDelay in fadeStepDelays(steps)) {
            val before = audio.volume
            audio.lowerVolume()
            delay(stepDelay)
            val now = audio.volume
            progress.lowest = minOf(progress.lowest, now)
            if (now <= min) return
            // Refused (e.g. Android 17 background audio restrictions, which fail silently), or raised by the user: the
            // rest of the fade would only delay the pause.
            if (now >= before) {
                log.record { "fade: volume not lowered ($before to $now), pausing now" }
                progress.refused = true
                return
            }
        }
        log.record { "fade: stopped at volume ${audio.volume}, above the minimum $min" }
    }

    /** @param evenIfPlaying whether playback goes on on purpose, see [StillAwake]. */
    private fun restore(volume: Int, evenIfPlaying: Boolean) {
        if (audio.volume == volume) return
        // The player ignored the pause request: keep it quiet rather than blasting audio at full volume.
        if (audio.isPlaying && !evenIfPlaying) {
            log.record { "restore: skipped, still playing at volume ${audio.volume}" }
            return
        }
        audio.restoreVolume(volume)
        log.record { "restore: volume $volume requested, now ${audio.volume}" }
    }
}
