package com.auroraar.skyscore.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The aurora is not one colour. These tests pin the measured vertical order
 * (blue at the lower edge, green through the body, red at the top) so a future
 * palette tweak cannot silently invert the physics.
 */
class AuroraEmissionTest {

    @Test
    fun `normalize maps the observed altitude band onto the curtain coordinate`() {
        assertEquals(0f, AuroraEmission.normalize(AuroraEmission.LOWER_KM), 1e-6f)
        assertEquals(1f, AuroraEmission.normalize(AuroraEmission.UPPER_KM), 1e-6f)
        // Outside the emitting band the coordinate clamps instead of going out of range.
        assertEquals(0f, AuroraEmission.normalize(0f), 1e-6f)
        assertEquals(1f, AuroraEmission.normalize(900f), 1e-6f)
    }

    @Test
    fun `blue peaks at the bottom edge of the curtain`() {
        val bottom = AuroraEmission.weights(AuroraEmission.normalize(AuroraEmission.BLUE_PEAK_KM))
        val middle = AuroraEmission.weights(AuroraEmission.normalize(AuroraEmission.GREEN_PEAK_KM))
        val top = AuroraEmission.weights(AuroraEmission.normalize(240f))

        assertTrue("blue must dominate at the lower edge", bottom.blue > bottom.green)
        assertTrue("blue must dominate at the lower edge", bottom.blue > bottom.red)
        assertTrue("blue must fade in the body", bottom.blue > middle.blue)
        assertTrue("blue must be gone near the top", top.blue < 0.02f)
    }

    @Test
    fun `green dominates the body at the measured 557_7 nm peak`() {
        val body = AuroraEmission.weights(AuroraEmission.normalize(AuroraEmission.GREEN_PEAK_KM))
        assertEquals(AuroraEmission.Band.GREEN, body.dominant())
        assertTrue("green must be the strongest emission overall", body.green > 0.9f)
    }

    @Test
    fun `red is quenched low down and dominates only at the top`() {
        val low = AuroraEmission.weights(AuroraEmission.normalize(AuroraEmission.RED_ONSET_KM - 20f))
        val top = AuroraEmission.weights(0.98f)

        assertEquals("630 nm must be fully quenched below its onset", 0f, low.red, 1e-6f)
        assertEquals(AuroraEmission.Band.RED, top.dominant())
        assertTrue("red must be faint, never the main emission", top.red <= 0.90f)
    }

    @Test
    fun `vertical order is blue then green then red`() {
        // Sample the whole curtain and confirm each band peaks lower than the next.
        val samples = (0..100).map { AuroraEmission.weights(it / 100f) }
        val bluePeak = samples.indexOfFirst { it.blue == samples.maxOf { s -> s.blue } }
        val greenPeak = samples.indexOfFirst { it.green == samples.maxOf { s -> s.green } }
        val redPeak = samples.indexOfFirst { it.red == samples.maxOf { s -> s.red } }

        assertTrue("blue ($bluePeak) must peak below green ($greenPeak)", bluePeak < greenPeak)
        assertTrue("green ($greenPeak) must peak below red ($redPeak)", greenPeak < redPeak)
    }

    @Test
    fun `mixing the bands at the lower edge yields a blue-dominant colour`() {
        val green = 0xFF2EEBD9.toInt()
        val blue = 0xFF2E6BFF.toInt()
        val red = 0xFFD93A6B.toInt()

        val bottom = AuroraEmission.mix(AuroraEmission.normalize(AuroraEmission.BLUE_PEAK_KM), blue, green, red)
        val top = AuroraEmission.mix(0.98f, blue, green, red)

        assertTrue("bottom must keep full alpha", (bottom ushr 24) == 0xFF)
        assertTrue("top must keep full alpha", (top ushr 24) == 0xFF)
        // Blue channel dominates at the bottom, red channel at the top.
        assertTrue(blueChannel(bottom) > redChannel(bottom))
        assertTrue(redChannel(top) > blueChannel(top))
    }

    @Test
    fun `screen mapping starts at the curtain alpha knee`() {
        // Regression guard. Mapping emission from the top of the frame instead
        // of the curtain's alpha ramp put the blue band and part of the green
        // body inside the fade, where they rendered as nothing.
        assertEquals(0f, AuroraEmission.screenToHeight(AuroraEmission.CURTAIN_BOTTOM_V), 1e-6f)
        assertEquals(1f, AuroraEmission.screenToHeight(AuroraEmission.CURTAIN_TOP_V), 1e-6f)
        assertTrue(AuroraEmission.screenToHeight(0f) == 0f)
        assertTrue(AuroraEmission.screenToHeight(1f) == 1f)
        assertTrue("non-finite must not escape", AuroraEmission.screenToHeight(Float.NaN).isFinite())
        // The blue peak must land above the alpha knee, i.e. be visible at all.
        assertTrue(AuroraEmission.CURTAIN_BOTTOM_V < 0.55f)
    }

    @Test
    fun `red ceiling stays well below green so the crown cannot flood the curtain`() {
        assertTrue(AuroraEmission.RED_CEILING > 0f)
        assertTrue("red must stay a faint crown", AuroraEmission.RED_CEILING <= 0.40f)
    }

    @Test
    fun `non-finite heights fall back to a plausible curtain`() {        val nan = AuroraEmission.weights(Float.NaN)
        val infinite = AuroraEmission.weights(Float.POSITIVE_INFINITY)
        val reference = AuroraEmission.weights(AuroraEmission.normalize(AuroraEmission.GREEN_PEAK_KM))

        assertEquals(reference.green, nan.green, 1e-6f)
        assertEquals(reference.green, infinite.green, 1e-6f)
        assertTrue(AuroraEmission.normalize(Float.NaN).isFinite())
    }

    @Test
    fun `every height produces a finite weight triple`() {
        (0..200).forEach { i ->
            val w = AuroraEmission.weights(i / 200f)
            assertTrue(w.blue.isFinite() && w.blue >= 0f)
            assertTrue(w.green.isFinite() && w.green >= 0f)
            assertTrue(w.red.isFinite() && w.red >= 0f)
        }
    }

    private fun blueChannel(color: Int) = color and 0xFF
    private fun redChannel(color: Int) = (color shr 16) and 0xFF
}
