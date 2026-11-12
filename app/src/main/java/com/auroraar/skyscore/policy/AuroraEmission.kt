package com.auroraar.skyscore.policy

import kotlin.math.exp

/**
 * Height-resolved auroral emission, derived from the observed optical spectrum.
 *
 * A real curtain is not a single colour: the emitting species change with
 * altitude, because collisional de-excitation (quenching) is stronger lower down
 * where the atmosphere is denser. That produces a fixed vertical order, which is
 * why aurora photographs always read blue at the bottom, green through the body
 * and faint red at the top.
 *
 * Measured emission lines and where they come from:
 *
 * - **428.0 nm, blue** - N2+ (ionised molecular nitrogen). Needs the highest
 *   particle energies, so it only appears near the lower border of a curtain,
 *   and mainly during strong geomagnetic activity.
 * - **557.7 nm, green** - OI (atomic oxygen) forbidden transition. The dominant
 *   and most common colour. Peaks around 100-120 km; the sharp loss of atomic
 *   oxygen below ~100 km gives curtains their abruptly cut-off lower edge.
 * - **630.0 nm, red** - OI, also forbidden. Only survives high up: below about
 *   150 km collisions quench it almost completely, so the red is a faint crown
 *   rather than a second bright band.
 *
 * Pink and magenta are not a separate emission line - they are the additive
 * mixture of red (630 nm) and blue (428 nm), seen during high solar activity.
 *
 * Altitudes are normalised so that 0 is [LOWER_KM] (the hard lower border) and
 * 1 is [UPPER_KM] (the top of the visible red). The same weights are implemented
 * in `AuroraShader`'s GLSL; this Kotlin copy is the reference the JVM tests
 * verify and the place the numbers are documented.
 */
object AuroraEmission {

    /** No aurora is observed below this; curtains start abruptly here. */
    const val LOWER_KM = 80f

    /** Top of the faint 630 nm red; above this the emission is negligible. */
    const val UPPER_KM = 260f

    /** 557.7 nm green peaks near 110 km. */
    const val GREEN_PEAK_KM = 110f

    /** 428.0 nm blue sits at the very bottom edge. */
    const val BLUE_PEAK_KM = 85f

    /** 630.0 nm red only becomes visible above roughly 150 km. */
    const val RED_ONSET_KM = 150f

    /** Relative strength of the three emission bands at one altitude. */
    data class Weights(
        val blue: Float,
        val green: Float,
        val red: Float,
    ) {
        /** Largest band at this altitude. */
        fun dominant(): Band = when {
            blue >= green && blue >= red -> Band.BLUE
            green >= red -> Band.GREEN
            else -> Band.RED
        }
    }

    enum class Band { BLUE, GREEN, RED }

    /**
     * Maps an absolute altitude in km onto the 0..1 curtain coordinate.
     * Non-finite input falls back to the green peak so a corrupt frame renders a
     * plausible curtain instead of an undefined colour.
     */
    fun normalize(km: Float): Float {
        if (!km.isFinite()) return normalize(GREEN_PEAK_KM)
        return ((km - LOWER_KM) / (UPPER_KM - LOWER_KM)).coerceIn(0f, 1f)
    }

    /**
     * Emission weights in the 0..1 curtain coordinate.
     *
     * The blue and green profiles are Gaussians around their measured peaks.
     * Red is a smooth ramp that stays at zero below [normalize] of
     * [RED_ONSET_KM], modelling collisional quenching, and is scaled down
     * because the 630 nm line is intrinsically faint.
     */
    fun weights(heightNorm: Float): Weights {
        val h = if (heightNorm.isFinite()) heightNorm.coerceIn(0f, 1f) else normalize(GREEN_PEAK_KM)
        val green = gaussian(h, normalize(GREEN_PEAK_KM), 0.26f)
        val blue = gaussian(h, normalize(BLUE_PEAK_KM), 0.09f)
        // 630 nm is intrinsically faint and only survives above the quenching
        // altitude, so its ceiling is well below the green peak. Letting red
        // approach parity with green paints the whole upper curtain solid red.
        val red = SkyScoreFormula.smoothstep(normalize(RED_ONSET_KM), 0.95f, h) * 0.45f
        return Weights(blue = blue, green = green, red = red)
    }

    /**
     * Linearly combines three band colours by their weight at [heightNorm].
     * Colours are packed ARGB ints, matching how styles are declared.
     */
    fun mix(heightNorm: Float, blueColor: Int, greenColor: Int, redColor: Int): Int {
        val w = weights(heightNorm)
        val total = w.blue + w.green + w.red
        if (!total.isFinite() || total <= 0f) return greenColor
        return blend(blueColor, greenColor, redColor, w, total)
    }

    private fun blend(blueColor: Int, greenColor: Int, redColor: Int, w: Weights, total: Float): Int {
        fun channel(shift: Int): Int {
            val b = ((blueColor shr shift) and 0xFF) * (w.blue / total)
            val g = ((greenColor shr shift) and 0xFF) * (w.green / total)
            val r = ((redColor shr shift) and 0xFF) * (w.red / total)
            return (b + g + r).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or
            (channel(16) shl 16) or
            (channel(8) shl 8) or
            channel(0)
    }

    private fun gaussian(x: Float, centre: Float, width: Float): Float {
        if (width <= 0f) return if (x == centre) 1f else 0f
        val d = (x - centre) / width
        return exp(-d * d)
    }
}
