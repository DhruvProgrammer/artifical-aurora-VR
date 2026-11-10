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
}
