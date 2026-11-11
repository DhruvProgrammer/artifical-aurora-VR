package com.auroraar.skyscore

import com.auroraar.skyscore.estimators.BrightnessEstimator
import com.auroraar.skyscore.estimators.CloudEstimator
import com.auroraar.skyscore.estimators.PhaseClassifier
import com.auroraar.skyscore.features.AstronomyPrior
import com.auroraar.skyscore.features.StarCounter
import com.auroraar.skyscore.features.YuvColor
import com.auroraar.skyscore.model.AstroPrior
import com.auroraar.skyscore.model.EngineInput
import com.auroraar.skyscore.model.ExposureInfo
import com.auroraar.skyscore.model.FrameFeatures
import com.auroraar.skyscore.model.Gate
import com.auroraar.skyscore.model.Mode
import com.auroraar.skyscore.model.Phase
import com.auroraar.skyscore.model.SubScores
import com.auroraar.skyscore.model.UserSettings
import com.auroraar.skyscore.policy.SkyScoreFormula
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YuvColorTest {

    @Test
    fun `studio-range black expands to zero`() {
        val rgb = YuvColor.fromLimitedRange(y = 16, u = 128, v = 128)
        assertEquals(0f, rgb.luma, 0.0001f)
        assertEquals(0f, rgb.r, 0.0001f)
        assertEquals(0f, rgb.g, 0.0001f)
        assertEquals(0f, rgb.b, 0.0001f)
    }

    @Test
    fun `studio-range white expands to one`() {
        val rgb = YuvColor.fromLimitedRange(y = 235, u = 128, v = 128)
        assertEquals(1f, rgb.luma, 0.0001f)
        assertTrue(rgb.r > 0.99f && rgb.g > 0.99f && rgb.b > 0.99f)
    }

    @Test
    fun `full-range black and white are clamped safely`() {
        assertEquals(0f, YuvColor.fromLimitedRange(0, 128, 128).luma, 0.0001f)
        assertEquals(1f, YuvColor.fromLimitedRange(255, 128, 128).luma, 0.0001f)
    }

    @Test
    fun `mid grey maps to the expected normalized luminance`() {
        val rgb = YuvColor.fromLimitedRange(y = 126, u = 128, v = 128)
        assertEquals((126f - 16f) / 219f, rgb.luma, 0.0001f)
    }
}

class StarCounterTest {

    private fun flat(w: Int, h: Int, value: Float) = FloatArray(w * h) { value }

    private fun mask(w: Int, h: Int) = FloatArray(w * h) { 1f }

    @Test
    fun `a flat dusk plateau produces no stars`() {
        val luma = flat(32, 32, 0.35f)
        assertEquals(0, StarCounter.count(luma, mask(32, 32), 32, 32, 0f))
    }

    @Test
    fun `a tied plateau is not a point source`() {
        val w = 16
        val h = 16
        val luma = flat(w, h, 0.2f)
        for (y in 6..8) for (x in 6..8) luma[y * w + x] = 0.8f
        assertEquals(0, StarCounter.count(luma, mask(w, h), w, h, 0f))
    }

    @Test
    fun `an isolated bright pixel counts as one star`() {
        val w = 16
        val h = 16
        val luma = flat(w, h, 0.2f)
        luma[8 * w + 8] = 0.9f
        assertEquals(1, StarCounter.count(luma, mask(w, h), w, h, 0f))
    }

    @Test
    fun `masked-out pixels never count`() {
        val w = 16
        val h = 16
        val luma = flat(w, h, 0.2f)
        luma[8 * w + 8] = 0.9f
        val blocked = FloatArray(w * h) { 0f }
        assertEquals(0, StarCounter.count(luma, blocked, w, h, 0f))
    }

    @Test
    fun `non-finite neighbours fail closed`() {
        val w = 16
        val h = 16
        val luma = flat(w, h, 0.2f)
        luma[7 * w + 8] = Float.NaN
        luma[8 * w + 8] = 0.9f
        assertEquals(0, StarCounter.count(luma, mask(w, h), w, h, 0f))
    }
}

class CameraBlockedTest {

    @Test
    fun `a black featureless frame is a blocked camera`() {
        assertTrue(
            SkyScoreFormula.cameraBlocked(
                meanLum = 0f,
                textureEnergy = 0f,
                noiseSigma = 0f,
                starCount = 0,
                lux = 0.2f,
            ),
        )
    }

    @Test
    fun `a live dark night sky is not a blocked camera`() {
        assertFalse(
            SkyScoreFormula.cameraBlocked(
                meanLum = 0.10f,
                textureEnergy = 0.02f,
                noiseSigma = 0.05f,
                starCount = 40,
                lux = 0.3f,
            ),
        )
    }

    @Test
    fun `a dark overcast live sky is not a blocked camera`() {
        assertFalse(
            SkyScoreFormula.cameraBlocked(
                meanLum = 0.07f,
                textureEnergy = 0.02f,
                // Real sensors are never temporally dead; this is the signal
                // that separates a live overcast night from a covered lens.
                noiseSigma = 0.008f,
                starCount = 0,
                lux = 0.4f,
            ),
        )
    }

    @Test
    fun `bright ambient light with a dark frame means a covered lens`() {
        assertTrue(
            SkyScoreFormula.cameraBlocked(
                meanLum = 0.05f,
                textureEnergy = 0.30f,
                noiseSigma = 0.04f,
                starCount = 0,
                lux = 20_000f,
            ),
        )
    }

    @Test
    fun `non-finite features fail closed`() {
        assertTrue(
            SkyScoreFormula.cameraBlocked(Float.NaN, 0f, 0f, 0, null),
        )
        assertTrue(
            SkyScoreFormula.cameraBlocked(0.2f, Float.POSITIVE_INFINITY, 0f, 0, null),
        )
    }
}

class NonFiniteInputTest {

    @Test
    fun `a non-finite sub-score cannot become a number`() {
        assertEquals(0, SkyScoreFormula.score(SubScores(Float.NaN, 1f, 1f, 1f)))
        assertEquals(0, SkyScoreFormula.score(SubScores(1f, Float.POSITIVE_INFINITY, 1f, 1f)))
    }

    @Test
    fun `non-finite darkness and visibility fail closed`() {
        assertEquals(0f, SkyScoreFormula.darkness(listOf(Float.NaN, 0f, 0f, 1f)), 0.0001f)
        assertEquals(0f, SkyScoreFormula.visibility(Float.NaN, 1f), 0.0001f)
        assertEquals(
            Gate.NO_SKY,
            SkyScoreFormula.gate(Float.NaN, Float.NaN, cameraBlocked = false, creative = true),
        )
    }

    @Test
    fun `invalid coordinates cannot fake a location`() {
        val epoch = Instant.parse("2026-06-21T18:30:00Z").toEpochMilli()
        val nanLat = AstronomyPrior.forTime(epoch, Double.NaN, 75.0)
        val outOfRange = AstronomyPrior.forTime(epoch, 200.0, 75.0)
        assertFalse(nanLat.hasLocation)
        assertFalse(outOfRange.hasLocation)
        assertTrue(nanLat.prob.all { it.isFinite() })
    }

    @Test
    fun `a NaN frame cannot open the gate or crash the engine`() {
        val engine = SkyScoreEngine()
        val conditions = engine.onInput(
            EngineInput(
                features = FrameFeatures(
                    meanLum = Float.NaN,
                    skyMeanLum = Float.NaN,
                    fSky = Float.NaN,
                    maskConfidence = Float.NaN,
                    textureEnergy = Float.NaN,
                    noiseSigma = Float.NaN,
                ),
                exposure = null,
                lux = Float.NaN,
                astro = dayPrior,
                cameraElevationDeg = Float.NaN,
                nowMs = 1_000L,
            ),
        )
        assertEquals(Gate.CAMERA_BLOCKED, conditions.gate)
        assertEquals(0, conditions.skyScore)
        assertTrue(conditions.subScores.visibility.isFinite())
        assertTrue(!engine.renderParams.value.enabled)
    }

    @Test
    fun `infinite exposure metadata falls back to appearance`() {
        val f = FrameFeatures(skyMeanLinear = 0.2f, skyMeanLum = 0.4f)

        val infiniteTime = BrightnessEstimator.estimate(
            f,
            ExposureInfo(Float.POSITIVE_INFINITY, 100f, 2f, true, 0f, 0L),
        )
        assertFalse(infiniteTime.photometric)
        assertTrue(infiniteTime.logLum.isFinite())

        val infiniteIso = BrightnessEstimator.estimate(
            f,
            ExposureInfo(0.01f, Float.POSITIVE_INFINITY, 2f, true, 0f, 0L),
        )
        assertFalse(infiniteIso.photometric)

        val nanCompensation = BrightnessEstimator.estimate(
            f,
            ExposureInfo(0.01f, 100f, 2f, true, Float.NaN, 0L),
        )
        assertFalse(nanCompensation.photometric)
    }

    @Test
    fun `bright blue daylight without exposure metadata is not dusk`() {
        // Regression: blue was checked before brightness, so a metadata-less
        // clear midday sky fell into the blue-dusk branch and estimated 0.8.
        val daylight = FrameFeatures(
            skyMeanLum = 0.65f,
            skyMeanLinear = 0.38f,
            blueDominance = 0.30f,
            warmth = 1.0f,
            fSky = 0.60f,
            maskConfidence = 0.95f,
        )
        val estimate = BrightnessEstimator.estimate(daylight, null)
        assertFalse(estimate.photometric)
        assertTrue("logLum=${estimate.logLum}", estimate.logLum >= 2.0f)
    }

    @Test
    fun `non-finite cloud inputs stay finite`() {
        val f = FrameFeatures(
            fSky = 0.5f,
            maskConfidence = Float.NaN,
            blueDominance = Float.NaN,
            skyMeanLum = Float.NaN,
            textureEnergy = Float.NaN,
            saturation = Float.NaN,
            warmth = Float.NaN,
        )
        val day = CloudEstimator.daytime(f)
        val night = CloudEstimator.night(f)
        val blend = CloudEstimator.blend(day, night, listOf(Float.NaN, 0f, 0f, 0f))

        assertTrue(day.cover.isFinite() && day.confidence.isFinite())
        assertTrue(night.cover.isFinite() && night.confidence.isFinite())
        assertTrue(blend.cover.isFinite() && blend.confidence.isFinite())
    }

    private val dayPrior = AstroPrior(
        sunElevationDeg = 40f,
        prob = listOf(0.9f, 0.05f, 0.03f, 0.02f),
        confidence = 0.9f,
        hasLocation = true,
    )
}

class CreativeGateTest {

    private val nightPrior = AstroPrior(
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
        clippedFraction = 0.02f,
        textureEnergy = 0.02f,
        fSky = 0.60f,
        maskConfidence = 0.95f,
    )

    @Test
    fun `creative may show daylight but never with no sky`() {
        val engine = SkyScoreEngine(UserSettings(mode = Mode.CREATIVE))

        engine.onInput(
            EngineInput(
                features = daylightFeatures(),
                exposure = ExposureInfo(1f / 500f, 100f, 2.2f, true, 0f, 0L),
                lux = 20_000f,
                astro = nightPrior,
                cameraElevationDeg = 40f,
                nowMs = 1_000L,
            ),
        )
        assertTrue(engine.renderParams.value.enabled)

        engine.onInput(
            EngineInput(
                features = daylightFeatures().copy(fSky = 0.01f, maskConfidence = 0.1f),
                exposure = ExposureInfo(1f / 500f, 100f, 2.2f, true, 0f, 0L),
                lux = 20_000f,
                astro = nightPrior,
                cameraElevationDeg = 40f,
                nowMs = 1_200L,
            ),
        )
        assertFalse(engine.renderParams.value.enabled)
    }

    @Test
    fun `NaN slider values are sanitized before they reach the renderer`() {
        val engine = SkyScoreEngine(
            UserSettings(
                mode = Mode.CREATIVE,
                intensity = Float.NaN,
                saturation = Float.POSITIVE_INFINITY,
                maxIntensity = Float.NaN,
            ),
        )
        engine.onInput(
            EngineInput(
                features = FrameFeatures(
                    meanLum = 0.08f,
                    skyMeanLum = 0.05f,
                    blueDominance = 0.05f,
                    warmth = 1f,
                    saturation = 0.2f,
                    noiseSigma = 0.03f,
                    starCount = 20,
                    fSky = 0.6f,
                    maskConfidence = 0.9f,
                    textureEnergy = 0.02f,
                ),
                exposure = null,
                lux = 0.4f,
                astro = nightPrior,
                cameraElevationDeg = 30f,
                nowMs = 1_000L,
            ),
        )
        val params = engine.renderParams.value
        assertTrue(params.intensity.isFinite())
        assertTrue(params.saturation.isFinite())
        assertTrue(params.intensity <= 1f)
    }
}

class ClassifierRobustnessTest {

    private val neutralAstro = AstroPrior(
        sunElevationDeg = 0f,
        prob = listOf(0.25f, 0.25f, 0.25f, 0.25f),
        confidence = 0.3f,
        hasLocation = false,
    )

    @Test
    fun `a flat dusk frame is not classified as night`() {
        val flat = FrameFeatures(
            meanLum = 0.30f,
            skyMeanLum = 0.30f,
            blueDominance = 0.04f,
            warmth = 1.0f,
            saturation = 0.18f,
            noiseSigma = 0.0f,
            starCount = 0,
            textureEnergy = 0.01f,
            fSky = 0.6f,
            maskConfidence = 0.9f,
        )
        val prob = PhaseClassifier.fuse(
            logLum = 1.0f,
            lumValid = true,
            lumConfidence = 0.3f,
            features = flat,
            exposure = null,
            lux = null,
            astro = neutralAstro,
        )
        assertTrue("night=${prob[Phase.NIGHT.ordinal]}", prob[Phase.NIGHT.ordinal] < 0.5f)
        assertEquals(1f, prob.sum(), 0.001f)
    }

    @Test
    fun `non-finite log luminance still yields a normalized distribution`() {
        val prob = PhaseClassifier.fuse(
            logLum = Float.NaN,
            lumValid = true,
            lumConfidence = Float.NaN,
            features = FrameFeatures(),
            exposure = null,
            lux = Float.NaN,
            astro = neutralAstro,
        )
        assertEquals(1f, prob.sum(), 0.001f)
        assertTrue(prob.all { it.isFinite() })
    }
}
