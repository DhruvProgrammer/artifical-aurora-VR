package com.auroraar.skyscore.features

/**
 * YUV_420_888 (limited range) to normalized RGB. Pure Kotlin so the black-level
 * and clipping behaviour can be unit tested without a camera.
 */
object YuvColor {

    data class Rgb(val r: Float, val g: Float, val b: Float, val luma: Float)

    fun fromLimitedRange(y: Int, u: Int, v: Int): Rgb {
        // Studio range is 16..235; expanding it makes black 0 and white 1.
        val luma = ((y and 0xFF) - 16f).div(219f).coerceIn(0f, 1f)
        val uf = ((u and 0xFF) - 128f) / 224f
        val vf = ((v and 0xFF) - 128f) / 224f
        val r = (luma + 1.402f * vf).coerceIn(0f, 1f)
        val g = (luma - 0.344136f * uf - 0.714136f * vf).coerceIn(0f, 1f)
        val b = (luma + 1.772f * uf).coerceIn(0f, 1f)
        return Rgb(r, g, b, luma)
    }
}
