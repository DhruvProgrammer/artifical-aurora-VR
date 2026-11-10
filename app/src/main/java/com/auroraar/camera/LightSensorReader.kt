package com.auroraar.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Ambient light in lux, low-passed. Sits near the earpiece, so it is weighted
 * low and cross-checked against sky pixels (addendum §4.4).
 */
class LightSensorReader(context: Context) {

    @Volatile
    var lux: Float? = null
        private set

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val value = event.values.firstOrNull() ?: return
            val current = lux
            lux = if (current == null) value else current + (value - current) * 0.25f
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        sensor?.let { sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sensorManager?.unregisterListener(listener)
    }
}
