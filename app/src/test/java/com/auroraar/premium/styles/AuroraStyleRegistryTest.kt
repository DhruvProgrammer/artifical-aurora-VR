package com.auroraar.premium.styles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuroraStyleRegistryTest {

    @Test
    fun `catalogue has three free and seven premium styles`() {
        assertEquals(3, AuroraStyleRegistry.FREE.size)
        assertEquals(7, AuroraStyleRegistry.PREMIUM.size)
        assertEquals(10, AuroraStyleRegistry.ALL.size)
        assertTrue(AuroraStyleRegistry.FREE.all { it.tier == StyleTier.FREE })
        assertTrue(AuroraStyleRegistry.PREMIUM.all { it.tier == StyleTier.PREMIUM })
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
}
