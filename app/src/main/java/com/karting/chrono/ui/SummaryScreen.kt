package com.karting.chrono.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.karting.chrono.core.FinishLine
import com.karting.chrono.core.GpsSample
import com.karting.chrono.core.Lap
import com.karting.chrono.data.SessionDatabase
import com.karting.chrono.service.formatLap
import java.io.File

/**
 * Snapshot of one session for display. Either fed from the live service
 * (just-finished session) or loaded from Room (latest finished session).
 */
data class SessionSnapshot(
    val laps: List<Lap>,
    val track: List<GpsSample>,
    val line: FinishLine?,
)

@Composable
fun SummaryScreen(
    @Suppress("UNUSED_PARAMETER") context: Context,
    liveLaps: List<Lap>,
    liveTrack: List<GpsSample>,
    liveLine: FinishLine?,
    onBack: () -> Unit,
    onShowTrack: (List<GpsSample>, FinishLine?) -> Unit,
) {
    val ctx = LocalContext.current

    // If the service still has a session in memory, use that. Otherwise load
    // the most recently finished session from Room.
    var snapshot by remember(liveLaps) {
        mutableStateOf(
            if (liveLaps.isNotEmpty()) SessionSnapshot(liveLaps, liveTrack, liveLine)
            else null
        )
    }

    LaunchedEffect(liveLaps) {
        if (liveLaps.isEmpty() && snapshot == null) {
            snapshot = loadLatestFromDb(ctx)
        }
    }

    val s = snapshot
    if (s == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No sessions yet", style = MaterialTheme.typography.title3)
            Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                Text("Back")
            }
        }
        return
    }

    val laps = s.laps
    val best = laps.minByOrNull { it.durationMs }
    val avg = if (laps.isNotEmpty()) laps.sumOf { it.durationMs } / laps.size else null

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = "Session",
                style = MaterialTheme.typography.title3,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            Text(
                text = "Best ${best?.let { formatLap(it.durationMs) } ?: "—"}  •  " +
                    "Avg ${avg?.let { formatLap(it) } ?: "—"}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.primary,
            )
        }
        if (laps.isEmpty()) {
            item {
                Text(
                    text = "No completed laps",
                    style = MaterialTheme.typography.caption2,
                )
            }
        }
        items(laps) { lap ->
            val isBest = lap.index == best?.index
            Text(
                text = "%2d  %s".format(lap.index, formatLap(lap.durationMs)),
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                color = if (isBest) MaterialTheme.colors.primary
                else MaterialTheme.colors.onBackground,
            )
        }
        item {
            Button(
                onClick = { onShowTrack(s.track, s.line) },
                enabled = s.track.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.secondaryButtonColors(),
            ) { Text("Show track") }
        }
        item {
            Button(
                onClick = { shareLapsAsCsv(ctx, laps) },
                enabled = laps.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.secondaryButtonColors(),
            ) { Text("Share CSV") }
        }
        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = ButtonDefaults.secondaryButtonColors(),
            ) { Text("Back") }
        }
    }
}

private suspend fun loadLatestFromDb(ctx: Context): SessionSnapshot? {
    val dao = SessionDatabase.get(ctx).sessionDao()
    val session = dao.getLatestSession() ?: return null
    val laps = dao.getLaps(session.id).map {
        Lap(index = it.lapIndex, startTimestampMs = it.startMs, endTimestampMs = it.endMs)
    }
    val pts = dao.getPoints(session.id).map {
        GpsSample(timestampMs = it.tsMs, latDeg = it.lat, lonDeg = it.lon)
    }
    val line = FinishLine(
        aLatDeg = session.lineALat, aLonDeg = session.lineALon,
        bLatDeg = session.lineBLat, bLonDeg = session.lineBLon,
    )
    return SessionSnapshot(laps = laps, track = pts, line = line)
}

private fun shareLapsAsCsv(ctx: Context, laps: List<Lap>) {
    val csv = buildString {
        append("lap,start_ms,end_ms,duration_ms\n")
        for (l in laps) {
            append(l.index).append(',')
                .append(l.startTimestampMs).append(',')
                .append(l.endTimestampMs).append(',')
                .append(l.durationMs).append('\n')
        }
    }
    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "laps_${System.currentTimeMillis()}.csv")
    file.writeText(csv)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(intent, "Share laps").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
