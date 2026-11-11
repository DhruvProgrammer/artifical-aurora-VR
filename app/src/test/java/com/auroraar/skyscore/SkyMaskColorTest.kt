package com.auroraar.skyscore

import com.auroraar.skyscore.features.SkyMaskColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyMaskColorTest {

    @Test
    fun `blue sky is fully plausible`() {
        assertEquals(1f, SkyMaskColor.plausibility(0.18f, 0.38f, 0.72f), 0.001f)
    }

    @Test
    fun `neutral overcast grey is plausible`() {
        assertTrue(SkyMaskColor.plausibility(0.55f, 0.55f, 0.55f) > 0.9f)
    }

    @Test
    fun `warm sunset survives the sky mask`() {
        // This is the case the naive blue-only mask broke.
        val sunset = SkyMaskColor.plausibility(0.72f, 0.38f, 0.26f)
        assertTrue("sunset=$sunset", sunset > 0.55f)
        // The extractor multiplies by 1.25 and accepts > 0.5 after erosion.
        assertTrue("masked alpha=${sunset * 1.25f}", sunset * 1.25f > 0.5f)
    }

    @Test
    fun `green foliage is rejected above the horizon`() {
        assertTrue(SkyMaskColor.plausibility(0.15f, 0.42f, 0.12f) < 0.2f)
    }

    @Test
    fun `deep twilight blue is plausible`() {
        assertTrue(SkyMaskColor.plausibility(0.08f, 0.14f, 0.30f) > 0.9f)
    }

    @Test
    fun `a black covered lens is not sky`() {
        assertEquals(0f, SkyMaskColor.plausibility(0f, 0f, 0f), 0.0001f)
        assertTrue(SkyMaskColor.plausibility(0.02f, 0.02f, 0.02f) < 0.1f)
    }

    @Test
    fun `dark red fabric is not sky`() {
        assertTrue(SkyMaskColor.plausibility(0.15f, 0.02f, 0.02f) < 0.1f)
    }

    @Test
    fun `a genuinely dark blue night sky is still plausible`() {
        assertTrue(SkyMaskColor.plausibility(0.03f, 0.05f, 0.09f) > 0.9f)
    }

    @Test
    fun `a lifted neutral covered lens is not sky`() {
        // AE can lift a covered lens out of digital black; it stays flat and
        // neutral, which a real night sky never is.
        assertTrue(SkyMaskColor.plausibility(0.07f, 0.07f, 0.07f) < 0.1f)
        assertTrue(SkyMaskColor.plausibility(0.09f, 0.09f, 0.09f) < 0.1f)
    }

    @Test
    fun `dim green foliage is rejected above the horizon`() {
        assertTrue(SkyMaskColor.plausibility(0.02f, 0.05f, 0.02f) < 0.2f)
        assertTrue(SkyMaskColor.plausibility(0.03f, 0.06f, 0.03f) < 0.2f)
    }

    @Test
    fun `a tinted dark overcast sky is still plausible`() {
        assertTrue(SkyMaskColor.plausibility(0.05f, 0.06f, 0.08f) > 0.5f)
    }

    @Test
    fun `non-finite colour channels fail closed`() {
        assertEquals(0f, SkyMaskColor.plausibility(Float.NaN, 0.2f, 0.3f), 0.0001f)
    }
}
