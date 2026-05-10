package com.karting.chrono.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.karting.chrono.core.FinishLine
import com.karting.chrono.core.GpsSample
import com.karting.chrono.core.LocalProjection
import com.karting.chrono.core.Vec2

/**
 * Renders a 2D bird's-eye view of a GPS trace, auto-fit to the canvas.
 *
 * The trace is projected to local meters around the first sample, then the
 * bounding box of all points is computed and scaled (with aspect ratio
 * preserved) to fit the available canvas with a small inner margin.
 *
 * Style:
 *  - polyline of the trace
 *  - a brighter dot at the latest sample
 *  - if a finish line is provided, drawn as a contrasting cross-bar
 */
@Composable
fun TrackView(
    samples: List<GpsSample>,
    finishLine: FinishLine?,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    if (samples.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No GPS yet", style = MaterialTheme.typography.caption2)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) {
            Text(text = title, style = MaterialTheme.typography.caption2)
        }

        val trackColor = MaterialTheme.colors.primary
        val headColor = MaterialTheme.colors.onBackground
        val lineColor = MaterialTheme.colors.error

        Canvas(modifier = Modifier.fillMaxSize()) {
            val origin = samples.first()
            val proj = LocalProjection(origin.latDeg, origin.lonDeg)
            val pts: List<Vec2> = samples.map { proj.project(it.latDeg, it.lonDeg) }

            val lineProjected: Pair<Vec2, Vec2>? = finishLine?.let {
                proj.project(it.aLatDeg, it.aLonDeg) to proj.project(it.bLatDeg, it.bLonDeg)
            }

            // Bounding box across track AND finish-line endpoints so the line
            // is always visible.
            var minX = pts.minOf { it.x }
            var maxX = pts.maxOf { it.x }
            var minY = pts.minOf { it.y }
            var maxY = pts.maxOf { it.y }
            lineProjected?.let { (a, b) ->
                minX = minOf(minX, a.x, b.x)
                maxX = maxOf(maxX, a.x, b.x)
                minY = minOf(minY, a.y, b.y)
                maxY = maxOf(maxY, a.y, b.y)
            }

            val pad = 12f
            val w = size.width - 2 * pad
            val h = size.height - 2 * pad

            val rangeX = (maxX - minX).coerceAtLeast(1.0)
            val rangeY = (maxY - minY).coerceAtLeast(1.0)
            val scale = minOf(w / rangeX, h / rangeY).toFloat()

            // Centre the scaled content.
            val drawnW = (rangeX * scale).toFloat()
            val drawnH = (rangeY * scale).toFloat()
            val offX = pad + (w - drawnW) / 2f
            val offY = pad + (h - drawnH) / 2f

            fun toCanvas(v: Vec2): Offset = Offset(
                x = offX + ((v.x - minX) * scale).toFloat(),
                // Flip Y so north is up on screen.
                y = offY + (drawnH - ((v.y - minY) * scale).toFloat()),
            )

            // Track polyline.
            val path = Path()
            val first = toCanvas(pts.first())
            path.moveTo(first.x, first.y)
            for (i in 1 until pts.size) {
                val o = toCanvas(pts[i])
                path.lineTo(o.x, o.y)
            }
            drawPath(
                path = path,
                color = trackColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round),
            )

            // Latest position dot.
            val head = toCanvas(pts.last())
            drawCircle(color = headColor, radius = 4f, center = head)

            // Finish line.
            lineProjected?.let { (a, b) ->
                val ca = toCanvas(a)
                val cb = toCanvas(b)
                drawLine(
                    color = lineColor,
                    start = ca,
                    end = cb,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
