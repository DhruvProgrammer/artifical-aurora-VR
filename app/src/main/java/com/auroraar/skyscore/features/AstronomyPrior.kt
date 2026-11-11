package com.auroraar.skyscore.features

import com.auroraar.skyscore.model.AstroPrior
import com.auroraar.skyscore.model.Phase
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tan

/**
 * Offline sun position (NOAA/Meeus style) + a phase prior. No network, no
 * space-weather data. This is a prior only - a phone may be indoors, in a
 * valley, or under a storm, so its confidence is always < 1.
 *
 * Location is optional. Without it we fall back to the time-zone meridian and
 * a mid-latitude guess, and reduce confidence.
 */
object AstronomyPrior {

    private const val FALLBACK_LAT = 20.0

    fun forTime(
        epochMs: Long,
        latitude: Double?,
        longitude: Double?,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): AstroPrior {
        val validLat = latitude != null && latitude.isFinite() && latitude in -90.0..90.0
        val validLon = longitude != null && longitude.isFinite() && longitude in -180.0..180.0
        val hasLocation = validLat && validLon
        val lat = if (hasLocation) latitude!! else FALLBACK_LAT
        val lon = if (hasLocation) {
            longitude!!
        } else {
            val offsetMinutes = runCatching { timeZone.getOffset(epochMs) / 60_000.0 }
                .getOrDefault(0.0)
            if (offsetMinutes.isFinite()) offsetMinutes / 4.0 else 0.0
        }
        val elevation = sunElevationDeg(epochMs, lat, lon)
        if (!elevation.isFinite()) {
            return AstroPrior(
                sunElevationDeg = 0f,
                prob = floatArrayOf(0.25f, 0.25f, 0.25f, 0.25f).toList(),
                confidence = 0.1f,
                hasLocation = false,
            )
        }
        val prob = phasePrior(elevation)
        val confidence = if (hasLocation) 0.9f else 0.30f
        return AstroPrior(elevation, prob.toList(), confidence, hasLocation)
    }

    fun sunElevationDeg(epochMs: Long, latDeg: Double, lonDeg: Double): Float {
        val jd = epochMs / 86_400_000.0 + 2_440_587.5
        val t = (jd - 2_451_545.0) / 36_525.0

        val l0 = 280.46646 + t * (36_000.76983 + t * 0.0003032)
        val m = 357.52911 + t * (35_999.05029 - 0.0001537 * t)
        val mRad = Math.toRadians(m)

        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val center = sin(mRad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * mRad) * (0.019993 - 0.000101 * t) +
            sin(3 * mRad) * 0.000289

        val trueLong = l0 + center
        val omega = 125.04 - 1934.136 * t
        val appLong = trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))

        val seconds = 21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))
        val meanObliquity = 23.0 + (26.0 + seconds / 60.0) / 60.0
        val obliquity = meanObliquity + 0.00256 * cos(Math.toRadians(omega))

        val decl = asin(sin(Math.toRadians(obliquity)) * sin(Math.toRadians(appLong)))

        val y = tan(Math.toRadians(obliquity) / 2).let { it * it }
        val l0Rad = Math.toRadians(l0)
        val eqTime = 4 * Math.toDegrees(
            y * sin(2 * l0Rad) -
                2 * e * sin(mRad) +
                4 * e * y * sin(mRad) * cos(2 * l0Rad) -
                0.5 * y * y * sin(4 * l0Rad) -
                1.25 * e * e * sin(2 * mRad),
        )

        // True solar time uses UTC minutes plus the longitude term; adding the
        // timezone offset as well would count it twice.
        val utcMinutes = ((epochMs / 60_000.0) % 1440.0 + 1440.0) % 1440.0
        val trueSolarMinutes = (utcMinutes + eqTime + 4 * lonDeg).mod(1440.0)
        val hourAngle = Math.toRadians(trueSolarMinutes / 4.0 - 180.0)

        val latRad = Math.toRadians(latDeg)
        val cosZenith = sin(latRad) * sin(decl) + cos(latRad) * cos(decl) * cos(hourAngle)
        val zenith = acos(cosZenith.coerceIn(-1.0, 1.0))
        return Math.toDegrees(Math.PI / 2 - zenith).toFloat()
    }

    fun phasePrior(sunElevationDeg: Float): FloatArray {
        val elev = if (sunElevationDeg.isFinite()) sunElevationDeg.coerceIn(-90f, 90f) else 0f
        val day = smoothstep(2f, 8f, elev)
        val night = 1f - smoothstep(-14f, -6f, elev)
        val sunset = gauss(elev, 2f, 4.2f)
        val twilight = gauss(elev, -7f, 5.0f)

        val raw = floatArrayOf(
            day.coerceAtLeast(0.001f),
            sunset.coerceAtLeast(0.001f),
            twilight.coerceAtLeast(0.001f),
            night.coerceAtLeast(0.001f),
        )
        // Nudge in favour of astronomy near the extremes.
        if (day > 0.9f) raw[Phase.DAYLIGHT.ordinal] += 0.3f
        if (night > 0.9f) raw[Phase.NIGHT.ordinal] += 0.3f

        val sum = raw.sum()
        return FloatArray(4) { raw[it] / sum }
    }

    /** Normalize an arbitrary elevation to a 0..1 "how bright should the sky be" value. */
    fun skyLumNormFromElevation(sunElevationDeg: Float): Float {
        val elev = sunElevationDeg.coerceIn(-18f, 20f)
        return (1f - smoothstep(-18f, 8f, elev)).coerceIn(0f, 1f)
    }

    private fun gauss(x: Float, mu: Float, sigma: Float): Float {
        val z = (x - mu) / sigma
        return exp(-0.5f * z * z)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (abs(edge1 - edge0) < 1e-6f) return if (x < edge0) 0f else 1f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
