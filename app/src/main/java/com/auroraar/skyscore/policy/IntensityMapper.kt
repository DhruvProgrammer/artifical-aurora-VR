package com.auroraar.skyscore.policy

import com.auroraar.skyscore.model.AuroraRenderParams
import com.auroraar.skyscore.model.UserSettings
import com.auroraar.skyscore.policy.SkyScoreFormula.lerp
import com.auroraar.skyscore.policy.SkyScoreFormula.smoothstep
import kotlin.math.pow

/**
 * Addendum §10. Maps the (already smoothed) SkyScore index and the measured
 * darkness/cloud onto renderer uniforms. User sliders are multipliers on top of
 * the adaptive values, never replacements for the safety gates.
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

        if (!i.isFinite() || i <= 0.01f) {
            return AuroraRenderParams(
                enabled = false,
                intensity = 0f,
                cloud = c,
                skyLumNorm = lumNorm,
                creative = creative,
            )
        }

        val shaped = i // I
        val intensity = (if (settings.intensity.isFinite()) settings.intensity else 0.85f).coerceIn(0f, 1f)
        val userSaturation = (if (settings.saturation.isFinite()) settings.saturation else 1f).coerceIn(0f, 2f)
        val brightness = intensity * pow12(shaped)
        val saturation = lerp(0.55f, 1.00f, smoothstep(0.1f, 0.9f, shaped)) * userSaturation
        val density = lerp(0.25f, 1.00f, smoothstep(0.1f, 0.9f, shaped))
        val glow = lerp(0.35f, 1.00f, shaped) * (1f - 0.25f * c)
        val rayContrast = lerp(0.40f, 1.00f, shaped)
        val speed = lerp(0.70f, 1.00f, shaped)
        val sharpness = lerp(0.50f, 1.00f, 1f - c)
        val stars = (1f - c) * smoothstep(0.5f, 1.0f, d)
        val nightGrade = (1f - d).coerceIn(0f, 1f) * 0.9f

        return AuroraRenderParams(
            enabled = true,
            intensity = brightness.coerceIn(0f, 1f),
            saturation = saturation.coerceIn(0f, 2f),
            density = density.coerceIn(0f, 1f),
            glow = glow.coerceIn(0f, 1.5f),
            rayContrast = rayContrast.coerceIn(0f, 1f),
            speed = speed.coerceIn(0.5f, 1.2f),
            sharpness = sharpness.coerceIn(0f, 1f),
            stars = stars.coerceIn(0f, 1f),
            nightGrade = nightGrade.coerceIn(0f, 1f),
            cloud = c,
            skyLumNorm = lumNorm,
            creative = creative,
        )
    }

    private fun pow12(x: Float): Float {
        // x^1.2, gentle toe so low scores look faint rather than muddy.
        val v = x.coerceIn(0f, 1f)
        return v * v.pow(0.2f)
    }
}
