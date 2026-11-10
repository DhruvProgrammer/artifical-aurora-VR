package com.auroraar.skyscore.estimators

import com.auroraar.skyscore.model.ExposureInfo
import com.auroraar.skyscore.model.FrameFeatures
import kotlin.math.log10
import kotlin.math.pow

data class BrightnessEstimate(
    val logLum: Float,
    val confidence: Float,
    val photometric: Boolean,
)

/**
 * Addendum §4.2 - reconstruct sky luminance from the exposure triplet, then fall
 * back to appearance cues when the device doesn't expose metadata (or while AE
 * is mid-transition).
 *
 * L_sky ~= (12.5 * N^2 / (t * S)) * (Y_sky / Y_target) * 2^(-AE compensation)
 */
object BrightnessEstimator {

    private const val K = 12.5f
    private const val Y_TARGET = 0.16f

    fun estimate(
        features: FrameFeatures,
        exposure: ExposureInfo?,
    ): BrightnessEstimate {
        photometric(features, exposure)?.let { return it }
        return appearance(features)
    }

    private fun photometric(f: FrameFeatures, e: ExposureInfo?): BrightnessEstimate? {
        if (e == null) return null
        val t = e.exposureSec ?: return null
        val iso = e.iso ?: return null
        val n = e.fNumber ?: return null
        if (t <= 0f || iso <= 0f || n <= 0f) return null

        val y = f.skyMeanLinear
        // Clipped or crushed sky pixels carry no photometric meaning.
        if (y < 0.01f || y > 0.95f) return null

        val lMid = K * n * n / (t * iso)
        val aeScale = 2f.pow(-e.aeCompensationEv)
        val lSky = lMid * (y / Y_TARGET) * aeScale
        val logLum = log10(lSky.coerceAtLeast(1e-6f))
        if (!logLum.isFinite()) return null

        var confidence = 0.85f
        if (!e.aeConverged) confidence *= 0.5f
        return BrightnessEstimate(logLum.coerceIn(-4f, 5f), confidence, true)
    }

    /**
     * Independent of absolute brightness: stars are the strongest positive
     * evidence of a dark sky, warmth of a sunset, blue of dusk, and a bright
     * featureless frame of daytime.
     */
    private fun appearance(f: FrameFeatures): BrightnessEstimate {
        val logLum = when {
            f.starCount >= 25 -> -1.9f
            f.starCount >= 8 -> -1.2f
            f.starCount >= 1 -> -0.3f
            f.warmth >= 1.25f -> 2.1f
            f.blueDominance >= 0.18f -> 0.8f
            f.skyMeanLum < 0.10f -> -1.0f
            f.skyMeanLum > 0.60f -> 2.6f
            else -> 1.2f
        }
        return BrightnessEstimate(logLum, 0.32f, false)
    }
}
