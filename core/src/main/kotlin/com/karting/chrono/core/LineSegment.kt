package com.karting.chrono.core

/**
 * Result of a 2D segment-vs-segment intersection.
 *
 * @property t      Fraction along the first segment (P0 -> P1) where the
 *                  intersection occurred, in [0, 1].
 * @property point  The intersection point in local meters.
 */
data class Intersection(val t: Double, val point: Vec2)

object SegmentMath {

    /**
     * Tests whether segment [a0, a1] intersects segment [b0, b1] and, if so,
     * returns the fraction t along the A segment and the crossing point.
     *
     * Standard parametric form: solve
     *   a0 + t * (a1 - a0) = b0 + u * (b1 - b0)
     * for t, u using Cramer's rule. Both must lie in [0, 1] for the segments
     * to actually cross (as opposed to the supporting infinite lines).
     *
     * Returns null when the segments are parallel/collinear or do not cross.
     * Touching at an endpoint counts as crossing (inclusive).
     */
    fun intersect(a0: Vec2, a1: Vec2, b0: Vec2, b1: Vec2): Intersection? {
        val rx = a1.x - a0.x
        val ry = a1.y - a0.y
        val sx = b1.x - b0.x
        val sy = b1.y - b0.y

        val denom = rx * sy - ry * sx
        if (denom == 0.0) return null // parallel or collinear

        val dx = b0.x - a0.x
        val dy = b0.y - a0.y

        val t = (dx * sy - dy * sx) / denom
        val u = (dx * ry - dy * rx) / denom

        if (t < 0.0 || t > 1.0) return null
        if (u < 0.0 || u > 1.0) return null

        val px = a0.x + t * rx
        val py = a0.y + t * ry
        return Intersection(t, Vec2(px, py))
    }
}
