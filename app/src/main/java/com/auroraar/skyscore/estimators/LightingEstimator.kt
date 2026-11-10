package com.auroraar.skyscore.estimators

import com.auroraar.skyscore.model.FrameFeatures
import com.auroraar.skyscore.model.Phase

/**
 * Addendum §8. Environmental light pollution: sky-glow excess, warm sky-glow
 * cast, bright compact blobs in the frame, foreground luminance. Bypassed to a
 * neutral value while the sky is still bright.
 */
object LightingEstimator {

    fun estimate(f: FrameFeatures, phaseProb: List<Float>, lux: Float?): Float {
        val twilightProb = phaseProb.getOrElse(Phase.TWILIGHT.ordinal) { 0f }
        val nightProb = phaseProb.getOrElse(Phase.NIGHT.ordinal) { 0f }
        if (twilightProb + nightProb < 0.25f) return 0.30f

        val glow = ((f.skyMeanLum - 0.01f) / 0.15f).coerceIn(0f, 1f)
        val warmGlow = if (f.warmth > 1.10f) ((f.warmth - 1.10f) / 0.5f).coerceIn(0f, 0.4f) else 0f

        val blobs = (
            (f.brightBlobCount / 12f).coerceIn(0f, 1f) * 0.6f +
                (f.brightBlobFraction / 0.05f).coerceIn(0f, 1f) * 0.4f
            ).coerceIn(0f, 1f)

        val foreground = (f.foregroundLum / 0.40f).coerceIn(0f, 1f)

        val sensor = when {
            lux == null -> 0f
            lux < 1f -> 0f
            lux < 10f -> 0.15f
            lux < 80f -> 0.45f
            else -> 0.75f
        }

        val pollution = (
            0.40f * glow +
                0.15f * warmGlow +
                0.25f * blobs +
                0.10f * foreground +
                0.10f * sensor
            ).coerceIn(0f, 1f)

        return pollution
    }
}
