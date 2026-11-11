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
        if (!r.isFinite() || !g.isFinite() || !b.isFinite()) return 0f
        val brightest = maxOf(r, g, b)
        val darkest = minOf(r, g, b)
        val chroma = brightest - darkest

        // A covered lens / dead frame is near-black with no colour structure.
        // It used to score ~0.98 because "not blue" fell through to the default.
        if (brightest < 0.02f) return 0f
        // Auto-exposure can lift a covered lens to a flat neutral grey (~0.07),
        // which also used to score ~0.95. Real night sky carries at least a
        // slight blue or light-pollution tint, so a dark frame with almost no
        // chroma is treated as not-sky. This is deliberately fail-closed.
        if (brightest < 0.12f && chroma < 0.018f) return 0.05f
        // Dark red fabric/hand over the lens: strongly red, almost no green/blue.
        if (brightest < 0.35f && r > 0.04f && g < 0.05f && b < 0.05f && r > 2f * maxOf(g, b)) {
            return 0.05f
        }

        val blueDominance = (b - r) / (b + r + 0.03f)
        val other = maxOf(r, b)
        val greenish = g - other
        return when {
            blueDominance >= 0.05f -> 1f
            greenish > 0.045f -> 0.12f
            // Dim foliage never reaches the absolute threshold above, but it is
            // still strongly green relative to both red and blue.
            g > 1.35f * other && greenish > 0.012f -> 0.12f
            else -> 0.62f + 0.38f * smoothstep(-0.35f, 0.05f, blueDominance)
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
