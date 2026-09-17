package com.driveguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp),
        )
        Text("Session complete", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        if (session == null) {
            EmptyState(
                icon = Icons.Rounded.CheckCircle,
                title = "No session data",
                body = "The session was too short to record.",
            )
        } else {
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MetricTile("Duration", TimeFormat.durationHuman(session.durationMs))
                    MetricTile("Alerts", session.alertCount.toString())
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MetricTile("Interruptions", session.monitoringInterruptions.toString())
                    if (session.avgInferenceMs > 0f) {
                        MetricTile(
                            "Avg. analysis",
                            String.format("%.0f ms", session.avgInferenceMs),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { Text("Done") }
        Spacer(Modifier.height(16.dp))
    }
}
