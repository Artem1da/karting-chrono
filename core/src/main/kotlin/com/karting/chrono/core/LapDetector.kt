package com.karting.chrono.core

/** A completed lap. */
data class Lap(
    val index: Int,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
) {
    val durationMs: Long get() = endTimestampMs - startTimestampMs
}

/** Configurable thresholds for the detector. */
data class LapDetectorConfig(
    /** Minimum elapsed lap time (ms) before another crossing is accepted. */
    val minLapMs: Long = 20_000L,
    /**
     * Optional minimum speed (m/s) at the moment of crossing.
     * Crossings below this are ignored — useful to filter pit-lane drift.
     * Set to null to disable.
     */
    val minSpeedMps: Double? = null,
)

/**
 * What the detector returns for each new GPS sample.
 */
sealed class DetectorEvent {
    data object Idle : DetectorEvent()
    data object FirstFix : DetectorEvent()

    /** Crossing detected and accepted; a lap was completed. */
    data class LapCompleted(val lap: Lap, val crossingTimestampMs: Long) : DetectorEvent()

    /** Crossing detected but rejected (too soon, or below minimum speed). */
    data class CrossingIgnored(val reason: Reason, val crossingTimestampMs: Long) :
        DetectorEvent() {
        enum class Reason { MIN_LAP_TIME, MIN_SPEED }
    }
}

/**
 * Stateful, frame-by-frame lap detector.
 *
 * Feed it GPS samples in chronological order via [onSample]. The first call
 * after [start] establishes the timing origin (lap 1 begins on the next
 * crossing — there is no implicit "lap 0"). Each subsequent sample is paired
 * with the previous one; if the segment between them crosses the finish line,
 * we linearly interpolate the crossing timestamp.
 *
 * Pure Kotlin, no Android dependencies.
 */
class LapDetector(
    private val finishLine: FinishLine,
    private val config: LapDetectorConfig = LapDetectorConfig(),
) {
    private val projection: LocalProjection = finishLine.projection()
    private val lineA: Vec2
    private val lineB: Vec2

    init {
        val (a, b) = finishLine.localEndpoints()
        lineA = a
        lineB = b
    }

    private var prevSample: GpsSample? = null
    private var lastCrossingMs: Long? = null
    private var lapStartMs: Long? = null
    private var lapIndex: Int = 0

    private val laps = mutableListOf<Lap>()

    /** Read-only snapshot of all completed laps in chronological order. */
    fun laps(): List<Lap> = laps.toList()

    /** Current lap's start time, or null if no lap is in progress. */
    fun currentLapStartMs(): Long? = lapStartMs

    /**
     * Feed the next GPS sample. Returns an event describing what happened.
     *
     * Behavior:
     *  - First sample after construction: returns FirstFix; no lap yet.
     *  - Subsequent samples: checks segment(prev, current) vs the finish line.
     *  - On the FIRST accepted crossing the lap timer begins (lap 1 starts
     *    at that crossing instant). Subsequent accepted crossings emit
     *    LapCompleted with the lap that just finished and start the next.
     */
    fun onSample(sample: GpsSample): DetectorEvent {
        val prev = prevSample
        if (prev == null) {
            prevSample = sample
            return DetectorEvent.FirstFix
        }

        val pPrev = projection.project(prev.latDeg, prev.lonDeg)
        val pCur = projection.project(sample.latDeg, sample.lonDeg)

        val hit = SegmentMath.intersect(pPrev, pCur, lineA, lineB)
        prevSample = sample

        if (hit == null) return DetectorEvent.Idle

        // Linearly interpolate the crossing timestamp using the fraction t
        // along the [prev, cur] segment where the intersection occurred.
        val crossingMs = prev.timestampMs +
            ((sample.timestampMs - prev.timestampMs).toDouble() * hit.t).toLong()

        // Speed at crossing, linearly interpolated when both samples report it.
        val speedAtCrossing = interpolatedSpeed(prev, sample, hit.t)
        config.minSpeedMps?.let { minSpd ->
            if (speedAtCrossing != null && speedAtCrossing < minSpd) {
                return DetectorEvent.CrossingIgnored(
                    DetectorEvent.CrossingIgnored.Reason.MIN_SPEED,
                    crossingMs,
                )
            }
        }

        val last = lastCrossingMs
        if (last != null && (crossingMs - last) < config.minLapMs) {
            return DetectorEvent.CrossingIgnored(
                DetectorEvent.CrossingIgnored.Reason.MIN_LAP_TIME,
                crossingMs,
            )
        }

        // Accept the crossing.
        if (last == null) {
            // First accepted crossing — lap 1 begins now.
            lastCrossingMs = crossingMs
            lapStartMs = crossingMs
            return DetectorEvent.Idle
        }

        // Otherwise close the current lap and open the next.
        val startedAt = lapStartMs ?: last
        lapIndex += 1
        val lap = Lap(
            index = lapIndex,
            startTimestampMs = startedAt,
            endTimestampMs = crossingMs,
        )
        laps += lap
        lastCrossingMs = crossingMs
        lapStartMs = crossingMs
        return DetectorEvent.LapCompleted(lap, crossingMs)
    }

    private fun interpolatedSpeed(a: GpsSample, b: GpsSample, t: Double): Double? {
        val sa = a.speedMps ?: return null
        val sb = b.speedMps ?: return null
        return sa + (sb - sa) * t
    }
}
