package com.auroraar.skyscore

import com.auroraar.premium.styles.AuroraStyleRegistry
import com.auroraar.skyscore.model.CinematicLook
import com.auroraar.skyscore.model.IntensityAutomation
import com.auroraar.skyscore.model.UserSettings
import com.auroraar.skyscore.policy.IntensityMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntensityMapperTest {

    private val settings = UserSettings()

    @Test
    fun `style emission flag reaches the renderer regardless of tier`() {
        // The emission palette is a property of the style, not of user settings,
        // so a Free render must still receive it. If this ever read from
        // settings instead, both new Free styles would silently fall back to the
        // plain gradient in a release build, where Premium is locked.
        val rose = AuroraStyleRegistry.byId("rose_corona")
        val cobalt = AuroraStyleRegistry.byId("cobalt_cascade")

        listOf(rose, cobalt).forEach { style ->
            val params = IntensityMapper.map(
                intensityIndex = 1f,
                darkness = 1f,
                cloudEffective = 0.2f,
                settings = settings.copy(styleId = style.id),
                creative = false,
                skyLumNorm = 0.1f,
            )
            assertTrue("${style.id} must request the emission palette", params.emission)
            assertEquals(style.lowColor, params.lowColor)
            assertEquals(style.midColor, params.midColor)
            assertEquals(style.highColor, params.highColor)
        }

        // A legacy style must not opt in.
        val nordic = AuroraStyleRegistry.byId("nordic_classic")
        val legacy = IntensityMapper.map(
            intensityIndex = 1f,
            darkness = 1f,
            cloudEffective = 0.2f,
            settings = settings.copy(styleId = nordic.id),
            creative = false,
            skyLumNorm = 0.1f,
        )
        assertFalse("legacy styles must keep the gradient path", legacy.emission)
    }

    @Test
    fun `zero index disables rendering`() {
        val params = IntensityMapper.map(0f, 1f, 0.05f, settings, creative = false, skyLumNorm = 0f)
        assertFalse(params.enabled)
        assertTrue(params.intensity == 0f)
    }

    @Test
    fun `creative mode renders even in a bright sky`() {
        val params = IntensityMapper.map(1f, 0f, 0.2f, settings, creative = true, skyLumNorm = 1f)
        assertTrue(params.enabled)
        assertTrue(params.nightGrade > 0.5f)
        assertTrue(params.intensity > 0.5f)
    }

    @Test
    fun `brightness and saturation increase with the score`() {
        val low = IntensityMapper.map(0.25f, 1f, 0.05f, settings, creative = false, skyLumNorm = 0f)
        val high = IntensityMapper.map(0.95f, 1f, 0.05f, settings, creative = false, skyLumNorm = 0f)
        assertTrue(high.intensity > low.intensity)
        assertTrue(high.saturation > low.saturation)
        assertTrue(high.density > low.density)
    }

    @Test
    fun `stars fade under cloud`() {
        val clear = IntensityMapper.map(1f, 1f, 0.05f, settings, creative = false, skyLumNorm = 0f)
        val cloudy = IntensityMapper.map(1f, 1f, 0.90f, settings, creative = false, skyLumNorm = 0f)
        assertTrue(clear.stars > cloudy.stars)
        assertTrue(cloudy.glow <= clear.glow + 0.0001f)
    }

    @Test
    fun `max intensity caps the render`() {
        val capped = IntensityMapper.map(
            1f,
            1f,
            0.05f,
            settings.copy(maxIntensity = 0.4f),
            creative = false,
            skyLumNorm = 0f,
        )
        assertTrue(capped.intensity <= settings.intensity * 0.4f + 0.0001f)
    }

    @Test
    fun `style geometry palette and advanced values reach the renderer`() {
        val style = AuroraStyleRegistry.byId("festival")
        val params = IntensityMapper.map(
            1f,
            1f,
            0.05f,
            settings.copy(
                styleId = style.id,
                layers = 4,
                coverage = 0.9f,
                waveAmp = 0.7f,
                waveFreq = 0.6f,
                advancedSpeed = 1.3f,
                raySharpness = 1.2f,
                burst = 0.8f,
                direction = -1f,
            ),
            creative = false,
            skyLumNorm = 0f,
        )
        assertEquals(style.geometry.shaderId, params.geometry)
        assertEquals(style.lowColor, params.lowColor)
        assertEquals(style.midColor, params.midColor)
        assertEquals(style.highColor, params.highColor)
        assertEquals(4f, params.layers, 0.0001f)
        assertEquals(0.9f, params.coverage, 0.0001f)
        assertEquals(0.7f, params.waveAmp, 0.0001f)
        assertEquals(0.6f, params.waveFreq, 0.0001f)
        assertEquals(0.8f, params.burst, 0.0001f)
        assertEquals(-1f, params.direction, 0.0001f)
        assertTrue(params.speed > 1f)
    }

    @Test
    fun `cinematic look sets the grade id and grain`() {
        val natural = IntensityMapper.map(1f, 1f, 0.05f, settings, creative = false, skyLumNorm = 0f)
        val cinematic = IntensityMapper.map(
            1f,
            1f,
            0.05f,
            settings.copy(cinematicLook = CinematicLook.CINEMATIC),
            creative = false,
            skyLumNorm = 0f,
        )
        val dreamy = IntensityMapper.map(
            1f,
            1f,
            0.05f,
            settings.copy(cinematicLook = CinematicLook.DREAMY),
            creative = false,
            skyLumNorm = 0f,
        )
        assertEquals(CinematicLook.NATURAL.shaderId, natural.look)
        assertEquals(0f, natural.grain, 0.0001f)
        assertEquals(CinematicLook.CINEMATIC.shaderId, cinematic.look)
        assertTrue(cinematic.grain > 0f)
        assertEquals(CinematicLook.DREAMY.shaderId, dreamy.look)
        assertTrue(dreamy.grain > 0f)
    }

    @Test
    fun `automation is forwarded`() {
        val params = IntensityMapper.map(
            1f,
            1f,
            0.05f,
            settings.copy(
                automation = IntensityAutomation.SUBSTORM,
                automationParam = 0.7f,
            ),
            creative = false,
            skyLumNorm = 0f,
        )
        assertEquals(IntensityAutomation.SUBSTORM.shaderId, params.automation)
        assertEquals(0.7f, params.automationParam, 0.0001f)
    }
}
