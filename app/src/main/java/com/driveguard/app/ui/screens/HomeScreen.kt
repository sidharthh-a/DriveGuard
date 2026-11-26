package com.driveguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.driveguard.app.data.SessionSummary
import com.driveguard.app.ui.components.Mark
import com.driveguard.app.ui.components.MetricTile
import com.driveguard.app.ui.components.PrimaryAction
import com.driveguard.app.ui.components.SectionCard
import com.driveguard.app.ui.components.SectionLabel
import com.driveguard.app.util.TimeFormat

/**
 * Home: the mark, one statement, one action, and the last session. Nothing
 * else competes for attention.
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
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mark(tint = MaterialTheme.colorScheme.primary, size = 22.dp)
                Spacer(Modifier.size(8.dp))
                Text(
                    "DriveGuard",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row {
                IconButton(onClick = onOpenHistory) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = "Session history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenAbout) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = "About",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(48.dp))
        Text(
            "Stay alert.\nDrive safely.",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Front-camera monitoring that never leaves your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(40.dp))
        PrimaryAction(
            text = "Start driving",
            onClick = onStartDriving,
            leadingIcon = Icons.Rounded.PlayArrow,
        )

        Spacer(Modifier.height(40.dp))
        SectionLabel("Last session")
        Spacer(Modifier.height(12.dp))
        if (latestSession == null) {
            Text(
                "No sessions yet. Your driving history will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SectionCard {
                Row(Modifier.fillMaxWidth()) {
                    MetricTile(
                        label = "Duration",
                        value = TimeFormat.durationHuman(latestSession.durationMs),
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "Alerts",
                        value = latestSession.alertCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "An assistant, not a safety system. If you feel tired, stop and rest.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
