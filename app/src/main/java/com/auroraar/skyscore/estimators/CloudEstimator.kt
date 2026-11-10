package com.auroraar.skyscore.estimators

import com.auroraar.skyscore.model.CloudClass
import com.auroraar.skyscore.model.FrameFeatures
import com.auroraar.skyscore.model.Phase

data class CloudEstimate(val cover: Float, val confidence: Float, val cloudClass: CloudClass)

/**
 * Addendum §6. Classical estimator only (v3's learned cloud head is not part of
 * this build): the caller guarantees features are computed inside the eroded
 * sky mask, so buildings and mountains are never counted as cloud.
 *
 * Night is handled honestly: stars are positive evidence of clear sky, but the
 * absence of stars is not evidence of cloud, so confidence stays low and the
 * score shrinks toward the neutral prior.
 */
object CloudEstimator {

    fun daytime(f: FrameFeatures): CloudEstimate {
        if (f.fSky < 0.03f) return CloudEstimate(0.25f, 0f, CloudClass.PARTLY)

        val grey = ((0.16f - f.blueDominance) / 0.36f).coerceIn(0f, 1f)
        val bright = (f.skyMeanLum / 0.75f).coerceIn(0f, 1f)
        val texture = (f.textureEnergy / 0.09f).coerceIn(0f, 1f)
        val desaturated = ((0.45f - f.saturation) / 0.45f).coerceIn(0f, 1f)

        // A warm, saturated sunset is not a grey overcast sky.
        val warmCorrection = 1f -
            0.55f * ((f.warmth - 1.10f) / 0.40f).coerceIn(0f, 1f) *
            (f.saturation / 0.40f).coerceIn(0f, 1f)
        val greyAdjusted = grey * warmCorrection.coerceIn(0.35f, 1f)

        val cover = (
            0.65f * greyAdjusted * (0.55f + 0.45f * bright) +
                0.25f * texture +
                0.10f * desaturated
            ).coerceIn(0f, 1f)

        val conf = (
            0.35f +
                0.45f * f.maskConfidence +
                0.20f * if (f.fSky > 0.20f) 1f else 0f
            ).coerceIn(0f, 0.92f)

        return CloudEstimate(cover, conf, classify(cover))
    }

    fun night(f: FrameFeatures): CloudEstimate {
        if (f.fSky < 0.03f) return CloudEstimate(0.25f, 0f, CloudClass.PARTLY)

        val stars = f.starCount
        return when {
            stars >= 25 -> CloudEstimate(0.05f, 0.75f, CloudClass.CLEAR)
            stars >= 8 -> CloudEstimate(0.35f, 0.60f, CloudClass.PARTLY)
            stars >= 2 -> CloudEstimate(0.60f, 0.40f, CloudClass.OVERCAST)
            else -> {
                // No stars: could be cloud, could be a sensor that just can't see them.
                // Uniform bright glow with no texture leans overcast, with low confidence.
                val glow = (f.skyMeanLum / 0.18f).coerceIn(0f, 1f)
                val flat = 1f - (f.textureEnergy / 0.06f).coerceIn(0f, 1f)
                val cover = (0.20f + 0.35f * glow * flat).coerceIn(0f, 1f)
                CloudEstimate(cover, 0.18f, classify(cover))
            }
        }
    }

    /**
     * Blend day and night estimates by how dark the scene is. Twilight uses
     * half the night model because the camera still has some signal.
     */
    fun blend(day: CloudEstimate, night: CloudEstimate, phaseProb: List<Float>): CloudEstimate {
        val nightWeight = (
            phaseProb.getOrElse(Phase.TWILIGHT.ordinal) { 0f } * 0.5f +
                phaseProb.getOrElse(Phase.NIGHT.ordinal) { 0f }
            ).coerceIn(0f, 1f)
        val cover = day.cover * (1f - nightWeight) + night.cover * nightWeight
        val conf = day.confidence * (1f - nightWeight) + night.confidence * nightWeight
        return CloudEstimate(cover.coerceIn(0f, 1f), conf.coerceIn(0f, 1f), classify(cover))
    }

    fun classify(cover: Float): CloudClass = when {
        cover < 0.20f -> CloudClass.CLEAR
        cover < 0.60f -> CloudClass.PARTLY
        cover <= 0.85f -> CloudClass.OVERCAST
        else -> CloudClass.HEAVY
    }
}
