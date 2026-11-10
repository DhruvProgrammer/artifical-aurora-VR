package com.auroraar.camera

import androidx.camera.core.ImageProxy
import com.auroraar.skyscore.model.FrameFeatures
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

/**
 * Cheap per-frame feature extraction on a 160x90 grid read directly from the
 * YUV planes (addendum §2/§14).
 *
 * v3's semantic sky mask is not part of this build, so the mask is a classical
 * approximation: a tilt-aware vertical prior multiplied by a blue/grey colour
 * plausibility, eroded by one grid cell (~6-10 px at 640x360). Buildings below
 * the horizon are excluded by the prior; anything above it that is not sky-like
 * is excluded by the colour term. Honest limits are documented in the README.
 */
class SkyFeatureExtractor(
    private val gridW: Int = 160,
    private val gridH: Int = 90,
) {
    private val luma = FloatArray(gridW * gridH)
    private val red = FloatArray(gridW * gridH)
    private val green = FloatArray(gridW * gridH)
    private val blue = FloatArray(gridW * gridH)
    private val alpha = FloatArray(gridW * gridH)
    private val eroded = FloatArray(gridW * gridH)
    private val prevLuma = FloatArray(gridW * gridH)
    private var hasPrev = false

    fun analyze(image: ImageProxy, cameraElevationDeg: Float): FrameFeatures {
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0 || image.planes.size < 3) return blank()

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRow = yPlane.rowStride
        val yPix = yPlane.pixelStride
        val uRow = uPlane.rowStride
        val uPix = uPlane.pixelStride
        val vRow = vPlane.rowStride
        val vPix = vPlane.pixelStride
        val rotation = image.imageInfo.rotationDegrees

        var sumLuma = 0f
        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                val packed = mapCoords(gx, gy, rotation, width, height)
                val bx = packed ushr 16
                val by = packed and 0xFFFF

                val yIndex = by * yRow + bx * yPix
                val yy = (yBuf.get(yIndex).toInt() and 0xFF) / 255f

                val ux = bx / 2
                val uy = by / 2
                val uu = (uBuf.get(uy * uRow + ux * uPix).toInt() and 0xFF).toFloat()
                val vv = (vBuf.get(uy * vRow + ux * vPix).toInt() and 0xFF).toFloat()

                val yf = (yy * 255f - 16f) / 219f
                val uf = (uu - 128f) / 224f
                val vf = (vv - 128f) / 224f
                val r = (yf + 1.402f * vf).coerceIn(0f, 1f)
                val g = (yf - 0.344136f * uf - 0.714136f * vf).coerceIn(0f, 1f)
                val b = (yf + 1.772f * uf).coerceIn(0f, 1f)

                val i = gy * gridW + gx
                luma[i] = yy
                red[i] = r
                green[i] = g
                blue[i] = b
                sumLuma += yy
            }
        }

        buildMask(cameraElevationDeg)
        erode()

        var skyCount = 0
        var skyLumaSum = 0f
        var skyR = 0f
        var skyG = 0f
        var skyB = 0f
        var skyAlphaSum = 0f
        var clipped = 0
        var bgLumaSum = 0f
        var bgCount = 0
        var blobPixels = 0
        var textureSum = 0f
        var textureCount = 0

        for (gy in 1 until gridH - 1) {
            for (gx in 1 until gridW - 1) {
                val i = gy * gridW + gx
                if (eroded[i] > 0.5f) {
                    skyCount++
                    skyLumaSum += luma[i]
                    skyR += red[i]
                    skyG += green[i]
                    skyB += blue[i]
                    skyAlphaSum += eroded[i]
                    if (luma[i] > 0.985f) clipped++
                    val dx = abs(luma[i] - luma[i - 1])
                    textureSum += dx
                    textureCount++
                } else if (eroded[i] < 0.2f) {
                    bgCount++
                    bgLumaSum += luma[i]
                    if (luma[i] > 0.93f) blobPixels++
                }
            }
        }

        val total = (gridW * gridH).toFloat()
        val meanLum = sumLuma / total
        val fSky = skyCount / total
        val skyMean = if (skyCount > 0) skyLumaSum / skyCount else 0f
        val meanR = if (skyCount > 0) skyR / skyCount else 0f
        val meanG = if (skyCount > 0) skyG / skyCount else 0f
        val meanB = if (skyCount > 0) skyB / skyCount else 0f
        val maxC = max(meanR, max(meanG, meanB))
        val minC = minOf(meanR, minOf(meanG, meanB))
        val saturation = if (maxC > 0.01f) (maxC - minC) / maxC else 0f
        val blueDominance = ((meanB - meanR) / (meanB + meanR + 0.03f)).coerceIn(-1f, 1f)
        val warmth = if (meanB > 0.01f) (meanR / meanB).coerceIn(0f, 3f) else 1f

        // Temporal noise on flat sky patches: independent of AE normalisation.
        var noiseSum = 0f
        var noiseCount = 0
        if (hasPrev) {
            for (gy in 1 until gridH - 1) {
                for (gx in 1 until gridW - 1) {
                    val i = gy * gridW + gx
                    if (eroded[i] > 0.5f) {
                        val flat = abs(luma[i] - luma[i - 1]) < 0.015f
                        if (flat) {
                            noiseSum += abs(luma[i] - prevLuma[i])
                            noiseCount++
                        }
                    }
                }
            }
        }
        val noise = if (noiseCount > 100) (noiseSum / noiseCount) * 1.25f else 0f
        hasPrev = true
        luma.copyInto(prevLuma)

        val starCount = if (skyCount > 50 && skyMean < 0.45f) countStars(noise) else 0

        val bands = FloatArray(8)
        val bandCounts = IntArray(8)
        for (gy in 0 until gridH) {
            val band = (gy * 8 / gridH).coerceIn(0, 7)
            for (gx in 0 until gridW) {
                val i = gy * gridW + gx
                if (eroded[i] > 0.5f) {
                    bands[band] += luma[i]
                    bandCounts[band]++
                }
            }
        }
        for (k in 0 until 8) {
            if (bandCounts[k] > 0) bands[k] /= bandCounts[k]
        }

        val horizon = bands.getOrElse(6) { skyMean }
        val zenith = bands.getOrElse(1) { skyMean }

        return FrameFeatures(
            meanLum = meanLum,
            skyMeanLum = skyMean,
            skyMeanLinear = srgbToLinear(skyMean),
            blueDominance = blueDominance,
            warmth = warmth,
            saturation = saturation,
            noiseSigma = noise.coerceIn(0f, 1f),
            starCount = starCount,
            clippedFraction = if (skyCount > 0) clipped / skyCount.toFloat() else 0f,
            textureEnergy = if (textureCount > 0) (textureSum / textureCount / 0.08f).coerceIn(0f, 1f) else 0f,
            gradientStrength = (horizon - zenith).coerceIn(-1f, 1f),
            fSky = fSky,
            maskConfidence = if (skyCount > 0) skyAlphaSum / skyCount else 0f,
            foregroundLum = if (bgCount > 0) bgLumaSum / bgCount else 0f,
            brightBlobCount = (blobPixels / 12).coerceIn(0, 40),
            brightBlobFraction = (blobPixels / total).coerceIn(0f, 1f),
            skyLumBands = bands.toList(),
        )
    }

    fun resetTemporal() {
        hasPrev = false
    }

    private fun buildMask(cameraElevationDeg: Float) {
        val horizon = horizonRow(cameraElevationDeg)
        for (gy in 0 until gridH) {
            val v = (gy + 0.5f) / gridH
            val vertical = 1f - smoothstep(horizon - 0.06f, horizon + 0.24f, v)
            for (gx in 0 until gridW) {
                val i = gy * gridW + gx
                if (vertical <= 0.01f) {
                    alpha[i] = 0f
                    continue
                }
                val bd = (blue[i] - red[i]) / (blue[i] + red[i] + 0.03f)
                val colorPlausibility = 0.30f + 0.70f * smoothstep(-0.06f, 0.14f, bd)
                alpha[i] = (vertical * colorPlausibility * 1.25f).coerceIn(0f, 1f)
            }
        }
    }

    /** One 3x3 min filter: ~6-10 source pixels at the analysis resolution. */
    private fun erode() {
        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                var m = 1f
                for (dy in -1..1) {
                    val y = (gy + dy).coerceIn(0, gridH - 1)
                    for (dx in -1..1) {
                        val x = (gx + dx).coerceIn(0, gridW - 1)
                        m = minOf(m, alpha[y * gridW + x])
                    }
                }
                eroded[gy * gridW + gx] = m
            }
        }
    }

    private fun countStars(noise: Float): Int {
        val threshold = max(0.38f, luma.average().toFloat() + max(0.16f, noise * 6f))
        var count = 0
        for (gy in 1 until gridH - 1) {
            for (gx in 1 until gridW - 1) {
                val i = gy * gridW + gx
                if (eroded[i] <= 0.5f) continue
                val v = luma[i]
                if (v < threshold) continue
                var isMax = true
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        if (luma[i + dy * gridW + dx] > v) {
                            isMax = false
                            break
                        }
                    }
                    if (!isMax) break
                }
                if (isMax) count++
            }
        }
        return count.coerceAtMost(400)
    }

    private fun horizonRow(elevationDeg: Float): Float {
        val vfovHalf = Math.toRadians(27.0)
        val e = Math.toRadians(elevationDeg.toDouble())
        return (0.5 + tan(e) / (2.0 * tan(vfovHalf))).toFloat().coerceIn(-0.8f, 1.8f)
    }

    /** Maps an upright grid cell to buffer coordinates for the given rotation. */
    private fun mapCoords(gx: Int, gy: Int, rotation: Int, width: Int, height: Int): Int {
        val u = (gx + 0.5f) / gridW
        val v = (gy + 0.5f) / gridH
        val bx: Float
        val by: Float
        when (rotation) {
            0 -> {
                bx = u * width
                by = v * height
            }
            90 -> {
                bx = v * width
                by = (1f - u) * height
            }
            180 -> {
                bx = (1f - u) * width
                by = (1f - v) * height
            }
            else -> {
                bx = (1f - v) * width
                by = u * height
            }
        }
        val x = bx.toInt().coerceIn(0, width - 1)
        val y = by.toInt().coerceIn(0, height - 1)
        return (x shl 16) or (y and 0xFFFF)
    }

    private fun blank() = FrameFeatures()

    private fun srgbToLinear(c: Float): Float {
        val v = c.coerceIn(0f, 1f)
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge0 == edge1) return if (x < edge0) 0f else 1f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
