package com.auroraar.camera

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager

/**
 * Last known coarse location, used only to refine the offline sun position.
 * Optional and permission-gated; the app works without it.
 */
class LocationProvider(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun lastKnown(): Pair<Double, Double>? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        // Coarse permission may not grant GPS access on every OEM; a denial for
        // one provider must not discard the others.
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )
        var best: Location? = null
        for (provider in providers) {
            try {
                if (!manager.isProviderEnabled(provider)) continue
            } catch (_: IllegalArgumentException) {
                continue
            } catch (_: SecurityException) {
                continue
            }
            val location = try {
                manager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                continue
            } catch (_: IllegalArgumentException) {
                continue
            } ?: continue
            if (!location.latitude.isFinite() || !location.longitude.isFinite()) continue
            if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) continue
            if (best == null || location.time > best.time) best = location
        }
        val location = best ?: return null
        // A position from days ago is worse than the timezone fallback.
        if (System.currentTimeMillis() - location.time > MAX_AGE_MS) return null
        return location.latitude to location.longitude
    }

    companion object {
        private const val MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
