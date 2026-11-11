package com.auroraar.skyscore.policy

import com.auroraar.skyscore.model.Gate
import com.auroraar.skyscore.model.Phase
import com.auroraar.skyscore.model.RatingBand
import com.auroraar.skyscore.model.SubScores
import kotlin.math.roundToInt

/**
 * Pure formula from addendum §9 and §5. No Android dependencies so the §9.3
 * table and the monotonicity properties are testable on the JVM.
 */
object SkyScoreFormula {

    /** Darkness anchors from the product table (§9.3), indexed like [Phase]. */
    val DARKNESS_ANCHORS = floatArrayOf(0.00f, 0.20f, 0.375f, 1.00f)

    const val KAPPA_SUNSET = 0.25f
    const val KAPPA_TWILIGHT = 0.35f
    const val KAPPA_NIGHT = 0.50f

    /** Expected anchor value given a phase distribution - continuous through dusk. */
    fun darkness(phaseProb: List<Float>): Float {
        var d = 0f
        for (i in 0 until 4) {
            val p = phaseProb.getOrElse(i) { 0f }
            // Corrupted probabilities fail closed: darkness 0 blocks the gate.
            if (!p.isFinite() || p < 0f) return 0f
            d += p * DARKNESS_ANCHORS[i]
        }
        return if (d.isFinite()) d.coerceIn(0f, 1f) else 0f
    }

    /** Cloud penalty depends on how dark the sky already is. */
    fun kappa(phaseProb: List<Float>): Float {
        fun p(i: Int): Float = phaseProb.getOrElse(i) { 0f }.let { if (it.isFinite()) it else 0f }
        val sunset = p(Phase.SUNSET.ordinal)
        val twilight = p(Phase.TWILIGHT.ordinal)
        val night = p(Phase.NIGHT.ordinal)
        val k = sunset * KAPPA_SUNSET + twilight * KAPPA_TWILIGHT + night * KAPPA_NIGHT
        return if (k.isFinite()) k.coerceIn(0f, 1f) else 0f
    }

    /**
     * Opacity-weighted effective cloud cover. Low confidence shrinks toward the
     * neutral prior (0.25) instead of pretending the sky is clear.
     */
    fun cloudEffective(cover: Float, confidence: Float): Float {
        if (!cover.isFinite() || !confidence.isFinite()) return 0.25f
        val c = confidence.coerceIn(0f, 1f)
        return c * cover.coerceIn(0f, 1f) + (1f - c) * 0.25f
    }

    fun cloudScore(cover: Float, confidence: Float, phaseProb: List<Float>): Float {
        val effective = cloudEffective(cover, confidence)
        return (1f - kappa(phaseProb) * effective).coerceIn(0f, 1f)
    }

    /** Environmental lighting never drops below 0.70 (§9.1). */
    fun lightingScore(lightPollution: Float): Float {
        if (!lightPollution.isFinite()) return 0.70f
        return (1f - 0.30f * lightPollution.coerceIn(0f, 1f)).coerceAtLeast(0.70f)
    }

    fun visibility(fSky: Float, maskConfidence: Float): Float {
        if (!fSky.isFinite() || !maskConfidence.isFinite()) return 0f
        val ramp = smoothstep(0.03f, 0.30f, fSky.coerceIn(0f, 1f))
        return (ramp * (0.7f + 0.3f * maskConfidence.coerceIn(0f, 1f))).coerceIn(0f, 1f)
    }

    /** Multiplicative combination: any bad factor matters. */
    fun score(sub: SubScores): Int {
        val parts = floatArrayOf(sub.brightness, sub.cloud, sub.visibility, sub.lighting)
        if (parts.any { !it.isFinite() }) return 0
        val product = parts.fold(1f) { acc, v -> acc * v.coerceIn(0f, 1f) }
        if (!product.isFinite()) return 0
        return (100f * product).coerceIn(0f, 100f).roundToInt()
    }

    /**
     * A covered lens is dark, featureless and temporally dead. Checking only
     * meanLum was not enough: with limited-range luma a covered lens sat at
     * 0.063, above the old 0.004 threshold. Loosening texture/noise instead
     * falsely flagged a dark overcast live sky, so the decision requires all
     * three signals to agree: low luminance, almost no spatial detail and
     * almost no temporal noise.
     */
    fun cameraBlocked(
        meanLum: Float,
        textureEnergy: Float,
        noiseSigma: Float,
        starCount: Int,
        lux: Float?,
    ): Boolean {
        if (!meanLum.isFinite() || !textureEnergy.isFinite() || !noiseSigma.isFinite()) return true
        val digitalBlack = meanLum < 0.10f &&
            textureEnergy < 0.035f &&
            noiseSigma < 0.004f &&
            starCount == 0
        val brightAmbientButDarkFrame = lux != null &&
            lux.isFinite() &&
            lux > 50f &&
            meanLum < 0.08f &&
            starCount == 0
        return digitalBlack || brightAmbientButDarkFrame
    }

    fun band(score: Int): RatingBand = when {
        score >= 90 -> RatingBand.EXCELLENT
        score >= 70 -> RatingBand.GREAT
        score >= 50 -> RatingBand.GOOD
        score >= 30 -> RatingBand.FAIR
        score >= 1 -> RatingBand.LIMITED
        else -> RatingBand.UNAVAILABLE
    }

    fun gate(
        darkness: Float,
        visibility: Float,
        cameraBlocked: Boolean,
        creative: Boolean,
    ): Gate = when {
        cameraBlocked -> Gate.CAMERA_BLOCKED
        !visibility.isFinite() || visibility < 0.10f -> Gate.NO_SKY
        !darkness.isFinite() || (!creative && darkness < 0.08f) -> Gate.BLOCKED_DAYLIGHT
        else -> Gate.OPEN
    }

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge0 == edge1) return if (x < edge0) 0f else 1f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
}
