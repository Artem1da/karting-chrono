package com.karting.chrono.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class LocalProjectionTest {

    @Test
    fun `origin projects to zero`() {
        val proj = LocalProjection(45.0, 5.0)
        val v = proj.project(45.0, 5.0)
        assertEquals(0.0, v.x, 1e-9)
        assertEquals(0.0, v.y, 1e-9)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        val proj = LocalProjection(45.0, 5.0)
        val v = proj.project(46.0, 5.0)
        // ~111_195 m for the spherical-earth approximation
        assertEquals(111_195.0, v.y, 50.0)
        assertEquals(0.0, v.x, 1e-6)
    }

    @Test
    fun `unproject is inverse of project at karting scale`() {
        val proj = LocalProjection(48.5, 2.3)
        // 50 m east, 30 m north
        val (lat, lon) = proj.unproject(Vec2(50.0, 30.0))
        val v = proj.project(lat, lon)
        assertTrue(hypot(v.x - 50.0, v.y - 30.0) < 1e-6)
    }
}
