package com.auroraar.skyscore

import com.auroraar.skyscore.features.AstronomyPrior
import com.auroraar.skyscore.model.AstroPrior
import com.auroraar.skyscore.model.ExposureInfo
import com.auroraar.skyscore.model.FrameFeatures
import com.auroraar.skyscore.model.Gate
import com.auroraar.skyscore.model.Mode
import com.auroraar.skyscore.model.Phase
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AstronomyPriorTest {

    private val tz = TimeZone.getTimeZone("Asia/Kolkata")
    private val lat = 26.9124
    private val lon = 75.7873

    @Test
    fun `noon in jaipur is bright and midnight is dark`() {
        val noon = AstronomyPrior.forTime(Instant.parse("2026-06-21T06:30:00Z").toEpochMilli(), lat, lon, tz)
        val midnight = AstronomyPrior.forTime(Instant.parse("2026-06-21T18:30:00Z").toEpochMilli(), lat, lon, tz)

        assertTrue("noon elevation=${noon.sunElevationDeg}", noon.sunElevationDeg > 70f)
        assertTrue("midnight elevation=${midnight.sunElevationDeg}", midnight.sunElevationDeg < -20f)
        assertEquals(Phase.DAYLIGHT, Phase.fromIndex(noon.prob.indices.maxByOrNull { noon.prob[it] } ?: 0))
        assertEquals(Phase.NIGHT, Phase.fromIndex(midnight.prob.indices.maxByOrNull { midnight.prob[it] } ?: 0))
        assertTrue(noon.hasLocation)
        assertEquals(1f, noon.prob.sum(), 0.001f)
    }

    @Test
    fun `missing location lowers confidence`() {
        val epoch = Instant.parse("2026-06-21T18:30:00Z").toEpochMilli()
        val withLocation = AstronomyPrior.forTime(epoch, lat, lon, tz)
        val withoutLocation = AstronomyPrior.forTime(epoch, null, null, tz)
        assertTrue(withoutLocation.confidence < withLocation.confidence)
    }
}

class SkyScoreEngineTest {

    private val dayAstro = AstroPrior(
        sunElevationDeg = 55f,
        prob = listOf(0.99f, 0.005f, 0.003f, 0.002f),
        confidence = 0.9f,
        hasLocation = true,
    )
    private val nightAstro = AstroPrior(
        sunElevationDeg = -35f,
        prob = listOf(0.005f, 0.005f, 0.02f, 0.97f),
        confidence = 0.9f,
        hasLocation = true,
    )

    private fun daylightFeatures() = FrameFeatures(
        meanLum = 0.55f,
        skyMeanLum = 0.60f,
        skyMeanLinear = 0.35f,
        blueDominance = 0.30f,
        warmth = 1.0f,
        saturation = 0.50f,
        noiseSigma = 0f,
        starCount = 0,
        clippedFraction = 0.02f,
        textureEnergy = 0.02f,
        fSky = 0.60f,
        maskConfidence = 0.95f,
        foregroundLum = 0.25f,
    )

    private fun nightFeatures() = FrameFeatures(
        meanLum = 0.05f,
        skyMeanLum = 0.02f,
        skyMeanLinear = 0.02f,
        blueDominance = -0.02f,
        warmth = 1.0f,
        saturation = 0.2f,
        noiseSigma = 0.05f,
        starCount = 40,
        clippedFraction = 0f,
        textureEnergy = 0.01f,
        fSky = 0.60f,
        maskConfidence = 0.90f,
        foregroundLum = 0.02f,
    )

    @Test
    fun `daylight is blocked and the renderer is disabled`() {
        val engine = SkyScoreEngine()
        val conditions = engine.onInput(
            com.auroraar.skyscore.model.EngineInput(
                features = daylightFeatures(),
                exposure = ExposureInfo(1f / 1000f, 100f, 2.2f, true, 0f, 0L),
                lux = 20_000f,
                astro = dayAstro,
                cameraElevationDeg = 40f,
                nowMs = 1_000L,
            ),
        )
        assertEquals(Gate.BLOCKED_DAYLIGHT, conditions.gate)
        assertEquals(0, conditions.skyScore)
        assertTrue(!engine.renderParams.value.enabled)
        assertEquals(Phase.DAYLIGHT, conditions.phase)
    }

    @Test
    fun `dark clear night opens the gate and converges to a high score`() {
        val engine = SkyScoreEngine()
        var now = 1_000L
        var conditions = engine.onInput(
            com.auroraar.skyscore.model.EngineInput(
                features = nightFeatures(),
                exposure = ExposureInfo(0.1f, 3200f, 1.8f, true, 0f, 0L),
                lux = 0.5f,
                astro = nightAstro,
                cameraElevationDeg = 30f,
                nowMs = now,
            ),
        )
        repeat(60) {
            now += 200L
            conditions = engine.onInput(
                com.auroraar.skyscore.model.EngineInput(
                    features = nightFeatures(),
                    exposure = ExposureInfo(0.1f, 3200f, 1.8f, true, 0f, 0L),
                    lux = 0.5f,
                    astro = nightAstro,
                    cameraElevationDeg = 30f,
                    nowMs = now,
                ),
            )
        }
        assertEquals(Gate.OPEN, conditions.gate)
        assertEquals(Phase.NIGHT, conditions.phase)
        assertTrue("score=${conditions.skyScore}", conditions.skyScore >= 75)
        assertTrue(engine.renderParams.value.enabled)
    }

    @Test
    fun `no sky gate fires when there is no sky in view`() {
        val engine = SkyScoreEngine()
        val conditions = engine.onInput(
            com.auroraar.skyscore.model.EngineInput(
                features = nightFeatures().copy(fSky = 0.01f, maskConfidence = 0.2f),
                exposure = null,
                lux = 20f,
                astro = nightAstro,
                cameraElevationDeg = -40f,
                nowMs = 1_000L,
            ),
        )
        assertEquals(Gate.NO_SKY, conditions.gate)
        assertEquals(0, conditions.skyScore)
    }

    @Test
    fun `off mode disables the renderer`() {
        val engine = SkyScoreEngine()
        engine.setSettings(com.auroraar.skyscore.model.UserSettings(mode = Mode.OFF))
        engine.onInput(
            com.auroraar.skyscore.model.EngineInput(
                features = nightFeatures(),
                exposure = null,
                lux = 0.5f,
                astro = nightAstro,
                cameraElevationDeg = 30f,
                nowMs = 1_000L,
            ),
        )
        assertTrue(!engine.renderParams.value.enabled)
    }
}
