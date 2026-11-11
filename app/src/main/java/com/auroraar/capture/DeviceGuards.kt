package com.auroraar.capture

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.util.Log
import java.io.File

/** Reads the device conditions that [RecordingGuards] evaluates. */
class DeviceGuards(private val context: Context) {

    fun check(): RecordingGuardResult {
        val freeBytes = freeBytes()

        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val percent = runCatching {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        }.getOrDefault(-1)
        val charging = runCatching { batteryManager?.isCharging == true }.getOrDefault(false)

        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: 0
            }.getOrDefault(0)
        } else {
            0
        }

        return RecordingGuards.evaluate(freeBytes, percent, charging, thermal)
    }

    fun freeBytes(): Long = runCatching {
        StatFs(storagePath()).availableBytes
    }.getOrDefault(Long.MAX_VALUE)

    fun hasRoomFor(freeBytes: Long = freeBytes()): Boolean {
        if (freeBytes < RecordingGuards.MIN_FREE_BYTES) {
            Log.w(TAG, "Refusing recording: only $freeBytes bytes free")
            return false
        }
        return true
    }

    fun recordingsDir(): File = File(context.getExternalFilesDir(null), "Movies/AuroraAR")

    /** Recordings live on external storage, so guards must watch the same volume. */
    private fun storagePath(): String =
        context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath

    companion object {
        private const val TAG = "DeviceGuards"
    }
}
