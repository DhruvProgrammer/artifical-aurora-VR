package com.auroraar.data

import android.content.Context
import com.auroraar.skyscore.model.Mode
import com.auroraar.skyscore.model.UserSettings

/** Small SharedPreferences wrapper. No network, no analytics. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("aurora_settings", Context.MODE_PRIVATE)

    var useLocation: Boolean
        get() = prefs.getBoolean(KEY_LOCATION, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCATION, value).apply()

    fun load(): UserSettings = UserSettings(
        mode = runCatching { Mode.valueOf(prefs.getString(KEY_MODE, Mode.AUTO.name)!!) }
            .getOrDefault(Mode.AUTO),
        intensity = prefs.getFloat(KEY_INTENSITY, 0.85f),
        saturation = prefs.getFloat(KEY_SATURATION, 1f),
        maxIntensity = prefs.getFloat(KEY_MAX_INTENSITY, 1f),
        respectClouds = prefs.getBoolean(KEY_RESPECT_CLOUDS, true),
        lockIntensity = prefs.getBoolean(KEY_LOCK_INTENSITY, false),
        hudEnabled = prefs.getBoolean(KEY_HUD, true),
        showSubScores = prefs.getBoolean(KEY_SUB_SCORES, true),
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
            .apply()
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
    }
}
