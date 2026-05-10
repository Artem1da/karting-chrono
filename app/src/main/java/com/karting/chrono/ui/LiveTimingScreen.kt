package com.karting.chrono.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.karting.chrono.core.FinishLine
import com.karting.chrono.service.TimingService
import com.karting.chrono.service.formatLap
import kotlinx.coroutines.delay

@Composable
fun LiveTimingScreen(
    state: TimingService.State,
    finishLine: FinishLine?,
    onStop: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> TimingPage(state = state, onStop = onStop)
                1 -> TrackView(
                    samples = state.trackPoints,
                    finishLine = finishLine,
                    title = "Track",
                )
            }
        }
        // Page indicator dots — bottom centre, two small circles.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(2) { i ->
                val active = pagerState.currentPage == i
                Box(
                    Modifier
                        .size(if (active) 6.dp else 4.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colors.onBackground
                            else Color.Gray.copy(alpha = 0.5f)
                        )
                )
            }
        }
    }
}

@Composable
private fun TimingPage(
    state: TimingService.State,
    onStop: () -> Unit,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.lapStartMs) {
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
