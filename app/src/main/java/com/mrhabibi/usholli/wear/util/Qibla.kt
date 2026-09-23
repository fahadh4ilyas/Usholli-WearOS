package com.mrhabibi.usholli.wear.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Qibla direction helper. */
object Qibla {
    private const val KAABA_LAT = 21.4225066
    private const val KAABA_LNG = 39.8261739

    /**
     * Bearing to the Kaaba from a location, in degrees clockwise from true north,
     * normalised to [0, 360).
     */
    fun bearing(lat: Double, lng: Double): Double {
        val latRad = Math.toRadians(lat)
        val lngRad = Math.toRadians(lng)
        val kaabaLat = Math.toRadians(KAABA_LAT)
        val kaabaLng = Math.toRadians(KAABA_LNG)
        val dLng = kaabaLng - lngRad
        val y = sin(dLng)
        val x = cos(latRad) * tan(kaabaLat) - sin(latRad) * cos(dLng)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }
}
