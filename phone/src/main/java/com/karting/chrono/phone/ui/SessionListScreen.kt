package com.karting.chrono.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karting.chrono.phone.data.PhoneSessionDatabase
import com.karting.chrono.phone.data.PhoneSessionEntity
import com.karting.chrono.sync.ActiveSessionStatus
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    active: ActiveSessionStatus?,
    onOpen: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    val sessionsFlow = remember(ctx) {
        runCatching { PhoneSessionDatabase.get(ctx).dao().observeSessions() }
            .getOrElse { flowOf(emptyList()) }
    }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Karting Chrono") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (active != null) {
                ActiveBanner(active)
                HorizontalDivider()
            }

            if (sessions.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(sessions, key = { it.watchSessionId }) { s ->
                        SessionRow(s, onClick = { onOpen(s.watchSessionId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveBanner(active: ActiveSessionStatus) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column {
            Text(
                text = "Session active on watch",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    append("Lap ").append(active.lapCount + 1)
                    active.bestLapMs?.let { append("  •  best ").append(formatLap(it)) }
                    active.lastLapMs?.let { append("  •  last ").append(formatLap(it)) }
                },
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SessionRow(s: PhoneSessionEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = formatDate(s.startedAt), style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${s.lapCount} laps  •  best ${s.bestLapMs?.let(::formatLap) ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = s.bestLapMs?.let(::formatLap) ?: "—",
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No sessions yet.\nRun a session on your watch — it'll appear here.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

