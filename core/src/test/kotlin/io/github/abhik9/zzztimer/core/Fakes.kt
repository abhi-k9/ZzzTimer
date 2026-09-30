package io.github.abhik9.zzztimer.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FakeClock(var wall: Long = 1_000_000L, var elapsed: Long = 50_000L) : DeviceClock {
    override fun wallMillis() = wall
    override fun elapsedMillis() = elapsed

    fun advance(duration: Duration) {
        wall += duration.inWholeMilliseconds
        elapsed += duration.inWholeMilliseconds
    }
}

class FakeDisplay(var available: Boolean = true) : TimerDisplay {
    var shown: Timer? = null
    var timeout: Duration? = null

    override fun isAvailable() = available
    override fun current() = shown

    override fun show(timer: Timer, timeout: Duration) {
        shown = timer
        this.timeout = timeout
    }

    override fun hide() {
        shown = null
        timeout = null
    }
}

class FakeTrigger(var permitted: Boolean = true, var armable: Boolean = true) : SleepTrigger {
    var armed: Timer? = null
    var disarms = 0

    override fun isPermitted() = permitted

    override fun arm(timer: Timer): Boolean {
        if (armable) armed = timer
        return armable
    }

    override fun disarm() {
        armed = null
        disarms++
    }
}

class FakeAudio(
    override var volume: Int = 10,
    override val minVolume: Int = 0,
    override var isPlaying: Boolean = true,
    override val isVolumeFixed: Boolean = false,
    /** Whether the player honors [pause]. */
    private val pausable: Boolean = true,
) : MediaAudio {
    val events = mutableListOf<String>()

    /** Called right before pausing, while still playing. */
    var onPause: () -> Unit = {}

    override fun lowerVolume() {
        volume = (volume - 1).coerceAtLeast(minVolume)
        events += "lower"
    }

    override fun restoreVolume(volume: Int) {
        this.volume = volume
        events += "set:$volume"
    }

    override fun pause() {
        onPause()
        if (pausable) isPlaying = false
        events += "pause"
    }
}

/**
 * A media session playing through [audio] at [speed]: its position follows [clock] (in milliseconds) while
 * [FakeAudio.isPlaying]. [seekable] `false` stands for players that can't tell their position, e.g. live streams.
 */
class FakeMedia(
    private val audio: FakeAudio,
    private val clock: () -> Long,
    start: Duration = 100.seconds,
    private val speed: Double = 1.0,
    private val seekable: Boolean = true,
) : PlayingMedia {
    val seeks = mutableListOf<Duration>()
    private var position = start
    private var since = clock()

    init {
        // The position moves until the player pauses.
        audio.onPause = ::advance
    }

    override fun current(): PlaybackPoint? {
        advance()
        return if (seekable) PlaybackPoint("episode", position) else null
    }

    override fun seekTo(position: Duration) {
        advance()
        this.position = position
        seeks += position
        audio.events += "seek"
    }

    private fun advance() {
        val now = clock()
        if (audio.isPlaying) position += (now - since).milliseconds * speed
        since = now
    }
}
