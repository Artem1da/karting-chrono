package com.karting.chrono.core

import kotlin.math.PI
import kotlin.math.cos

/**
 * Equirectangular projection of WGS84 (lat, lon) into local meters around an origin.
 *
 * For karting tracks (a few hundred meters across) the curvature of the earth is
 * negligible, so we can treat a small patch as flat. We pick a fixed origin
 * (typically the start/finish line) and convert every other point relative to it.
 *
 * Math:
 *   1 deg of latitude  ~= R * (pi/180) meters
 *   1 deg of longitude ~= R * (pi/180) * cos(lat0) meters
 *
 * where R = 6_371_000 m (mean earth radius). cos(lat0) is evaluated once at the
 * origin and reused — within a track, the error from holding it constant is
 * far below GPS noise.
 */
class LocalProjection(val originLatDeg: Double, val originLonDeg: Double) {

    private val metersPerDegLat: Double = EARTH_RADIUS_M * PI / 180.0
    private val metersPerDegLon: Double =
        EARTH_RADIUS_M * PI / 180.0 * cos(originLatDeg * PI / 180.0)

    /** Project (lat, lon) to local (x = east, y = north) meters. */
    fun project(latDeg: Double, lonDeg: Double): Vec2 {
        val x = (lonDeg - originLonDeg) * metersPerDegLon
        val y = (latDeg - originLatDeg) * metersPerDegLat
        return Vec2(x, y)
    }

    /** Inverse of [project] — convert local meters back to (lat, lon). */
    fun unproject(local: Vec2): Pair<Double, Double> {
        val lat = local.y / metersPerDegLat + originLatDeg
        val lon = local.x / metersPerDegLon + originLonDeg
        return lat to lon
    }

    companion object {
        const val EARTH_RADIUS_M: Double = 6_371_000.0
    }
}

/** Plain 2D vector in local meters. */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)
}
