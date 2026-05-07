package com.karting.chrono.core

import kotlin.math.cos
import kotlin.math.sin

/**
 * A start/finish line defined by two geographic endpoints.
 *
 * Detection happens in projected local meters: we use [LocalProjection] anchored
 * on point A so that A is the origin and B has a small (x, y) displacement.
 */
data class FinishLine(
    val aLatDeg: Double,
    val aLonDeg: Double,
    val bLatDeg: Double,
    val bLonDeg: Double,
) {
    fun projection(): LocalProjection = LocalProjection(aLatDeg, aLonDeg)

    /**
     * Project both endpoints into the line's local frame. The first is always
     * (0, 0); the second is in meters relative to A.
     */
    fun localEndpoints(): Pair<Vec2, Vec2> {
        val proj = projection()
        return proj.project(aLatDeg, aLonDeg) to proj.project(bLatDeg, bLonDeg)
    }

    companion object {
        /**
         * Build a perpendicular line of [widthM] meters centered on the given
         * point, oriented orthogonally to the kart's heading.
         *
         * Heading uses Android's convention: degrees clockwise from true north.
         * The line direction is heading + 90deg (i.e. across the track).
         */
        fun centeredOn(
            latDeg: Double,
            lonDeg: Double,
            headingDegFromNorth: Double,
            widthM: Double = 10.0,
        ): FinishLine {
            // East/north components of the across-track direction.
            // Rotate (0, 1) — pointing north — by heading + 90 deg, clockwise.
            // In Android compass terms: bearing 0 = north (+y), 90 = east (+x).
            val perpRad = Math.toRadians(headingDegFromNorth + 90.0)
            val ex = sin(perpRad) // east component
            val ny = cos(perpRad) // north component

            val proj = LocalProjection(latDeg, lonDeg)
            val half = widthM / 2.0
            val a = Vec2(-ex * half, -ny * half)
            val b = Vec2(ex * half, ny * half)
            val (aLat, aLon) = proj.unproject(a)
            val (bLat, bLon) = proj.unproject(b)
            return FinishLine(aLat, aLon, bLat, bLon)
        }
    }
}
