package com.karting.chrono.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.karting.chrono.core.FinishLine
import com.karting.chrono.session.SessionViewModel

@Composable
fun SetupScreen(
    gps: SessionViewModel.GpsState,
    line: FinishLine?,
    pendingA: Pair<Double, Double>?,
    onSetStartA: () -> Unit,
    onSetEndB: () -> Unit,
    onCancelPending: () -> Unit,
    onSinglePoint: () -> Unit,
    onClearLine: () -> Unit,
    onStart: () -> Unit,
    onOpenSummary: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Karting Chrono",
            style = MaterialTheme.typography.title3,
        )

        val accStr = gps.accuracyM?.let { "±${"%.0f".format(it)} m" } ?: "—"
        val fixStr = if (gps.hasFix) "GPS: $accStr" else "Acquiring GPS…"
        Text(text = fixStr, style = MaterialTheme.typography.caption2)

        Text(
            text = when {
                line != null -> "Line set ✓"
                pendingA != null -> "Walk to far end of line"
                else -> "No line set"
            },
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        if (pendingA == null && line == null) {
            Button(
                onClick = onSetStartA,
                enabled = gps.hasFix,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Set start (A)") }
            Button(
                onClick = onSinglePoint,
                enabled = gps.hasFix && gps.headingDeg != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.secondaryButtonColors(),
            ) { Text("Single-point line") }
        } else if (pendingA != null) {
            Button(
                onClick = onSetEndB,
                enabled = gps.hasFix,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Set end (B)") }
            Button(
                onClick = onCancelPending,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.secondaryButtonColors(),
            ) { Text("Cancel") }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start session") }
            Button(
                onClick = onClearLine,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.secondaryButtonColors(),
            ) { Text("Clear line") }
        }

        Button(
            onClick = onOpenSummary,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.secondaryButtonColors(),
        ) { Text("Last session") }
    }
}
