package com.auroraar.skyscore

import com.auroraar.skyscore.model.UserSettings
import com.auroraar.skyscore.policy.IntensityMapper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntensityMapperTest {

    private val settings = UserSettings()

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
}
