package com.auroraar.premium

import com.auroraar.premium.styles.AuroraStyle
import com.auroraar.premium.styles.AuroraStyleRegistry
import com.auroraar.premium.styles.StyleTier
import com.auroraar.skyscore.model.CinematicLook
import com.auroraar.skyscore.model.IntensityAutomation
import com.auroraar.skyscore.model.UserSettings

/**
 * The single place that decides what a tier may use. UI and renderer both read
 * the sanitized settings, so a locked style can never leak through preferences
 * or a debug-only selection.
 */
object FeatureGate {

    fun canUseStyle(style: AuroraStyle, isPremium: Boolean): Boolean =
        style.tier == StyleTier.FREE || isPremium

    fun canUseStyle(styleId: String, isPremium: Boolean): Boolean =
        canUseStyle(AuroraStyleRegistry.byId(styleId), isPremium)

    fun advancedControlsUnlocked(isPremium: Boolean): Boolean = isPremium

    fun cinematicLooksUnlocked(isPremium: Boolean): Boolean = isPremium

    fun intensityAutomationUnlocked(isPremium: Boolean): Boolean = isPremium

    /**
     * Returns the settings the renderer may apply. Persisted premium choices are
     * kept intact so a later purchase re-enables them, but Free renders the
     * default style and no premium grade/automation.
     */
    fun effectiveSettings(settings: UserSettings, isPremium: Boolean): UserSettings {
        val requested = AuroraStyleRegistry.byId(settings.styleId)
        val style = if (canUseStyle(requested, isPremium)) requested else AuroraStyleRegistry.DEFAULT
        return if (isPremium) {
            settings.copy(styleId = style.id)
        } else {
            // Free renders the style's own base parameters; persisted advanced
            // values survive so a later purchase can reuse them.
            settings.copy(
                styleId = style.id,
                cinematicLook = CinematicLook.NATURAL,
                layers = style.layers,
                coverage = style.coverage,
                waveAmp = style.waveAmp,
                waveFreq = style.waveFreq,
                advancedSpeed = 1f,
                raySharpness = 1f,
                burst = style.burst,
                direction = style.direction,
                automation = IntensityAutomation.NONE,
                automationParam = 0.5f,
            )
        }
    }
}
