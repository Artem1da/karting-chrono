package com.karting.chrono.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karting.chrono.core.FinishLine
import com.karting.chrono.core.LocalProjection
import com.karting.chrono.core.Vec2
import com.karting.chrono.phone.data.PhoneLapEntity
import com.karting.chrono.phone.data.PhoneSessionDatabase
import com.karting.chrono.phone.data.PhoneSessionEntity
import com.karting.chrono.phone.data.PhoneTrackPointEntity
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val dao = remember(ctx) { PhoneSessionDatabase.get(ctx).dao() }

    val session by dao.observeSession(sessionId)
        .collectAsStateWithLifecycle(initialValue = null)
    val laps by dao.observeLaps(sessionId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val track by dao.observeTrack(sessionId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(session?.startedAt?.let(::formatDate) ?: "Session")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        if (session == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Loading…") }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HeaderSummary(session!!, laps)
            TrackCanvas(
                track = track,
                line = FinishLine(
                    aLatDeg = session!!.lineALat, aLonDeg = session!!.lineALon,
                    bLatDeg = session!!.lineBLat, bLonDeg = session!!.lineBLon,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LapList(laps, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun HeaderSummary(session: PhoneSessionEntity, laps: List<PhoneLapEntity>) {
    val best = laps.minByOrNull { it.endMs - it.startMs }
    val avg = if (laps.isNotEmpty()) laps.sumOf { it.endMs - it.startMs } / laps.size else null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row {
            Stat("Laps", laps.size.toString())
            Spacer(Modifier.fillMaxWidth(0.05f))
            Stat("Best", best?.let { formatLap(it.endMs - it.startMs) } ?: "—")
            Spacer(Modifier.fillMaxWidth(0.05f))
            Stat("Avg", avg?.let(::formatLap) ?: "—")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun LapList(laps: List<PhoneLapEntity>, modifier: Modifier = Modifier) {
    val best = laps.minByOrNull { it.endMs - it.startMs }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(laps, key = { it.lapIndex }) { lap ->
            val isBest = lap.lapIndex == best?.lapIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "%2d".format(lap.lapIndex),
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(0.15f),
                )
                Text(
                    text = formatLap(lap.endMs - lap.startMs),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                    color = if (isBest) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun TrackCanvas(
    track: List<PhoneTrackPointEntity>,
    line: FinishLine,
    modifier: Modifier = Modifier,
) {
    if (track.isEmpty()) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("No track data", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    val trackColor = MaterialTheme.colorScheme.primary
    val headColor = MaterialTheme.colorScheme.onSurface
    val lineColor = MaterialTheme.colorScheme.error
    val bg = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier.background(bg)) {
        val origin = track.first()
        val proj = LocalProjection(origin.lat, origin.lon)
        val pts: List<Vec2> = track.map { proj.project(it.lat, it.lon) }

        val a = proj.project(line.aLatDeg, line.aLonDeg)
        val b = proj.project(line.bLatDeg, line.bLonDeg)

        var minX = pts.minOf { it.x }; var maxX = pts.maxOf { it.x }
        var minY = pts.minOf { it.y }; var maxY = pts.maxOf { it.y }
        minX = minOf(minX, a.x, b.x); maxX = maxOf(maxX, a.x, b.x)
        minY = minOf(minY, a.y, b.y); maxY = maxOf(maxY, a.y, b.y)

        val pad = 16f
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val rangeX = (maxX - minX).coerceAtLeast(1.0)
        val rangeY = (maxY - minY).coerceAtLeast(1.0)
        val scale = minOf(w / rangeX, h / rangeY).toFloat()
        val drawnW = (rangeX * scale).toFloat()
        val drawnH = (rangeY * scale).toFloat()
        val cx = pad + (w - drawnW) / 2f
        val cy = pad + (h - drawnH) / 2f

        fun toCanvas(v: Vec2) = Offset(
            x = cx + ((v.x - minX) * scale).toFloat(),
            y = cy + (drawnH - ((v.y - minY) * scale).toFloat()),
        )

        val path = Path()
        val first = toCanvas(pts.first())
        path.moveTo(first.x, first.y)
        for (i in 1 until pts.size) {
            val o = toCanvas(pts[i]); path.lineTo(o.x, o.y)
        }
        drawPath(path, trackColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
        drawCircle(headColor, radius = 5f, center = toCanvas(pts.last()))
        drawLine(lineColor, toCanvas(a), toCanvas(b), strokeWidth = 4f, cap = StrokeCap.Round)
    }
}
