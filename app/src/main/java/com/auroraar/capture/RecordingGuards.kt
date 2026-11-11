package com.auroraar.capture

/** Safety warnings/refusals checked before a recording starts. */
enum class GuardWarning { LOW_STORAGE, LOW_BATTERY, THERMAL }

enum class GuardRefusal { STORAGE, BATTERY, THERMAL }

data class RecordingGuardResult(
    val refusal: GuardRefusal? = null,
    val warnings: Set<GuardWarning> = emptySet(),
) {
    val canRecord: Boolean get() = refusal == null
}

/**
 * Pure thresholds from the recording addendum §7.2. Kept free of Android so the
 * boundaries are covered by JVM tests; [DeviceGuards] supplies the readings.
 */
object RecordingGuards {

    const val MIN_FREE_BYTES = 100L * 1024 * 1024
    const val WARN_FREE_BYTES = 500L * 1024 * 1024
    const val MIN_BATTERY_PERCENT = 5
    const val WARN_BATTERY_PERCENT = 15

    /** PowerManager thermal status values (API 29+). */
    const val THERMAL_WARN = 2
    const val THERMAL_REFUSE = 4

    fun evaluate(
        freeBytes: Long,
        batteryPercent: Int,
        charging: Boolean,
        thermalStatus: Int,
    ): RecordingGuardResult {
        var refusal: GuardRefusal? = null
        val warnings = mutableSetOf<GuardWarning>()

        if (freeBytes < MIN_FREE_BYTES) {
            refusal = GuardRefusal.STORAGE
        } else if (freeBytes < WARN_FREE_BYTES) {
            warnings += GuardWarning.LOW_STORAGE
        }

        // -1 / unknown readings must not block recording.
        val battery = batteryPercent.coerceIn(0, 100).takeIf { batteryPercent >= 0 }
        if (!charging && battery != null) {
            if (battery < MIN_BATTERY_PERCENT) {
                refusal = refusal ?: GuardRefusal.BATTERY
            } else if (battery < WARN_BATTERY_PERCENT) {
                warnings += GuardWarning.LOW_BATTERY
            }
        }

        if (thermalStatus >= THERMAL_REFUSE) {
            refusal = refusal ?: GuardRefusal.THERMAL
        } else if (thermalStatus >= THERMAL_WARN) {
            warnings += GuardWarning.THERMAL
        }

        return RecordingGuardResult(refusal, warnings)
    }
}
