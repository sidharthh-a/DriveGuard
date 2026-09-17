package com.driveguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.driveguard.app.data.SessionSummary
import com.driveguard.app.ui.components.EmptyState
import com.driveguard.app.ui.components.SectionCard
import com.driveguard.app.util.TimeFormat
import java.text.DateFormat
import java.util.Date

/** Session history: list, delete one, clear all. Entirely on-device. */
@Composable
fun HistoryScreen(
    sessions: List<SessionSummary>,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("History", style = MaterialTheme.typography.headlineMedium)
            if (sessions.isNotEmpty()) {
                TextButton(onClick = onClearAll) { Text("Clear all") }
            }
        }
        if (sessions.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.History,
                title = "No sessions yet",
                body = "Completed driving sessions will appear here.",
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions, key = { it.id }) { s ->
                    SessionRow(s, onDelete)
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionSummary, onDelete: (Long) -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(session.startedAtMs)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${TimeFormat.durationHuman(session.durationMs)} · " +
                        "${session.alertCount} alerts · " +
                        "${session.monitoringInterruptions} interruptions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onDelete(session.id) }) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete session")
            }
        }
    }
}
