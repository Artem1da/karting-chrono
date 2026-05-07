package com.karting.chrono.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SegmentMathTest {

    @Test
    fun `crosses through middle returns t = 0_5`() {
        val hit = SegmentMath.intersect(
            Vec2(-1.0, 0.0), Vec2(1.0, 0.0),
            Vec2(0.0, -1.0), Vec2(0.0, 1.0),
        )
        assertNotNull(hit)
        assertEquals(0.5, hit!!.t, 1e-9)
        assertEquals(0.0, hit.point.x, 1e-9)
        assertEquals(0.0, hit.point.y, 1e-9)
    }

    @Test
    fun `parallel segments do not intersect`() {
        val hit = SegmentMath.intersect(
            Vec2(0.0, 0.0), Vec2(10.0, 0.0),
            Vec2(0.0, 1.0), Vec2(10.0, 1.0),
        )
        assertNull(hit)
    }

    @Test
    fun `collinear segments treated as non-crossing`() {
        val hit = SegmentMath.intersect(
            Vec2(0.0, 0.0), Vec2(10.0, 0.0),
            Vec2(5.0, 0.0), Vec2(15.0, 0.0),
        )
        assertNull(hit)
    }

    @Test
    fun `segments that miss along their supporting lines do not intersect`() {
        // Lines cross but the segments themselves are too short to reach.
        val hit = SegmentMath.intersect(
            Vec2(0.0, 0.0), Vec2(1.0, 0.0),
            Vec2(5.0, -1.0), Vec2(5.0, 1.0),
        )
        assertNull(hit)
    }

    @Test
    fun `crossing exactly on first endpoint returns t = 0`() {
        val hit = SegmentMath.intersect(
            Vec2(0.0, 0.0), Vec2(2.0, 0.0),
            Vec2(0.0, -1.0), Vec2(0.0, 1.0),
        )
        assertNotNull(hit)
        assertEquals(0.0, hit!!.t, 1e-9)
    }

    @Test
    fun `crossing exactly on last endpoint returns t = 1`() {
        val hit = SegmentMath.intersect(
            Vec2(-2.0, 0.0), Vec2(0.0, 0.0),
            Vec2(0.0, -1.0), Vec2(0.0, 1.0),
        )
        assertNotNull(hit)
        assertEquals(1.0, hit!!.t, 1e-9)
    }
}
