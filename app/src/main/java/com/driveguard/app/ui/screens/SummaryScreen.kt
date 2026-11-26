package com.driveguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.driveguard.app.data.SessionSummary
import com.driveguard.app.ui.components.EmptyState
import com.driveguard.app.ui.components.MetricTile
import com.driveguard.app.ui.components.PrimaryAction
import com.driveguard.app.ui.components.SectionCard
import com.driveguard.app.util.TimeFormat

/** Post-drive summary. Shows only metrics that were actually measured. */
@Composable
fun SummaryScreen(
    session: SessionSummary?,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Session complete",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Everything here was measured on this device and stays on it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))
        if (session == null) {
            EmptyState(
                icon = Icons.Rounded.History,
                title = "No session data",
                body = "The session was too short to record.",
            )
        } else {
            SectionCard {
                Row(Modifier.fillMaxWidth()) {
                    MetricTile(
                        "Duration",
                        TimeFormat.durationHuman(session.durationMs),
                        Modifier.weight(1f),
                    )
                    MetricTile("Alerts", session.alertCount.toString(), Modifier.weight(1f))
                }
                if (session.avgInferenceMs > 0f) {
                    HorizontalDivider(
                        Modifier.padding(vertical = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(Modifier.fillMaxWidth()) {
                        MetricTile(
                            "Interruptions",
                            session.monitoringInterruptions.toString(),
                            Modifier.weight(1f),
                        )
                        MetricTile(
                            "Avg. analysis",
                            String.format("%.0f ms", session.avgInferenceMs),
                            Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        PrimaryAction(text = "Done", onClick = onDone)
        Spacer(Modifier.height(8.dp))
    }
}
