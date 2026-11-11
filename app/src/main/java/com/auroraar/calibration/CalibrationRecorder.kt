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
    private val samples = ArrayDeque<String>()
    private var lastSampleMs = 0L

    @Volatile
    var recording: Boolean = false
        private set

    @Volatile
    var sampleCount: Int = 0
        private set

    private data class Labels(val phase: String, val okta: Int, val environment: String)

    // One volatile reference: a sample can never combine a new phase with an
    // old okta/environment while the user taps two chips in quick succession.
    @Volatile
    private var labels = Labels("night", 2, "rural")

    var phaseLabel: String
        get() = labels.phase
        set(value) {
            labels = labels.copy(phase = value)
        }

    var okta: Int
        get() = labels.okta
        set(value) {
            labels = labels.copy(okta = value.coerceIn(0, 8))
        }

    var environment: String
        get() = labels.environment
        set(value) {
            labels = labels.copy(environment = value)
        }

    /** Replaces all three labels at once, e.g. when a recording starts. */
    @Synchronized
    fun setLabels(phase: String, okta: Int, environment: String) {
        labels = Labels(phase, okta.coerceIn(0, 8), environment)
    }

    @Synchronized
    fun start() {
        samples.clear()
        sampleCount = 0
        lastSampleMs = 0L
        recording = true
    }

    @Synchronized
    fun stop() {
        recording = false
    }

    @Synchronized
    fun clear() {
        samples.clear()
        sampleCount = 0
        lastSampleMs = 0L
    }

    @Synchronized
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

        // One read of the immutable label snapshot.
        val sampleLabels = labels
        val okta = sampleLabels.okta
        val environment = sampleLabels.environment

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
            append(conditions.phaseProb.take(4).joinToString(",") { num(it) })
            append("],")
            append("\"cloudCover\":").append(num(conditions.cloudCover)).append(',')
            append("\"cloudConf\":").append(num(conditions.cloudConf)).append(',')
            append("\"cloudClass\":\"").append(conditions.cloudClass.name.lowercase(Locale.US)).append("\",")
            append("\"skyVisibility\":").append(num(conditions.skyVisibility)).append(',')
            append("\"lightPollution\":").append(num(conditions.lightPollution)).append(',')
            append("\"skyScore\":").append(conditions.skyScore.coerceIn(0, 100)).append(',')
            append("\"gate\":\"").append(conditions.gate.name.lowercase(Locale.US)).append("\",")
            append("\"subScores\":{")
            append("\"brightness\":").append(num(conditions.subScores.brightness)).append(',')
            append("\"cloud\":").append(num(conditions.subScores.cloud)).append(',')
            append("\"visibility\":").append(num(conditions.subScores.visibility)).append(',')
            append("\"lighting\":").append(num(conditions.subScores.lighting))
            append("},")
            append("\"fSky\":").append(num(features.fSky)).append(',')
            append("\"meanLum\":").append(num(features.meanLum)).append(',')
            append("\"skyMeanLum\":").append(num(features.skyMeanLum)).append(',')
            append("\"skyMeanLinear\":").append(num(features.skyMeanLinear)).append(',')
            append("\"saturation\":").append(num(features.saturation)).append(',')
            append("\"warmth\":").append(num(features.warmth)).append(',')
            append("\"blueDominance\":").append(num(features.blueDominance)).append(',')
            append("\"gradientStrength\":").append(num(features.gradientStrength)).append(',')
            append("\"foregroundLum\":").append(num(features.foregroundLum)).append(',')
            append("\"brightBlobCount\":").append(features.brightBlobCount.coerceIn(0, 1000)).append(',')
            append("\"brightBlobFraction\":").append(num(features.brightBlobFraction)).append(',')
            append("\"skyLumBands\":[")
            append(features.skyLumBands.take(8).joinToString(",") { num(it) })
            append("],")
            append("\"noiseSigma\":").append(num(features.noiseSigma)).append(',')
            append("\"starCount\":").append(features.starCount.coerceIn(0, 400)).append(',')
            append("\"textureEnergy\":").append(num(features.textureEnergy)).append(',')
            append("\"clippedFraction\":").append(num(features.clippedFraction)).append(',')
            append("\"labels\":{")
            append("\"phase\":\"").append(escape(sampleLabels.phase)).append("\",")
            append("\"okta\":").append(okta).append(',')
            append("\"lighting\":\"").append(escape(environment)).append("\"")
            append("}}")
        }
        if (samples.size >= MAX_SAMPLES) samples.removeFirst()
        samples.addLast(json)
        sampleCount = samples.size
    }

    /** Writes the current session to files/calibration and returns the file. */
    @Synchronized
    fun export(): File {
        val snapshot = samples.toList()
        val dir = File(appContext.filesDir, "calibration").apply { mkdirs() }
        val file = File(dir, "calib-${System.currentTimeMillis()}.json")
        file.writeText(
            buildString {
                append("[\n")
                snapshot.forEachIndexed { index, sample ->
                    append(sample)
                    if (index < snapshot.size - 1) append(',')
                    append('\n')
                }
                append("]\n")
            },
        )
        return file
    }

    private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    private fun num(value: Float?): String {
        if (value == null || !value.isFinite()) return "null"
        return String.format(Locale.US, "%.5f", value)
    }

    private fun escape(value: String): String = buildString {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append(String.format(Locale.US, "\\u%04x", ch.code)) else append(ch)
            }
        }
    }

    companion object {
        /** ~1 hour at 1 Hz; keeps memory and the exported JSON bounded. */
        const val MAX_SAMPLES = 3_600
    }
}
