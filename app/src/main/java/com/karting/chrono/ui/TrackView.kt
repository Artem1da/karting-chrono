package com.karting.chrono.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.karting.chrono.core.FinishLine
import com.karting.chrono.core.GpsSample
import com.karting.chrono.core.LocalProjection
import com.karting.chrono.core.Vec2
import kotlin.math.exp
import kotlin.math.ln

/**
 * Bird's-eye view of a GPS trace, auto-fit to the canvas.
 *
 * Default state always fits the entire trace inside the screen. The user
 * can then:
 *   - rotate the side crown to zoom in / out (1.0x = fit-to-screen)
 *   - drag a finger across the canvas to pan
 *   - double-tap anywhere to reset to fit-to-screen
 *
 * Interaction can be disabled via [interactionEnabled] — useful when the
 * view sits inside a pager, so off-screen instances don't silently consume
 * rotary input.
 */
@Composable
fun TrackView(
    samples: List<GpsSample>,
    finishLine: FinishLine?,
    modifier: Modifier = Modifier,
    title: String? = null,
    interactionEnabled: Boolean = true,
) {
    if (samples.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No GPS yet", style = MaterialTheme.typography.caption2)
        }
        return
    }

    // Survives Pager page disposal and process recreation. Stored as
    // floats so autoSaver handles them without a custom Offset saver.
    var userScale by rememberSaveable { mutableStateOf(1f) }
    var panX by rememberSaveable { mutableStateOf(0f) }
    var panY by rememberSaveable { mutableStateOf(0f) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(interactionEnabled) {
        if (interactionEnabled) focusRequester.requestFocus()
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
            val scaleLabel = if (userScale == 1f) "fit" else "%.1fx".format(userScale)
            Text(
                text = "$title  ·  $scaleLabel",
                style = MaterialTheme.typography.caption2,
            )
        }

        val trackColor = MaterialTheme.colors.primary
        val headColor = MaterialTheme.colors.onBackground
        val lineColor = MaterialTheme.colors.error

        val baseModifier = Modifier.fillMaxSize()
        val canvasModifier = if (interactionEnabled) {
            baseModifier
                // Rotary crown -> zoom. Logarithmic so each detent multiplies
                // by the same factor instead of adding pixels of scale.
                .onRotaryScrollEvent { event ->
                    val step = ln(1.10f) // ~10% per scroll unit
                    val k = exp(event.verticalScrollPixels / 120f * step)
                    userScale = (userScale * k).coerceIn(0.5f, 8f)
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
                // Double-tap anywhere to reset to fit-to-screen.
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        userScale = 1f
                        panX = 0f
                        panY = 0f
                    })
                }
                // Drag to pan.
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        panX += dragAmount.x
                        panY += dragAmount.y
                    }
                }
        } else {
            baseModifier
        }

        Canvas(modifier = canvasModifier) {
            val origin = samples.first()
            val proj = LocalProjection(origin.latDeg, origin.lonDeg)
            val pts: List<Vec2> = samples.map { proj.project(it.latDeg, it.lonDeg) }

            val lineProjected: Pair<Vec2, Vec2>? = finishLine?.let {
                proj.project(it.aLatDeg, it.aLonDeg) to proj.project(it.bLatDeg, it.bLonDeg)
            }

            // Bounding box across track AND finish line so neither is clipped
            // at the default (fit-to-screen) zoom.
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
            // Base scale: fit content inside (w, h) preserving aspect ratio.
            val baseScale = minOf(w / rangeX, h / rangeY).toFloat()
            val scale = baseScale * userScale

            // Centre the content in the canvas, then apply user pan.
            val drawnW = (rangeX * scale).toFloat()
            val drawnH = (rangeY * scale).toFloat()
            val cx = pad + (w - drawnW) / 2f + panX
            val cy = pad + (h - drawnH) / 2f + panY

            fun toCanvas(v: Vec2): Offset = Offset(
                x = cx + ((v.x - minX) * scale).toFloat(),
                // Flip Y so geographic north is up on screen.
                y = cy + (drawnH - ((v.y - minY) * scale).toFloat()),
            )

            // Track polyline.
            val path = Path()
            val firstPt = toCanvas(pts.first())
            path.moveTo(firstPt.x, firstPt.y)
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
