package io.github.abhik9.zzztimer.core

import kotlin.math.sqrt

/** How strong a shake must be to keep listening (shake to extend), from 1 (gentlest) to 5 (hardest). */
object ShakeSetting {
    const val DEFAULT_LEVEL = 3
    val range = 1..5

    /** Brings a stored or user provided value back into [range]. */
    fun sanitize(level: Int): Int = level.coerceIn(range)

    /** Acceleration needed at [level], in g: 1 g is gravity alone, a phone at rest. */
    fun thresholdG(level: Int): Double = 1.6 + (sanitize(level) - 1) * 0.4
}

/**
 * Detects a deliberate shake in accelerometer samples: [peaks] peaks above [thresholdG] within [windowMillis]. A single
 * bump, e.g. turning over in bed, isn't one.
 */
class ShakeDetector(
    private val thresholdG: Double,
    private val peaks: Int = 3,
    private val windowMillis: Long = 1_200,
    /** Samples of one peak come in bursts: closer ones count once. */
    private val minGapMillis: Long = 80,
) {
    companion object {
        const val STANDARD_GRAVITY = 9.80665

        /** The acceleration of a sample, in g. */
        fun gForce(x: Float, y: Float, z: Float): Double = sqrt((x * x + y * y + z * z).toDouble()) / STANDARD_GRAVITY
    }

    private val peakTimes = ArrayDeque<Long>()

    /**
     * @param timeMillis when the sample was taken, on any monotonic timeline.
     * @return whether this sample completes a shake. Detection then starts over.
     */
    fun onSample(timeMillis: Long, x: Float, y: Float, z: Float): Boolean {
        if (gForce(x, y, z) < thresholdG) return false
        val last = peakTimes.lastOrNull()
        if (last != null && timeMillis - last < minGapMillis) return false
        peakTimes.addLast(timeMillis)
        while (timeMillis - peakTimes.first() > windowMillis) peakTimes.removeFirst()
        if (peakTimes.size < peaks) return false
        peakTimes.clear()
        return true
    }
}
