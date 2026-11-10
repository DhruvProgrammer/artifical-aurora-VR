package com.auroraar.skyscore.features

/**
 * Colour plausibility for the classical sky mask.
 *
 * Blue sky is easy, but a sunset sky is warm (red-dominant) and must not be
 * rejected: that is exactly when the aurora preview is supposed to appear.
 * Vegetation is the main false positive above the horizon, so strongly
 * green-dominant pixels are rejected instead of all non-blue pixels.
 *
 * Pure Kotlin so the thresholds can be tested on the JVM.
 */
object SkyMaskColor {

    fun plausibility(r: Float, g: Float, b: Float): Float {
        val blueDominance = (b - r) / (b + r + 0.03f)
        val greenish = g - maxOf(r, b)
        return when {
            blueDominance >= 0.05f -> 1f
            greenish > 0.045f -> 0.12f
            else -> 0.62f + 0.38f * smoothstep(-0.35f, 0.05f, blueDominance)
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
