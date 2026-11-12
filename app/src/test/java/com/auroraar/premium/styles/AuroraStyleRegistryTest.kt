package com.auroraar.premium.styles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuroraStyleRegistryTest {

    @Test
    fun `catalogue has five free and seven premium styles`() {
        assertEquals(5, AuroraStyleRegistry.FREE.size)
        assertEquals(7, AuroraStyleRegistry.PREMIUM.size)
        assertEquals(12, AuroraStyleRegistry.ALL.size)
        assertTrue(AuroraStyleRegistry.FREE.all { it.tier == StyleTier.FREE })
        assertTrue(AuroraStyleRegistry.PREMIUM.all { it.tier == StyleTier.PREMIUM })
    }

    @Test
    fun `pink and blue styles are free and use the measured emission profile`() {
        // Both new styles must be reachable in a release build, where Premium is
        // locked, and must render the banded profile rather than a flat gradient.
        listOf("rose_corona", "cobalt_cascade").forEach { id ->
            val style = AuroraStyleRegistry.byId(id)
            assertEquals(id, style.id)
            assertEquals(StyleTier.FREE, style.tier)
            assertTrue("$id must use the emission palette", style.emission)
        }
    }

    @Test
    fun `pink style keeps blue low and red high`() {
        val rose = AuroraStyleRegistry.byId("rose_corona")
        // Blue is the 428 nm edge band, red is the 630 nm crown.
        assertTrue(blueChannel(rose.lowColor) > blueChannel(rose.highColor))
        assertTrue(redChannel(rose.highColor) > redChannel(rose.lowColor))
    }

    @Test
    fun `blue style is blue dominant at its lower edge`() {
        val cobalt = AuroraStyleRegistry.byId("cobalt_cascade")
        val low = cobalt.lowColor
        assertTrue(blueChannel(low) > redChannel(low))
        assertTrue("lower edge must be strongly blue", blueChannel(low) > 200)
    }

    @Test
    fun `only emission styles opt into the banded palette`() {
        // The legacy gradient must stay available so existing looks are unchanged.
        assertTrue(AuroraStyleRegistry.ALL.any { !it.emission })
    }

    @Test
    fun `ids are unique and resolvable`() {
        val ids = AuroraStyleRegistry.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertEquals(id, AuroraStyleRegistry.byId(id).id)
        }
    }

    @Test
    fun `unknown ids fall back to the default`() {
        assertEquals(AuroraStyleRegistry.DEFAULT, AuroraStyleRegistry.byId("unknown"))
        assertEquals(AuroraStyleRegistry.DEFAULT.id, AuroraStyleRegistry.sanitizeId(""))
    }

    @Test
    fun `premium styles include real geometry variety`() {
        val geometries = AuroraStyleRegistry.ALL.map { it.geometry }.toSet()
        assertTrue(geometries.size >= 5)
    }

    @Test
    fun `palette and shape values stay inside the shader contract`() {
        AuroraStyleRegistry.ALL.forEach { style ->
            assertTrue(style.layers in 1..4)
            assertTrue(style.coverage in 0f..1f)
            assertTrue(style.waveAmp in 0f..1f)
            assertTrue(style.waveFreq in 0f..1f)
            assertTrue(style.burst in 0f..1f)
            assertTrue(style.direction == -1f || style.direction == 1f)
            // Every palette entry is a fully opaque ARGB colour.
            assertTrue((style.lowColor ushr 24) == 0xFF)
            assertTrue((style.midColor ushr 24) == 0xFF)
            assertTrue((style.highColor ushr 24) == 0xFF)
        }
    }

    private fun blueChannel(color: Int) = color and 0xFF

    private fun redChannel(color: Int) = (color shr 16) and 0xFF
}
