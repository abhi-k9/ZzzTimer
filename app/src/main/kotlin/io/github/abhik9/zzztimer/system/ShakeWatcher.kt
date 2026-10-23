package io.github.abhik9.zzztimer.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.abhik9.zzztimer.core.ShakeDetector
import io.github.abhik9.zzztimer.core.ShakeSetting

/**
 * Detects shakes of the strength [level] with the accelerometer, see [ShakeDetector]. Callbacks run on the thread that
 * calls [start], which must have a looper (e.g. the main thread).
 * @param onForce every sample, in g, e.g. to show it while testing.
 */
class ShakeWatcher(
    context: Context,
    level: Int,
    private val onForce: (Double) -> Unit = {},
    private val onShake: () -> Unit,
) : SensorEventListener {

    private val sensors: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val detector = ShakeDetector(ShakeSetting.thresholdG(level))

    /** @return whether shakes can be detected: not without accelerometer. */
    fun start(): Boolean = accelerometer != null && sensors?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME) == true

    fun stop() {
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        onForce(ShakeDetector.gForce(x, y, z))
        // Sensor timestamps are in nanoseconds, on a monotonic timeline.
        if (detector.onSample(event.timestamp / 1_000_000, x, y, z)) onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
