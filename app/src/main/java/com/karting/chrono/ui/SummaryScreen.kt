package com.karting.chrono.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import com.karting.chrono.core.Lap
import com.karting.chrono.service.formatLap
import java.io.File

@Composable
fun SummaryScreen(
    @Suppress("UNUSED_PARAMETER") context: Context,
    laps: List<Lap>,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val best = laps.minByOrNull { it.durationMs }
    val avg = if (laps.isNotEmpty()) laps.sumOf { it.durationMs } / laps.size else null

    if (laps.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No laps yet", style = MaterialTheme.typography.title3)
            Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                Text("Back")
            }
        }
        return
    }

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
                onClick = { shareLapsAsCsv(ctx, laps) },
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
