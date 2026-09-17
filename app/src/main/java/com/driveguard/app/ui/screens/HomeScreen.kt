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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.driveguard.app.data.SessionSummary
import com.driveguard.app.ui.components.SectionCard
import com.driveguard.app.util.TimeFormat

/**
 * Home dashboard: identity, one dominant action, recent session glance,
 * history/settings entry points and a standing safety note.
 */
@Composable
fun HomeScreen(
    latestSession: SessionSummary?,
    onStartDriving: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Row {
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Rounded.History, contentDescription = "Session history")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
                IconButton(onClick = onOpenAbout) {
                    Icon(Icons.Rounded.Info, contentDescription = "About")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "DRIVEGUARD",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Stay alert. Drive safely.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onStartDriving,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .semantics { },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Start driving", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(32.dp))

        SectionCard {
            Text("Recent session", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (latestSession == null) {
                Text(
                    "No sessions yet. Your driving history will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    com.driveguard.app.ui.components.MetricTile(
                        label = "Duration",
                        value = TimeFormat.durationHuman(latestSession.durationMs),
                    )
                    com.driveguard.app.ui.components.MetricTile(
                        label = "Alerts",
                        value = latestSession.alertCount.toString(),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionCard {
            Text("Safety note", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "DriveGuard is a driver-assistance tool. It does not replace " +
                    "responsible driving, adequate rest, or professional safety " +
                    "systems. If you feel tired, stop and rest.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
