package com.auroraar.skyscore.policy

import com.auroraar.premium.styles.AuroraStyleRegistry
import com.auroraar.skyscore.model.AuroraRenderParams
import com.auroraar.skyscore.model.CinematicLook
import com.auroraar.skyscore.model.UserSettings
import com.auroraar.skyscore.policy.SkyScoreFormula.lerp
import com.auroraar.skyscore.policy.SkyScoreFormula.smoothstep
import kotlin.math.pow

/**
 * Addendum §10. Maps the (already smoothed) SkyScore index and the measured
 * darkness/cloud onto renderer uniforms. User sliders are multipliers on top of
 * the adaptive values, never replacements for the safety gates.
 *
 * Style parameters come from [AuroraStyleRegistry]; Free users receive the
 * style's base values through [com.auroraar.premium.FeatureGate].
 */
object IntensityMapper {

    fun map(
        intensityIndex: Float,
        darkness: Float,
        cloudEffective: Float,
        settings: UserSettings,
        creative: Boolean,
        skyLumNorm: Float,
    ): AuroraRenderParams {
        val rawIndex = if (intensityIndex.isFinite()) intensityIndex else 0f
        val maxIntensity = if (settings.maxIntensity.isFinite()) settings.maxIntensity else 1f
        val i = (rawIndex * maxIntensity).coerceIn(0f, 1f)
        val d = (if (darkness.isFinite()) darkness else 0f).coerceIn(0f, 1f)
        val c = (if (cloudEffective.isFinite()) cloudEffective else 0.25f).coerceIn(0f, 1f)
        val lumNorm = (if (skyLumNorm.isFinite()) skyLumNorm else 1f).coerceIn(0f, 1f)

        val style = AuroraStyleRegistry.byId(settings.styleId)
        val look = settings.cinematicLook
        val grain = when (look) {
            CinematicLook.CINEMATIC -> 0.028f
            CinematicLook.DREAMY -> 0.016f
            CinematicLook.NATURAL -> 0f
        }

        // Style geometry, palette and grade stay stable even when the aurora is
        // gated off; the camera pass can still apply a Premium cinematic look.
        val base = AuroraRenderParams(
            enabled = false,
            intensity = 0f,
            cloud = c,
            skyLumNorm = lumNorm,
            creative = creative,
            geometry = style.geometry.shaderId,
            lowColor = style.lowColor,
            midColor = style.midColor,
            highColor = style.highColor,
            layers = settings.layers.coerceIn(1, 4).toFloat(),
            coverage = settings.coverage.finiteOr(style.coverage).coerceIn(0f, 1f),
            waveAmp = settings.waveAmp.finiteOr(style.waveAmp).coerceIn(0f, 1f),
            waveFreq = settings.waveFreq.finiteOr(style.waveFreq).coerceIn(0f, 1f),
            direction = if (settings.direction < 0f) -1f else 1f,
            burst = settings.burst.finiteOr(style.burst).coerceIn(0f, 1f),
            look = look.shaderId,
            grain = grain,
            automation = settings.automation.shaderId,
            automationParam = settings.automationParam.finiteOr(0.5f).coerceIn(0f, 1f),
        )

        if (!i.isFinite() || i <= 0.01f) {
            return base
        }

        val shaped = i // I
        val intensity = (if (settings.intensity.isFinite()) settings.intensity else 0.85f).coerceIn(0f, 1f)
        val userSaturation = (if (settings.saturation.isFinite()) settings.saturation else 1f).coerceIn(0f, 2f)
        val brightness = intensity * pow12(shaped)
        val saturation = lerp(0.55f, 1.00f, smoothstep(0.1f, 0.9f, shaped)) * userSaturation
        val density = lerp(0.25f, 1.00f, smoothstep(0.1f, 0.9f, shaped))
        val glow = lerp(0.35f, 1.00f, shaped) * (1f - 0.25f * c)
        val rayContrast = lerp(0.40f, 1.00f, shaped)
        val speed = lerp(0.70f, 1.00f, shaped) *
            settings.advancedSpeed.finiteOr(1f).coerceIn(0.4f, 2f)
        val sharpness = lerp(0.50f, 1.00f, 1f - c) *
            settings.raySharpness.finiteOr(1f).coerceIn(0.3f, 1.5f)
        val stars = (1f - c) * smoothstep(0.5f, 1.0f, d)
        val nightGrade = (1f - d).coerceIn(0f, 1f) * 0.9f

        return base.copy(
            enabled = true,
            intensity = brightness.coerceIn(0f, 1f),
            saturation = saturation.coerceIn(0f, 2f),
            density = density.coerceIn(0f, 1f),
            glow = glow.coerceIn(0f, 1.5f),
            rayContrast = rayContrast.coerceIn(0f, 1f),
            speed = speed.coerceIn(0.3f, 2.5f),
            sharpness = sharpness.coerceIn(0f, 1.5f),
            stars = stars.coerceIn(0f, 1f),
            nightGrade = nightGrade.coerceIn(0f, 1f),
        )
    }

    private fun pow12(x: Float): Float {
        // x^1.2, gentle toe so low scores look faint rather than muddy.
        val v = x.coerceIn(0f, 1f)
        return v * v.pow(0.2f)
    }

    private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
}
