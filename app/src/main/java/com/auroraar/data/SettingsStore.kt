package com.auroraar.data

import android.content.Context
import com.auroraar.premium.styles.AuroraStyleRegistry
import com.auroraar.skyscore.SkyScoreEngine
import com.auroraar.skyscore.model.CinematicLook
import com.auroraar.skyscore.model.IntensityAutomation
import com.auroraar.skyscore.model.Mode
import com.auroraar.skyscore.model.UserSettings

/** Small SharedPreferences wrapper. No network, no analytics. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("aurora_settings", Context.MODE_PRIVATE)

    var useLocation: Boolean
        get() = prefs.getBoolean(KEY_LOCATION, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCATION, value).apply()

    var recordAudio: Boolean
        get() = prefs.getBoolean(KEY_RECORD_AUDIO, true)
        set(value) = prefs.edit().putBoolean(KEY_RECORD_AUDIO, value).apply()

    var countdown: Boolean
        get() = prefs.getBoolean(KEY_COUNTDOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_COUNTDOWN, value).apply()

    var holdToRecord: Boolean
        get() = prefs.getBoolean(KEY_HOLD_TO_RECORD, false)
        set(value) = prefs.edit().putBoolean(KEY_HOLD_TO_RECORD, value).apply()

    fun load(): UserSettings = SkyScoreEngine.sanitizeSettings(
        UserSettings(
            mode = enumOr(KEY_MODE, Mode.AUTO),
            intensity = prefs.getFloat(KEY_INTENSITY, 0.85f),
            saturation = prefs.getFloat(KEY_SATURATION, 1f),
            maxIntensity = prefs.getFloat(KEY_MAX_INTENSITY, 1f),
            respectClouds = prefs.getBoolean(KEY_RESPECT_CLOUDS, true),
            lockIntensity = prefs.getBoolean(KEY_LOCK_INTENSITY, false),
            hudEnabled = prefs.getBoolean(KEY_HUD, true),
            showSubScores = prefs.getBoolean(KEY_SUB_SCORES, true),
            styleId = AuroraStyleRegistry.sanitizeId(
                prefs.getString(KEY_STYLE, AuroraStyleRegistry.DEFAULT.id)!!,
            ),
            cinematicLook = enumOr(KEY_LOOK, CinematicLook.NATURAL),
            layers = prefs.getInt(KEY_LAYERS, 2),
            coverage = prefs.getFloat(KEY_COVERAGE, 0.75f),
            waveAmp = prefs.getFloat(KEY_WAVE_AMP, 0.5f),
            waveFreq = prefs.getFloat(KEY_WAVE_FREQ, 0.5f),
            advancedSpeed = prefs.getFloat(KEY_ADV_SPEED, 1f),
            raySharpness = prefs.getFloat(KEY_RAY_SHARP, 1f),
            burst = prefs.getFloat(KEY_BURST, 0f),
            direction = prefs.getFloat(KEY_DIRECTION, 1f),
            automation = enumOr(KEY_AUTOMATION, IntensityAutomation.NONE),
            automationParam = prefs.getFloat(KEY_AUTO_PARAM, 0.5f),
        ),
    )

    fun save(settings: UserSettings) {
        prefs.edit()
            .putString(KEY_MODE, settings.mode.name)
            .putFloat(KEY_INTENSITY, settings.intensity)
            .putFloat(KEY_SATURATION, settings.saturation)
            .putFloat(KEY_MAX_INTENSITY, settings.maxIntensity)
            .putBoolean(KEY_RESPECT_CLOUDS, settings.respectClouds)
            .putBoolean(KEY_LOCK_INTENSITY, settings.lockIntensity)
            .putBoolean(KEY_HUD, settings.hudEnabled)
            .putBoolean(KEY_SUB_SCORES, settings.showSubScores)
            .putString(KEY_STYLE, settings.styleId)
            .putString(KEY_LOOK, settings.cinematicLook.name)
            .putInt(KEY_LAYERS, settings.layers)
            .putFloat(KEY_COVERAGE, settings.coverage)
            .putFloat(KEY_WAVE_AMP, settings.waveAmp)
            .putFloat(KEY_WAVE_FREQ, settings.waveFreq)
            .putFloat(KEY_ADV_SPEED, settings.advancedSpeed)
            .putFloat(KEY_RAY_SHARP, settings.raySharpness)
            .putFloat(KEY_BURST, settings.burst)
            .putFloat(KEY_DIRECTION, settings.direction)
            .putString(KEY_AUTOMATION, settings.automation.name)
            .putFloat(KEY_AUTO_PARAM, settings.automationParam)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(key: String, fallback: T): T {
        val raw = prefs.getString(key, null) ?: return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_INTENSITY = "intensity"
        const val KEY_SATURATION = "saturation"
        const val KEY_MAX_INTENSITY = "max_intensity"
        const val KEY_RESPECT_CLOUDS = "respect_clouds"
        const val KEY_LOCK_INTENSITY = "lock_intensity"
        const val KEY_HUD = "hud"
        const val KEY_SUB_SCORES = "sub_scores"
        const val KEY_LOCATION = "use_location"
        const val KEY_RECORD_AUDIO = "record_audio"
        const val KEY_COUNTDOWN = "record_countdown"
        const val KEY_HOLD_TO_RECORD = "record_hold"
        const val KEY_STYLE = "style_id"
        const val KEY_LOOK = "cinematic_look"
        const val KEY_LAYERS = "style_layers"
        const val KEY_COVERAGE = "style_coverage"
        const val KEY_WAVE_AMP = "style_wave_amp"
        const val KEY_WAVE_FREQ = "style_wave_freq"
        const val KEY_ADV_SPEED = "style_speed"
        const val KEY_RAY_SHARP = "style_ray_sharpness"
        const val KEY_BURST = "style_burst"
        const val KEY_DIRECTION = "style_direction"
        const val KEY_AUTOMATION = "style_automation"
        const val KEY_AUTO_PARAM = "style_automation_param"
    }
}
