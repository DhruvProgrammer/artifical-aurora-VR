package com.auroraar.capture

import android.content.Context
import com.auroraar.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local entitlement cache. Play Billing is intentionally not wired in this
 * build (it would require the INTERNET permission and a Play Console account),
 * so release builds are always Free. Debug builds expose a manual switch so
 * both policy paths can be exercised on a device.
 */
class Entitlements(context: Context) {

    private val prefs = context.getSharedPreferences("aurora_entitlements", Context.MODE_PRIVATE)
    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_PREMIUM, false))
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    val policy: RecordingPolicy
        get() = RecordingPolicy.forTier(
            if (_isPremium.value) RecordingTier.PREMIUM else RecordingTier.FREE,
        )

    /** Release builds cannot be upgraded locally; only Play Billing could do that. */
    fun setPremium(value: Boolean) {
        if (value && !BuildConfig.DEBUG) return
        prefs.edit().putBoolean(KEY_PREMIUM, value).apply()
        _isPremium.value = value
    }

    private companion object {
        const val KEY_PREMIUM = "premium_debug"
    }
}
