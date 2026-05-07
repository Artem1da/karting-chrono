package com.karting.chrono.core

/**
 * One GPS fix delivered by the watch.
 *
 * @property timestampMs UNIX epoch ms (or any monotonic ms — the detector only
 *                       cares about deltas).
 * @property latDeg, lonDeg WGS84 position in degrees.
 * @property speedMps      Ground speed in m/s, or null if unknown.
 * @property accuracyM     Horizontal accuracy in meters (1-sigma), or null.
 */
data class GpsSample(
    val timestampMs: Long,
    val latDeg: Double,
    val lonDeg: Double,
    val speedMps: Double? = null,
    val accuracyM: Double? = null,
    /** Heading in degrees clockwise from true north, if reported. */
    val bearingDeg: Double? = null,
)
