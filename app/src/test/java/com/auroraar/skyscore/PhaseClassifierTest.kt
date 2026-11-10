package com.auroraar.skyscore

import com.auroraar.skyscore.estimators.PhaseClassifier
import com.auroraar.skyscore.model.AstroPrior
import com.auroraar.skyscore.model.FrameFeatures
import com.auroraar.skyscore.model.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseClassifierTest {

    private val dayAstro = AstroPrior(45f, listOf(0.97f, 0.02f, 0.005f, 0.005f), 0.9f, true)
    private val nightAstro = AstroPrior(-35f, listOf(0.005f, 0.005f, 0.02f, 0.97f), 0.9f, true)

    private fun features(
        skyMeanLum: Float = 0.5f,
        blueDominance: Float = 0.25f,
        warmth: Float = 1f,
        stars: Int = 0,
        noise: Float = 0f,
        clipped: Float = 0f,
        fSky: Float = 0.5f,
        maskConfidence: Float = 0.9f,
        texture: Float = 0.02f,
    ) = FrameFeatures(
        skyMeanLum = skyMeanLum,
        skyMeanLinear = 0.2f,
        blueDominance = blueDominance,
        warmth = warmth,
        saturation = 0.4f,
        noiseSigma = noise,
        starCount = stars,
        clippedFraction = clipped,
        textureEnergy = texture,
        fSky = fSky,
        maskConfidence = maskConfidence,
    )

    @Test
    fun `bright midday fuses to daylight`() {
        val p = PhaseClassifier.fuse(
            logLum = 3.4f,
            lumValid = true,
            lumConfidence = 0.85f,
            features = features(skyMeanLum = 0.7f, blueDominance = 0.3f, clipped = 0.01f),
            exposure = null,
            lux = 20_000f,
            astro = dayAstro,
        )
        assertTrue(p[Phase.DAYLIGHT.ordinal] > 0.6f)
        assertEquals(1f, p.sum(), 0.001f)
    }

    @Test
    fun `starry dark sky fuses to night`() {
        val p = PhaseClassifier.fuse(
            logLum = -1.9f,
            lumValid = true,
            lumConfidence = 0.85f,
            features = features(skyMeanLum = 0.02f, stars = 40, noise = 0.05f, blueDominance = -0.02f),
            exposure = null,
            lux = 0.2f,
            astro = nightAstro,
        )
        assertTrue(p[Phase.NIGHT.ordinal] > 0.6f)
        assertEquals(1f, p.sum(), 0.001f)
    }

    @Test
    fun `warm horizon at mid brightness fuses to sunset`() {
        val p = PhaseClassifier.fuse(
            logLum = 2.2f,
            lumValid = true,
            lumConfidence = 0.85f,
            features = features(skyMeanLum = 0.35f, warmth = 1.50f, blueDominance = -0.1f, clipped = 0.002f),
            exposure = null,
            lux = 300f,
            astro = AstroPrior(3f, listOf(0.1f, 0.7f, 0.15f, 0.05f), 0.9f, true),
        )
        assertTrue(p[Phase.SUNSET.ordinal] > 0.7f)
    }

    @Test
    fun `deep blue twilight fuses to twilight`() {
        val p = PhaseClassifier.fuse(
            logLum = 0.4f,
            lumValid = true,
            lumConfidence = 0.7f,
            features = features(skyMeanLum = 0.07f, blueDominance = 0.22f, warmth = 1.0f),
            exposure = null,
            lux = 20f,
            astro = AstroPrior(-5f, listOf(0.01f, 0.15f, 0.7f, 0.14f), 0.9f, true),
        )
        assertTrue(p[Phase.TWILIGHT.ordinal] > 0.45f)
    }

    @Test
    fun `override forces night when very dark`() {
        val p = PhaseClassifier.fuse(
            logLum = -1.5f,
            lumValid = true,
            lumConfidence = 0.9f,
            features = features(skyMeanLum = 0.02f, warmth = 1.0f),
            exposure = null,
            lux = 0.1f,
            astro = dayAstro,
        )
        assertTrue(p[Phase.DAYLIGHT.ordinal] < 0.2f)
        assertTrue(p[Phase.NIGHT.ordinal] > 0.3f)
    }
}
