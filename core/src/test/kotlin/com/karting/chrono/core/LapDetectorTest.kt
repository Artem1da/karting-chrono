package com.karting.chrono.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic-trace tests for the lap detector.
 *
 * We pick a finish line that runs east-west across local origin (lat0, lon0)
 * and feed simulated samples that approach from south (negative y) and exit
 * to the north (positive y).
 */
class LapDetectorTest {

    private val lat0 = 45.0
    private val lon0 = 5.0
    private val proj = LocalProjection(lat0, lon0)

    /** Build a finish line ~10 m wide centered on the origin, running east-west. */
    private fun line(): FinishLine {
        val (latW, lonW) = proj.unproject(Vec2(-5.0, 0.0))
        val (latE, lonE) = proj.unproject(Vec2(5.0, 0.0))
        return FinishLine(latW, lonW, latE, lonE)
    }

    private fun sample(tMs: Long, x: Double, y: Double, speed: Double? = 20.0): GpsSample {
        val (lat, lon) = proj.unproject(Vec2(x, y))
        return GpsSample(tMs, lat, lon, speedMps = speed)
    }

    @Test
    fun `first sample yields FirstFix and no laps`() {
        val det = LapDetector(line())
        val ev = det.onSample(sample(0, 0.0, -10.0))
        assertTrue(ev is DetectorEvent.FirstFix)
        assertTrue(det.laps().isEmpty())
    }

    @Test
    fun `second sample crossing line starts the timer but completes no lap`() {
        val det = LapDetector(line())
        det.onSample(sample(0, 0.0, -5.0))
        val ev = det.onSample(sample(1000, 0.0, 5.0)) // crosses at t=0.5 -> 500 ms
        // First crossing only starts the lap timer.
        assertTrue("got $ev", ev is DetectorEvent.Idle)
        assertEquals(500L, det.currentLapStartMs())
        assertTrue(det.laps().isEmpty())
    }

    @Test
    fun `interpolation handles asymmetric placement`() {
        val det = LapDetector(line())
        // prev at y = -1, cur at y = +9 -> crossing at t = 0.1 along [prev, cur]
        det.onSample(sample(10_000, 0.0, -1.0))
        det.onSample(sample(11_000, 0.0, 9.0))
        // First crossing: timer starts at 10_000 + 0.1 * 1000 = 10_100
        assertEquals(10_100L, det.currentLapStartMs())
    }

    @Test
    fun `two crossings yield one lap with interpolated duration`() {
        val det = LapDetector(line(), LapDetectorConfig(minLapMs = 1_000L))
        det.onSample(sample(0, 0.0, -5.0))
        det.onSample(sample(1_000, 0.0, 5.0))   // crossing at 500
        det.onSample(sample(40_000, 0.0, -5.0)) // back to south side
        det.onSample(sample(41_000, 0.0, 5.0))  // crossing at 40_500
        val laps = det.laps()
        assertEquals(1, laps.size)
        assertEquals(40_000L, laps[0].durationMs)
        assertEquals(1, laps[0].index)
    }

    @Test
    fun `crossing exactly on a sample yields t at endpoint`() {
        val det = LapDetector(line())
        det.onSample(sample(0, 0.0, -5.0))
        det.onSample(sample(1_000, 0.0, 0.0)) // touches the line exactly at t=1
        // First crossing — starts the timer at t=1000
        assertEquals(1_000L, det.currentLapStartMs())
    }

    @Test
    fun `consecutive samples on same side do not register a crossing`() {
        val det = LapDetector(line())
        det.onSample(sample(0, 0.0, -5.0))
        val a = det.onSample(sample(1_000, 0.0, -3.0))
        val b = det.onSample(sample(2_000, 0.0, -1.0))
        assertTrue(a is DetectorEvent.Idle)
        assertTrue(b is DetectorEvent.Idle)
        assertTrue(det.laps().isEmpty())
    }

    @Test
    fun `min lap time guard rejects rapid second crossing`() {
        val det = LapDetector(line(), LapDetectorConfig(minLapMs = 20_000L))
        det.onSample(sample(0, 0.0, -5.0))
        det.onSample(sample(1_000, 0.0, 5.0))     // first crossing -> timer starts
        det.onSample(sample(2_000, 0.0, -5.0))
        val ev = det.onSample(sample(3_000, 0.0, 5.0)) // ~2s later, must be ignored
        assertTrue("got $ev", ev is DetectorEvent.CrossingIgnored)
        assertEquals(
            DetectorEvent.CrossingIgnored.Reason.MIN_LAP_TIME,
            (ev as DetectorEvent.CrossingIgnored).reason,
        )
        assertTrue(det.laps().isEmpty())
    }

    @Test
    fun `min speed guard rejects slow crossings`() {
        val det = LapDetector(line(), LapDetectorConfig(minSpeedMps = 5.0))
        det.onSample(sample(0, 0.0, -5.0, speed = 1.0))
        val ev = det.onSample(sample(1_000, 0.0, 5.0, speed = 1.0))
        assertTrue("got $ev", ev is DetectorEvent.CrossingIgnored)
        assertEquals(
            DetectorEvent.CrossingIgnored.Reason.MIN_SPEED,
            (ev as DetectorEvent.CrossingIgnored).reason,
        )
    }

    @Test
    fun `gps jitter near the line does not produce phantom laps`() {
        // The kart never actually reaches the line — it wobbles south of it.
        val det = LapDetector(line())
        var t = 0L
        for (y in listOf(-1.0, -0.5, -0.8, -0.2, -0.6, -0.1, -0.4)) {
            det.onSample(sample(t, 0.0, y))
            t += 1_000
        }
        assertTrue(det.laps().isEmpty())
        assertEquals(null, det.currentLapStartMs())
    }

    @Test
    fun `multi-lap synthetic trace produces sequential lap indices`() {
        val det = LapDetector(line(), LapDetectorConfig(minLapMs = 5_000L))
        // 4 crossings -> 3 laps
        val crossings = listOf(0L, 30_000L, 65_000L, 95_000L)
        for ((i, tCross) in crossings.withIndex()) {
            // approach + cross
            det.onSample(sample(tCross - 1_000, 0.0, -5.0))
            det.onSample(sample(tCross + 1_000, 0.0, 5.0))
            // crossing time interpolates to ~ tCross
            if (i < crossings.size - 1) {
                // walk back south for the next loop
                det.onSample(sample(tCross + 5_000, 0.0, -5.0))
            }
        }
        val laps = det.laps()
        assertEquals(3, laps.size)
        assertEquals(listOf(1, 2, 3), laps.map { it.index })
        // Durations approximately equal to gaps between crossings.
        assertEquals(30_000L, laps[0].durationMs)
        assertEquals(35_000L, laps[1].durationMs)
        assertEquals(30_000L, laps[2].durationMs)
    }
}
