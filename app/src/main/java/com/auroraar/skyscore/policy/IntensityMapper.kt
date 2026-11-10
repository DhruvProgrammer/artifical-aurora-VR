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
        val i = (intensityIndex * settings.maxIntensity).coerceIn(0f, 1f)
        val d = darkness.coerceIn(0f, 1f)
        val c = cloudEffective.coerceIn(0f, 1f)

        if (i <= 0.01f) {
            return AuroraRenderParams(
                enabled = false,
                intensity = 0f,
                cloud = c,
                skyLumNorm = skyLumNorm,
                creative = creative,
            )
        }

        val shaped = i // I
        val brightness = settings.intensity * pow12(shaped)
        val saturation = lerp(0.55f, 1.00f, smoothstep(0.1f, 0.9f, shaped)) * settings.saturation
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
            skyLumNorm = skyLumNorm.coerceIn(0f, 1f),
            creative = creative,
        )
    }

    private fun pow12(x: Float): Float {
        // x^1.2, gentle toe so low scores look faint rather than muddy.
        val v = x.coerceIn(0f, 1f)
        return v * v.pow(0.2f)
    }
}
