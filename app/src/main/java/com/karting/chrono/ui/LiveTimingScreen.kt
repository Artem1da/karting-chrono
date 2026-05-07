package com.karting.chrono.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.karting.chrono.service.TimingService
import com.karting.chrono.service.formatLap
import kotlinx.coroutines.delay

@Composable
fun LiveTimingScreen(
    state: TimingService.State,
    onStop: () -> Unit,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.lapStartMs) {
        // Tick at ~30 Hz so the hundredths display feels live without being wasteful.
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(33L)
        }
    }

    val currentLapMs = state.lapStartMs?.let { (nowMs - it).coerceAtLeast(0L) }
    val gpsStale = state.gpsLastFixAtMs?.let { (nowMs - it) > 3_000L } ?: true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (gpsStale) "GPS lost" else "Lap ${state.lapCount + 1}",
                style = MaterialTheme.typography.caption2,
                color = if (gpsStale) MaterialTheme.colors.error else MaterialTheme.colors.onBackground,
            )
            Text(
                text = currentLapMs?.let { formatLap(it) } ?: "—:—",
                fontFamily = FontFamily.Monospace,
                fontSize = 32.sp,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Last  ${state.lastLapMs?.let { formatLap(it) } ?: "—"}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.body2,
            )
            Text(
                text = "Best  ${state.bestLapMs?.let { formatLap(it) } ?: "—"}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.primary,
            )
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.primaryButtonColors(),
        ) { Text("Stop") }
    }
}
