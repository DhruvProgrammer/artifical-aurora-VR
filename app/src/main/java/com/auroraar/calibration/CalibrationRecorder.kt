package com.auroraar.calibration

import android.content.Context
import android.os.Build
import com.auroraar.skyscore.model.ExposureInfo
import com.auroraar.skyscore.model.FrameFeatures
import com.auroraar.skyscore.model.SkyConditions
import java.io.File
import java.util.Locale

/**
 * Addendum §15.1. Records one labelled sample per second locally so thresholds
 * can be tuned on real phones instead of assumptions. Nothing is uploaded; the
 * user shares the JSON manually.
 */
class CalibrationRecorder(context: Context) {

    private val appContext = context.applicationContext
    private val samples = ArrayList<String>()
    private var lastSampleMs = 0L

    var recording: Boolean = false
        private set
    var sampleCount: Int = 0
        private set

    var phaseLabel: String = "night"
    var okta: Int = 2
    var environment: String = "rural"

    fun start() {
        samples.clear()
        sampleCount = 0
        lastSampleMs = 0L
        recording = true
    }

    fun stop() {
        recording = false
    }

    fun clear() {
        samples.clear()
        sampleCount = 0
        lastSampleMs = 0L
    }

    fun maybeRecord(
        features: FrameFeatures,
        conditions: SkyConditions,
        exposure: ExposureInfo?,
        lux: Float?,
        cameraElevationDeg: Float,
    ) {
        if (!recording) return
        val now = System.currentTimeMillis()
        if (now - lastSampleMs < 1_000L) return
        lastSampleMs = now

        val json = buildString {
            append("{")
            append("\"timestamp\":").append(now).append(',')
            append("\"device\":\"").append(escape(deviceName())).append("\",")
            append("\"exposureSec\":").append(num(exposure?.exposureSec)).append(',')
            append("\"iso\":").append(num(exposure?.iso)).append(',')
            append("\"aperture\":").append(num(exposure?.fNumber)).append(',')
            append("\"aeConverged\":").append(exposure?.aeConverged ?: false).append(',')
            append("\"aeCompensationEv\":").append(num(exposure?.aeCompensationEv)).append(',')
            append("\"lux\":").append(num(lux)).append(',')
            append("\"pitchDeg\":").append(num(cameraElevationDeg)).append(',')
            append("\"skyLogLum\":").append(num(conditions.skyLogLum)).append(',')
            append("\"brightnessConf\":").append(num(conditions.brightnessConf)).append(',')
            append("\"phase\":\"").append(conditions.phase.name.lowercase(Locale.US)).append("\",")
            append("\"phaseProb\":[")
            append(conditions.phaseProb.joinToString(",") { String.format(Locale.US, "%.4f", it) })
            append("],")
            append("\"cloudCover\":").append(num(conditions.cloudCover)).append(',')
            append("\"cloudConf\":").append(num(conditions.cloudConf)).append(',')
            append("\"cloudClass\":\"").append(conditions.cloudClass.name.lowercase(Locale.US)).append("\",")
            append("\"skyVisibility\":").append(num(conditions.skyVisibility)).append(',')
            append("\"lightPollution\":").append(num(conditions.lightPollution)).append(',')
            append("\"skyScore\":").append(conditions.skyScore).append(',')
            append("\"gate\":\"").append(conditions.gate.name.lowercase(Locale.US)).append("\",")
            append("\"fSky\":").append(num(features.fSky)).append(',')
            append("\"warmth\":").append(num(features.warmth)).append(',')
            append("\"blueDominance\":").append(num(features.blueDominance)).append(',')
            append("\"noiseSigma\":").append(num(features.noiseSigma)).append(',')
            append("\"starCount\":").append(features.starCount).append(',')
            append("\"textureEnergy\":").append(num(features.textureEnergy)).append(',')
            append("\"clippedFraction\":").append(num(features.clippedFraction)).append(',')
            append("\"labels\":{")
            append("\"phase\":\"").append(escape(phaseLabel)).append("\",")
            append("\"okta\":").append(okta).append(',')
            append("\"lighting\":\"").append(escape(environment)).append("\"")
            append("}}")
        }
        samples.add(json)
        sampleCount = samples.size
    }

    /** Writes the current session to files/calibration and returns the file. */
    fun export(): File {
        val dir = File(appContext.filesDir, "calibration").apply { mkdirs() }
        val file = File(dir, "calib-${System.currentTimeMillis()}.json")
        file.writeText("[\n" + samples.joinToString(",\n") + "\n]\n")
        return file
    }

    private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    private fun num(value: Float?): String =
        if (value == null || value.isNaN()) "null" else String.format(Locale.US, "%.5f", value)

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
