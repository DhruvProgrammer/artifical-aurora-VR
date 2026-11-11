package com.auroraar.skyscore.estimators

import com.auroraar.skyscore.model.AstroPrior
import com.auroraar.skyscore.model.ExposureInfo
import com.auroraar.skyscore.model.FrameFeatures
import com.auroraar.skyscore.model.Phase
import kotlin.math.exp
import kotlin.math.ln

/**
 * Addendum §4.6. Fuses independent cues into a phase distribution.
 *
 * Every cue returns per-class log-likelihoods; weights are multiplied by each
 * cue's own confidence and missing cues contribute nothing. The result is a
 * softmax plus a small set of hard rules for the cases the spec calls out
 * explicitly (bright overcast day, warm sunset, dark noisy night).
 */
object PhaseClassifier {

    // Class-conditionals for log10 sky luminance (cd/m^2), §4.6.
    private val MU = floatArrayOf(3.4f, 2.2f, 0.4f, -1.8f)
    private val SIGMA = floatArrayOf(0.55f, 0.60f, 0.95f, 1.00f)

    private const val W_LUMINANCE = 1.0f
    private const val W_APPEARANCE = 0.8f
    private const val W_LUX = 0.25f
    private const val W_ASTRO = 0.6f
    private const val W_ASTRO_NO_LOCATION = 0.2f
    private const val W_NOISE = 0.4f

    fun fuse(
        logLum: Float,
        lumValid: Boolean,
        lumConfidence: Float,
        features: FrameFeatures,
        exposure: ExposureInfo?,
        lux: Float?,
        astro: AstroPrior,
    ): FloatArray {
        val safeLogLum = if (logLum.isFinite()) logLum.coerceIn(-6f, 6f) else -1.8f
        val safeConf = if (lumConfidence.isFinite()) lumConfidence.coerceIn(0f, 1f) else 0f
        val logits = FloatArray(4)

        // Cue A - photometric / estimated sky luminance.
        if (lumValid) {
            val w = W_LUMINANCE * safeConf
            for (k in 0 until 4) {
                val z = (safeLogLum - MU[k]) / SIGMA[k]
                logits[k] += w * (-0.5f * z * z)
            }
        }

        // Cue B - sky appearance (colour, stars, clipping, noise texture).
        appearanceLogits(features).let { cue ->
            for (k in 0 until 4) logits[k] += W_APPEARANCE * cue[k]
        }

        // Cue C - ambient light sensor, low weight and only when plausible.
        if (lux != null && lux.isFinite() && lumValid) {
            val contradicted = kotlin.math.abs(luxToLogLum(lux) - safeLogLum) > 3.0f
            if (!contradicted) {
                val cue = luxLogits(lux)
                for (k in 0 until 4) logits[k] += W_LUX * cue[k]
            }
        }

        // Cue D - offline astronomy prior.
        val astroWeight = if (astro.hasLocation) W_ASTRO else W_ASTRO_NO_LOCATION
        val astroConf = if (astro.confidence.isFinite()) astro.confidence.coerceIn(0f, 1f) else 0f
        for (k in 0 until 4) {
            val p = astro.prob.getOrElse(k) { 0.01f }
            val safeP = if (p.isFinite()) p.coerceIn(0f, 1f) else 0.01f
            logits[k] += astroWeight * astroConf * (ln(safeP + 0.01f) + 0.4f)
        }

        // Cue E - sensor noise / ISO. High noise is independent of AE normalisation.
        noiseLogits(features, exposure).let { cue ->
            for (k in 0 until 4) logits[k] += W_NOISE * cue[k]
        }

        for (k in 0 until 4) {
            logits[k] = if (logits[k].isFinite()) logits[k].coerceIn(-80f, 80f) else 0f
        }
        var prob = softmax(logits)
        prob = applyHardRules(prob, safeLogLum, lumValid, features)
        return prob
    }

    private fun appearanceLogits(f: FrameFeatures): FloatArray {
        val hasStars = f.starCount >= 3
        val veryBlue = f.blueDominance >= 0.18f
        val warm = (f.warmth - 1.05f) / 0.35f

        val day = 2.0f * ((f.skyMeanLum - 0.5f).coerceIn(-0.35f, 0.35f)) * 2f +
            (if (f.clippedFraction > 0.004f) 1.2f else 0f) +
            (if (veryBlue && f.skyMeanLum > 0.35f) 0.4f else 0f) +
            (if (hasStars) -2.5f else 0f)

        val sunset = 2.2f * warm.coerceIn(0f, 1.5f) +
            (if (hasStars) -1.2f else 0f)

        val twilight = 0.8f * ((f.blueDominance - 0.05f) / 0.25f).coerceIn(0f, 1.2f) + 0.3f +
            (if (hasStars) -0.8f else 0f)

        val night = (if (hasStars) 1.6f else 0f) +
            (if (f.skyMeanLum < 0.12f) 1.2f else 0f) +
            (if (f.noiseSigma > 0.04f) 0.6f else 0f)

        return floatArrayOf(day, sunset, twilight, night)
    }

    private fun luxLogits(lux: Float): FloatArray = when {
        lux > 5_000f -> floatArrayOf(0f, -2.5f, -5f, -7f)
        lux > 500f -> floatArrayOf(-1f, 0f, -1.5f, -4f)
        lux > 10f -> floatArrayOf(-3f, -1f, 0f, -1.5f)
        else -> floatArrayOf(-6f, -3f, -1f, 0f)
    }

    private fun luxToLogLum(lux: Float): Float = kotlin.math.log10(lux.coerceAtLeast(0.01f)) - 0.5f

    private fun noiseLogits(f: FrameFeatures, exposure: ExposureInfo?): FloatArray {
        val iso = exposure?.iso
        val highIso = iso != null && iso > 800f
        val lowIso = iso != null && iso < 200f
        return when {
            f.noiseSigma > 0.06f -> floatArrayOf(-3f, -1f, 0.5f, 1.5f)
            f.noiseSigma > 0.03f -> floatArrayOf(-2f, -0.5f, 0.8f, 0.6f)
            highIso -> floatArrayOf(-2f, -0.5f, 0.6f, 1.0f)
            lowIso -> floatArrayOf(0.8f, 0.2f, -0.2f, -0.6f)
            else -> floatArrayOf(0.2f, 0.2f, 0.1f, 0f)
        }
    }

    private fun applyHardRules(
        prob: FloatArray,
        logLum: Float,
        lumValid: Boolean,
        f: FrameFeatures,
    ): FloatArray {
        var p = prob

        // Bright overcast day stays DAY regardless of cloud cover.
        if (lumValid && logLum > 2.0f && f.warmth < 1.15f) {
            p = floatArrayOf(0.92f, 0.05f, 0.02f, 0.01f)
        }

        // Warm horizon gradient at mid brightness => sunset.
        if (lumValid && f.warmth >= 1.25f && logLum in 1.2f..3.2f) {
            p = floatArrayOf(0.05f, 0.85f, 0.08f, 0.02f)
        }

        // Stars are positive evidence of a dark, clear sky.
        if (f.starCount >= 5) {
            val night = (p[Phase.NIGHT.ordinal] + 0.45f).coerceAtMost(0.95f)
            val rest = 1f - night
            val twilightShare = p[Phase.TWILIGHT.ordinal]
            val otherSum = p[Phase.DAYLIGHT.ordinal] + p[Phase.SUNSET.ordinal] + twilightShare
            val scale = if (otherSum > 0.01f) rest / otherSum else 0f
            p = floatArrayOf(
                p[Phase.DAYLIGHT.ordinal] * scale,
                p[Phase.SUNSET.ordinal] * scale,
                twilightShare * scale,
                night,
            )
        }

        // Never let a very dark photometric reading be classified as day.
        if (lumValid && logLum < -0.5f && (p[Phase.DAYLIGHT.ordinal] > 0.4f || p[Phase.SUNSET.ordinal] > 0.4f)) {
            val tw = p[Phase.TWILIGHT.ordinal] + 0.25f
            val ni = p[Phase.NIGHT.ordinal] + 0.75f
            val sum = tw + ni
            p = floatArrayOf(0f, 0f, tw / sum, ni / sum)
        }

        return normalize(p)
    }

    fun normalize(p: FloatArray): FloatArray {
        val sum = p.sum()
        if (!sum.isFinite() || sum <= 1e-6f) return floatArrayOf(0f, 0f, 0f, 1f)
        return FloatArray(4) { (p[it] / sum).coerceIn(0f, 1f) }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exps = FloatArray(4) { exp(logits[it] - max) }
        val sum = exps.sum()
        if (sum <= 1e-6f || !sum.isFinite()) return floatArrayOf(0f, 0f, 0f, 1f)
        return FloatArray(4) { exps[it] / sum }
    }
}
