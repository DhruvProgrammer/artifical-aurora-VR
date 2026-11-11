package com.auroraar.premium.styles

import androidx.annotation.StringRes
import com.auroraar.R
import com.auroraar.skyscore.model.AuroraGeometry

/** Free or paid. The gate lives in [com.auroraar.premium.FeatureGate]. */
enum class StyleTier { FREE, PREMIUM }

/**
 * A real shader/parameter variant, not a recolour: geometry, palette, layer
 * count, coverage, wave motion and default burst all change the rendered shape.
 */
data class AuroraStyle(
    val id: String,
    @StringRes val nameRes: Int,
    val tier: StyleTier,
    val geometry: AuroraGeometry,
    val lowColor: Int,
    val midColor: Int,
    val highColor: Int,
    val layers: Int = 2,
    val coverage: Float = 0.75f,
    val waveAmp: Float = 0.5f,
    val waveFreq: Float = 0.5f,
    val burst: Float = 0f,
    val direction: Float = 1f,
)

/**
 * Data-driven style catalogue from the v5 prompt. Free keeps the three basic
 * looks; Premium adds seven variants including four new geometries.
 */
object AuroraStyleRegistry {

    val FREE: List<AuroraStyle> = listOf(
        AuroraStyle(
            id = "nordic_classic",
            nameRes = R.string.style_nordic_classic,
            tier = StyleTier.FREE,
            geometry = AuroraGeometry.CURTAIN,
            lowColor = 0xFF51FF9E.toInt(),
            midColor = 0xFF2EEBD9.toInt(),
            highColor = 0xFF9E59FF.toInt(),
            layers = 2,
            coverage = 0.75f,
            waveAmp = 0.50f,
            waveFreq = 0.50f,
        ),
        AuroraStyle(
            id = "arctic_violet",
            nameRes = R.string.style_arctic_violet,
            tier = StyleTier.FREE,
            geometry = AuroraGeometry.CURTAIN,
            lowColor = 0xFF6FD6FF.toInt(),
            midColor = 0xFF8D7BFF.toInt(),
            highColor = 0xFFE36BFF.toInt(),
            layers = 3,
            coverage = 0.68f,
            waveAmp = 0.62f,
            waveFreq = 0.58f,
        ),
        AuroraStyle(
            id = "rose_quartz",
            nameRes = R.string.style_rose_quartz,
            tier = StyleTier.FREE,
            geometry = AuroraGeometry.CURTAIN,
            lowColor = 0xFFFF9ECF.toInt(),
            midColor = 0xFFFF6FAE.toInt(),
            highColor = 0xFFB86BFF.toInt(),
            layers = 2,
            coverage = 0.82f,
            waveAmp = 0.36f,
            waveFreq = 0.44f,
        ),
    )

    val PREMIUM: List<AuroraStyle> = listOf(
        AuroraStyle(
            id = "solar_storm",
            nameRes = R.string.style_solar_storm,
            tier = StyleTier.PREMIUM,
            geometry = AuroraGeometry.ARC,
            lowColor = 0xFFFFB347.toInt(),
            midColor = 0xFFFF5E62.toInt(),
            highColor = 0xFFB026FF.toInt(),
            layers = 3,
            coverage = 0.88f,
            waveAmp = 0.72f,
            waveFreq = 0.66f,
            burst = 0.60f,
        ),
        AuroraStyle(
            id = "emerald_veil",
            nameRes = R.string.style_emerald_veil,
            tier = StyleTier.PREMIUM,
            geometry = AuroraGeometry.CURTAIN,
            lowColor = 0xFF5CFFB1.toInt(),
            midColor = 0xFF00E5A0.toInt(),
            highColor = 0xFF00A884.toInt(),
            layers = 4,
            coverage = 0.92f,
            waveAmp = 0.58f,
            waveFreq = 0.40f,
        ),
        AuroraStyle(
            id = "desert_dream",
            nameRes = R.string.style_desert_dream,
            tier = StyleTier.PREMIUM,
            geometry = AuroraGeometry.CURTAIN,
            lowColor = 0xFFFFD59E.toInt(),
            midColor = 0xFFFFA45B.toInt(),
            highColor = 0xFFCC6BFF.toInt(),
            layers = 2,
            coverage = 0.55f,
            waveAmp = 0.30f,
            waveFreq = 0.34f,
        ),
        AuroraStyle(
            id = "corona_crown",
            nameRes = R.string.style_corona_crown,
            tier = StyleTier.PREMIUM,
            geometry = AuroraGeometry.CORONA,
            lowColor = 0xFFFFF3A3.toInt(),
            midColor = 0xFF9CFF8A.toInt(),
            highColor = 0xFF7B6BFF.toInt(),
            layers = 3,
            coverage = 0.80f,
            waveAmp = 0.42f,
            waveFreq = 0.72f,
        ),
        AuroraStyle(
            id = "crimson_arc",
            nameRes = R.string.style_crimson_arc,
            tier = StyleTier.PREMIUM,
            geometry = AuroraGeometry.ARC,
            lowColor = 0xFFFF8A80.toInt(),
            midColor = 0xFFE11D48.toInt(),
            highColor = 0xFF7F1D3A.toInt(),
            layers = 2,
            coverage = 0.94f,
            waveAmp = 0.22f,
            waveFreq = 0.30f,
        ),
        AuroraStyle(
            id = "pillars_light",
            nameRes = R.string.style_pillars_light,
            tier = StyleTier.PREMIUM,
            geometry = AuroraGeometry.PILLARS,
            lowColor = 0xFFE8FFF4.toInt(),
            midColor = 0xFF6BFFC9.toInt(),
            highColor = 0xFF8B7BFF.toInt(),
            layers = 4,
            coverage = 0.72f,
            waveAmp = 0.18f,
            waveFreq = 0.60f,
        ),
        AuroraStyle(
            id = "festival",
            nameRes = R.string.style_festival,
            tier = StyleTier.PREMIUM,
            geometry = AuroraGeometry.FESTIVAL,
            lowColor = 0xFFFFE066.toInt(),
            midColor = 0xFF4DFFDF.toInt(),
            highColor = 0xFFFF5EDB.toInt(),
            layers = 4,
            coverage = 0.96f,
            waveAmp = 0.82f,
            waveFreq = 0.78f,
            burst = 0.85f,
        ),
    )

    val ALL: List<AuroraStyle> = FREE + PREMIUM

    val DEFAULT: AuroraStyle = FREE.first()

    fun byId(id: String): AuroraStyle = ALL.firstOrNull { it.id == id } ?: DEFAULT

    /** Unknown persisted ids fall back to the default instead of breaking the renderer. */
    fun sanitizeId(id: String): String = byId(id).id

    fun available(isPremium: Boolean): List<AuroraStyle> = if (isPremium) ALL else FREE
}
