package com.auroraar.skyscore.features

/**
 * Point-source counting for the classical star cue. Pure Kotlin so the
 * plateau/tie behaviour is testable without an Android ImageProxy.
 *
 * A star must be strictly brighter than all eight neighbours (ties are
 * rejected, so a flat dusk gradient scores zero) and must stand clear of its
 * own local background by a margin that grows with sensor noise.
 */
object StarCounter {

    const val MAX_STARS = 400

    fun count(
        luma: FloatArray,
        mask: FloatArray,
        gridW: Int,
        gridH: Int,
        noise: Float,
    ): Int {
        if (gridW < 3 || gridH < 3) return 0
        val safeNoise = if (noise.isFinite() && noise > 0f) noise else 0f
        val margin = maxOf(0.08f, safeNoise * 8f)
        var count = 0
        for (gy in 1 until gridH - 1) {
            for (gx in 1 until gridW - 1) {
                val i = gy * gridW + gx
                if (mask[i] <= 0.5f) continue
                val v = luma[i]
                if (!v.isFinite() || v < 0.12f) continue

                var neighbourSum = 0f
                var neighbourCount = 0
                var strictMax = true
                loop@ for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val neighbour = luma[i + dy * gridW + dx]
                        if (!neighbour.isFinite()) {
                            strictMax = false
                            break@loop
                        }
                        neighbourSum += neighbour
                        neighbourCount++
                        if (neighbour >= v) {
                            strictMax = false
                            break@loop
                        }
                    }
                }
                if (!strictMax || neighbourCount == 0) continue
                if (v - neighbourSum / neighbourCount < margin) continue
                count++
            }
        }
        return count.coerceAtMost(MAX_STARS)
    }
}
