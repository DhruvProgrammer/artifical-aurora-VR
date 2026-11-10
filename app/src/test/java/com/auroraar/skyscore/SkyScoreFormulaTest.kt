package com.auroraar.skyscore

import com.auroraar.skyscore.model.Phase
import com.auroraar.skyscore.model.RatingBand
import com.auroraar.skyscore.model.SubScores
import com.auroraar.skyscore.policy.SkyScoreFormula
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyScoreFormulaTest {

    private fun prob(day: Float, sunset: Float, twilight: Float, night: Float): List<Float> =
        listOf(day, sunset, twilight, night)

    private fun score(darkness: Float, cloud: Float, visibility: Float = 0.98f, lighting: Float = 0.97f): Int {
        val p = prob(1f - darkness, 0f, 0f, darkness)
        return SkyScoreFormula.score(
            SubScores(
                brightness = darkness,
                cloud = SkyScoreFormula.cloudScore(cloud, 1f, p),
                visibility = visibility,
                lighting = lighting,
            ),
        )
    }

    @Test
    fun `bright daylight is zero`() {
        assertEquals(0, score(darkness = 0f, cloud = 0.05f))
    }

    @Test
    fun `very cloudy daytime is zero`() {
        assertEquals(0, score(darkness = 0f, cloud = 0.95f))
    }

    @Test
    fun `addendum 9_3 table rows stay inside their bands`() {
        assertTrue(score(0.20f, 0.05f) in 15..25)      // sunset
        assertTrue(score(0.375f, 0.05f) in 30..45)     // twilight
        assertTrue(score(1.00f, 0.90f) in 40..60)      // dark, cloudy
        assertTrue(score(1.00f, 0.45f) in 65..80)      // dark, partly clear
        assertTrue(score(1.00f, 0.05f) in 90..100)     // dark, clear
    }

    @Test
    fun `illustrative example gives 92`() {
        val sub = SubScores(brightness = 1.00f, cloud = 0.97f, visibility = 0.98f, lighting = 0.97f)
        assertEquals(92, SkyScoreFormula.score(sub))
    }

    @Test
    fun `score is monotonic non increasing in cloud cover`() {
        var previous = 101
        var cloud = 0f
        while (cloud <= 1f) {
            val current = score(darkness = 1f, cloud = cloud)
            assertTrue("cloud=$cloud", current <= previous)
            previous = current
            cloud += 0.05f
        }
    }

    @Test
    fun `score is monotonic non decreasing in darkness`() {
        var previous = -1
        var darkness = 0f
        while (darkness <= 1f) {
            val current = score(darkness = darkness, cloud = 0.3f)
            assertTrue("darkness=$darkness", current >= previous)
            previous = current
            darkness += 0.05f
        }
    }

    @Test
    fun `cloud term is never negative`() {
        val p = prob(0f, 0f, 0f, 1f)
        val value = SkyScoreFormula.cloudScore(cover = 1f, confidence = 1f, phaseProb = p)
        assertTrue(value >= 0f)
        assertEquals(0.5f, value, 0.0001f)
    }

    @Test
    fun `low confidence cloud estimate shrinks to neutral prior`() {
        val effective = SkyScoreFormula.cloudEffective(cover = 1f, confidence = 0f)
        assertEquals(0.25f, effective, 0.0001f)
    }

    @Test
    fun `lighting score never drops below 0_70`() {
        assertEquals(0.70f, SkyScoreFormula.lightingScore(1f), 0.0001f)
    }

    @Test
    fun `darkness follows the phase anchors`() {
        assertEquals(0.00f, SkyScoreFormula.darkness(prob(1f, 0f, 0f, 0f)), 0.0001f)
        assertEquals(0.20f, SkyScoreFormula.darkness(prob(0f, 1f, 0f, 0f)), 0.0001f)
        assertEquals(0.375f, SkyScoreFormula.darkness(prob(0f, 0f, 1f, 0f)), 0.0001f)
        assertEquals(1.00f, SkyScoreFormula.darkness(prob(0f, 0f, 0f, 1f)), 0.0001f)
    }

    @Test
    fun `daylight gate blocks only when darkness is very low`() {
        assertEquals(
            com.auroraar.skyscore.model.Gate.BLOCKED_DAYLIGHT,
            SkyScoreFormula.gate(0.05f, 0.9f, cameraBlocked = false, creative = false),
        )
        assertEquals(
            com.auroraar.skyscore.model.Gate.OPEN,
            SkyScoreFormula.gate(0.05f, 0.9f, cameraBlocked = false, creative = true),
        )
    }

    @Test
    fun `no sky gate wins over daylight gate`() {
        assertEquals(
            com.auroraar.skyscore.model.Gate.NO_SKY,
            SkyScoreFormula.gate(0.05f, 0.02f, cameraBlocked = false, creative = false),
        )
    }

    @Test
    fun `rating bands match the spec`() {
        assertEquals(RatingBand.EXCELLENT, SkyScoreFormula.band(92))
        assertEquals(RatingBand.GREAT, SkyScoreFormula.band(74))
        assertEquals(RatingBand.GOOD, SkyScoreFormula.band(55))
        assertEquals(RatingBand.FAIR, SkyScoreFormula.band(33))
        assertEquals(RatingBand.LIMITED, SkyScoreFormula.band(12))
        assertEquals(RatingBand.UNAVAILABLE, SkyScoreFormula.band(0))
    }

    @Test
    fun `phase ordinals stay aligned with darkness anchors`() {
        assertEquals(0, Phase.DAYLIGHT.ordinal)
        assertEquals(1, Phase.SUNSET.ordinal)
        assertEquals(2, Phase.TWILIGHT.ordinal)
        assertEquals(3, Phase.NIGHT.ordinal)
    }
}
