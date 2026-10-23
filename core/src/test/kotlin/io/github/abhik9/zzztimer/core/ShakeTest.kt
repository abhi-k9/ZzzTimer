package io.github.abhik9.zzztimer.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShakeTest {

    private val g = ShakeDetector.STANDARD_GRAVITY.toFloat()
    private val detector = ShakeDetector(thresholdG = 2.0)

    /** Feeds a sample of [gs] g at [time]: @return whether it completes a shake. */
    private fun sample(time: Long, gs: Float) = detector.onSample(time, 0f, 0f, gs * g)

    @Test
    fun `strength levels`() {
        assertEquals(1.6, ShakeSetting.thresholdG(1), 1e-9)
        assertEquals(2.4, ShakeSetting.thresholdG(ShakeSetting.DEFAULT_LEVEL), 1e-9)
        assertEquals(3.2, ShakeSetting.thresholdG(5), 1e-9)
        assertEquals(3.2, ShakeSetting.thresholdG(9), 1e-9)
    }

    @Test
    fun `a phone at rest weighs 1 g`() = assertEquals(1.0, ShakeDetector.gForce(0f, g, 0f), 1e-6)

    @Test
    fun `three strong peaks within a second are a shake`() {
        assertFalse(sample(0, 2.5f))
        assertFalse(sample(300, 2.5f))
        assertTrue(sample(600, 2.5f))
        // Starts over.
        assertFalse(sample(700, 2.5f))
    }

    @Test
    fun `a single bump, weak moves or slow peaks are not`() {
        assertFalse(sample(0, 3f))
        for (time in 100L..1_000L step 100) assertFalse(sample(time, 1.5f))
        assertFalse(sample(2_000, 2.5f))
        assertFalse(sample(4_000, 2.5f))
        assertFalse(sample(6_000, 2.5f))
    }

    @Test
    fun `samples of one peak count once`() {
        assertFalse(sample(0, 2.5f))
        assertFalse(sample(20, 2.5f))
        assertFalse(sample(40, 2.5f))
        assertFalse(sample(300, 2.5f))
        assertTrue(sample(600, 2.5f))
    }
}
