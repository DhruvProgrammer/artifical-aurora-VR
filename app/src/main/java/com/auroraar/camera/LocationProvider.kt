package com.auroraar.camera

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager

/**
 * Last known coarse location, used only to refine the offline sun position.
 * Optional and permission-gated; the app works without it.
 */
class LocationProvider(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun lastKnown(): Pair<Double, Double>? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        for (provider in providers) {
            try {
                val location = manager.getLastKnownLocation(provider) ?: continue
                return location.latitude to location.longitude
            } catch (_: SecurityException) {
                return null
            } catch (_: IllegalArgumentException) {
                continue
            }
        }
        return null
    }
}
