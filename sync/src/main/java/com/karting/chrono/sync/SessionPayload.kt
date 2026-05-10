package com.karting.chrono.sync

import com.google.android.gms.wearable.DataMap
import com.karting.chrono.core.FinishLine
import com.karting.chrono.core.GpsSample
import com.karting.chrono.core.Lap

/**
 * Cross-device payload describing one full session.
 *
 * Encoded as a [DataMap] so it can be put into a `PutDataMapRequest` directly.
 * DataMap supports primitive arrays — laps and track points are flattened
 * into parallel double/long arrays to keep the wire format compact.
 */
data class SessionPayload(
    val sessionId: Long,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val finishLine: FinishLine,
    val laps: List<Lap>,
    val track: List<GpsSample>,
) {
    fun writeTo(map: DataMap) {
        map.putLong("id", sessionId)
        map.putLong("startedAt", startedAtMs)
        map.putLong("endedAt", endedAtMs ?: -1L)

        map.putDouble("aLat", finishLine.aLatDeg)
        map.putDouble("aLon", finishLine.aLonDeg)
        map.putDouble("bLat", finishLine.bLatDeg)
        map.putDouble("bLon", finishLine.bLonDeg)

        // Laps: parallel arrays.
        map.putIntArray("lapIdx", IntArray(laps.size) { laps[it].index })
        map.putLongArray("lapStart", LongArray(laps.size) { laps[it].startTimestampMs })
        map.putLongArray("lapEnd", LongArray(laps.size) { laps[it].endTimestampMs })

        // Track: parallel arrays of (ts, lat, lon).
        map.putLongArray("trackTs", LongArray(track.size) { track[it].timestampMs })
        map.putDoubleArray("trackLat", DoubleArray(track.size) { track[it].latDeg })
        map.putDoubleArray("trackLon", DoubleArray(track.size) { track[it].lonDeg })
    }

    companion object {
        fun readFrom(map: DataMap): SessionPayload {
            val endedAt = map.getLong("endedAt", -1L).takeIf { it > 0 }

            val line = FinishLine(
                aLatDeg = map.getDouble("aLat"),
                aLonDeg = map.getDouble("aLon"),
                bLatDeg = map.getDouble("bLat"),
                bLonDeg = map.getDouble("bLon"),
            )

            val lapIdx = map.getIntArray("lapIdx") ?: IntArray(0)
            val lapStart = map.getLongArray("lapStart") ?: LongArray(0)
            val lapEnd = map.getLongArray("lapEnd") ?: LongArray(0)
            val laps = List(lapIdx.size) { i ->
                Lap(
                    index = lapIdx[i],
                    startTimestampMs = lapStart[i],
                    endTimestampMs = lapEnd[i],
                )
            }

            val ts = map.getLongArray("trackTs") ?: LongArray(0)
            val lat = map.getDoubleArray("trackLat") ?: DoubleArray(0)
            val lon = map.getDoubleArray("trackLon") ?: DoubleArray(0)
            val track = List(ts.size) { i ->
                GpsSample(timestampMs = ts[i], latDeg = lat[i], lonDeg = lon[i])
            }

            return SessionPayload(
                sessionId = map.getLong("id"),
                startedAtMs = map.getLong("startedAt"),
                endedAtMs = endedAt,
                finishLine = line,
                laps = laps,
                track = track,
            )
        }
    }
}

/**
 * Compact "is the watch currently in a session?" status, posted at
 * [WearablePaths.PATH_ACTIVE_SESSION] and refreshed on every lap.
 *
 * The DataItem is **deleted** when the session ends so phone listeners can
 * detect the transition via DataEvent.TYPE_DELETED.
 */
data class ActiveSessionStatus(
    val sessionId: Long,
    val startedAtMs: Long,
    val lapStartMs: Long?,
    val lapCount: Int,
    val bestLapMs: Long?,
    val lastLapMs: Long?,
) {
    fun writeTo(map: DataMap) {
        map.putLong("id", sessionId)
        map.putLong("startedAt", startedAtMs)
        map.putLong("lapStart", lapStartMs ?: -1L)
        map.putInt("lapCount", lapCount)
        map.putLong("best", bestLapMs ?: -1L)
        map.putLong("last", lastLapMs ?: -1L)
    }

    companion object {
        fun readFrom(map: DataMap): ActiveSessionStatus = ActiveSessionStatus(
            sessionId = map.getLong("id"),
            startedAtMs = map.getLong("startedAt"),
            lapStartMs = map.getLong("lapStart", -1L).takeIf { it > 0 },
            lapCount = map.getInt("lapCount", 0),
            bestLapMs = map.getLong("best", -1L).takeIf { it > 0 },
            lastLapMs = map.getLong("last", -1L).takeIf { it > 0 },
        )
    }
}
