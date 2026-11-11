package com.auroraar.premium

import com.auroraar.premium.styles.AuroraStyleRegistry
import com.auroraar.premium.styles.StyleTier
import com.auroraar.skyscore.model.CinematicLook
import com.auroraar.skyscore.model.IntensityAutomation
import com.auroraar.skyscore.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureGateTest {

    private val premiumStyle = AuroraStyleRegistry.PREMIUM.first()

    @Test
    fun `free styles are available to both tiers`() {
        AuroraStyleRegistry.FREE.forEach { style ->
            assertTrue(FeatureGate.canUseStyle(style, isPremium = false))
            assertTrue(FeatureGate.canUseStyle(style, isPremium = true))
        }
    }

    @Test
    fun `premium styles are gated`() {
        AuroraStyleRegistry.PREMIUM.forEach { style ->
            assertFalse(FeatureGate.canUseStyle(style, isPremium = false))
            assertTrue(FeatureGate.canUseStyle(style, isPremium = true))
        }
        assertTrue(FeatureGate.advancedControlsUnlocked(true))
        assertFalse(FeatureGate.advancedControlsUnlocked(false))
        assertTrue(FeatureGate.cinematicLooksUnlocked(true))
        assertFalse(FeatureGate.cinematicLooksUnlocked(false))
    }

    @Test
    fun `unknown style ids fall back to the default`() {
        val effective = FeatureGate.effectiveSettings(
            UserSettings(styleId = "does-not-exist"),
            isPremium = true,
        )
        assertEquals(AuroraStyleRegistry.DEFAULT.id, effective.styleId)
    }

    @Test
    fun `free sanitises premium choices but keeps them persisted upstream`() {
        val requested = UserSettings(
            styleId = premiumStyle.id,
            cinematicLook = CinematicLook.CINEMATIC,
            layers = 4,
            coverage = 0.9f,
            waveAmp = 0.8f,
            burst = 0.7f,
            direction = -1f,
            automation = IntensityAutomation.SUBSTORM,
            automationParam = 0.8f,
        )
        val effective = FeatureGate.effectiveSettings(requested, isPremium = false)
        assertEquals(AuroraStyleRegistry.DEFAULT.id, effective.styleId)
        assertEquals(CinematicLook.NATURAL, effective.cinematicLook)
        assertEquals(IntensityAutomation.NONE, effective.automation)
        assertEquals(AuroraStyleRegistry.DEFAULT.layers, effective.layers)
        assertEquals(AuroraStyleRegistry.DEFAULT.coverage, effective.coverage, 0.0001f)
        assertEquals(AuroraStyleRegistry.DEFAULT.burst, effective.burst, 0.0001f)
        assertEquals(AuroraStyleRegistry.DEFAULT.direction, effective.direction, 0.0001f)
    }

    @Test
    fun `premium keeps every choice`() {
        val requested = UserSettings(
            styleId = premiumStyle.id,
            cinematicLook = CinematicLook.DREAMY,
            layers = 4,
            coverage = 0.9f,
            automation = IntensityAutomation.PULSE,
        )
        val effective = FeatureGate.effectiveSettings(requested, isPremium = true)
        assertEquals(premiumStyle.id, effective.styleId)
        assertEquals(CinematicLook.DREAMY, effective.cinematicLook)
        assertEquals(IntensityAutomation.PULSE, effective.automation)
        assertEquals(4, effective.layers)
        assertEquals(0.9f, effective.coverage, 0.0001f)
    }

    @Test
    fun `free style keeps its own base values`() {
        val style = AuroraStyleRegistry.FREE.last()
        val effective = FeatureGate.effectiveSettings(
            UserSettings(styleId = style.id, layers = 4, coverage = 0.1f),
            isPremium = false,
        )
        assertEquals(style.id, effective.styleId)
        assertEquals(style.layers, effective.layers)
        assertEquals(style.coverage, effective.coverage, 0.0001f)
        assertEquals(StyleTier.FREE, AuroraStyleRegistry.byId(effective.styleId).tier)
    }
}
