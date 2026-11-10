package com.auroraar.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.asin
import kotlin.math.sqrt

/**
 * Gravity-based camera elevation (0 = looking at the horizon, +90 = zenith).
 * Used for the sky-mask prior and for visibility when the phone points down.
 */
class PoseReader(context: Context) {

    @Volatile
    var elevationDeg: Float = 0f
        private set

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val g = event.values
            val mag = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
            if (mag < 0.1f) return
            // Camera (back, -z) elevation: horizon 0, zenith +90, ground -90.
            // Gravity sensor matches the accelerometer at rest, so +z points up
            // when the screen faces up; negate to get the back-camera pointing.
            val measured = Math.toDegrees(asin((-g[2] / mag).coerceIn(-1f, 1f)).toDouble()).toFloat()
            elevationDeg += (measured - elevationDeg) * 0.2f
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        sensor?.let { sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager?.unregisterListener(listener)
    }
}
