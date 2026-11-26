package com.driveguard.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.driveguard.app.data.AppSettings
import com.driveguard.app.data.AppearanceMode
import com.driveguard.app.detection.Sensitivity
import com.driveguard.app.ui.components.SectionCard
import com.driveguard.app.ui.components.SectionLabel

/** Settings: monitoring, alerts, appearance, data, privacy. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSensitivityChange: (Sensitivity) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onAppearanceChange: (AppearanceMode) -> Unit,
    onClearHistory: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        SectionLabel("Monitoring")
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Column(Modifier.selectableGroup()) {
                Sensitivity.entries.forEachIndexed { index, level ->
                    if (index > 0) {
                        HorizontalDivider(
                            Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = settings.sensitivityName == level.name,
                                role = Role.RadioButton,
                                onClick = { onSensitivityChange(level) },
                            )
                            .semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.sensitivityName == level.name,
                            onClick = null,
                        )
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(
                                level.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                level.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Alerts")
        Spacer(Modifier.height(8.dp))
        SectionCard {
            ToggleRow("Sound", settings.soundEnabled, onSoundChange)
            HorizontalDivider(
                Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            ToggleRow("Vibration", settings.vibrationEnabled, onVibrationChange)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "In silent or Do-Not-Disturb mode the alarm may be muted by your " +
                "device; vibration still runs if enabled.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        SectionLabel("Appearance")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppearanceMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.appearance == mode,
                    onClick = { onAppearanceChange(mode) },
                    label = {
                        Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                    },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Data")
        Spacer(Modifier.height(8.dp))
        SectionCard {
            LinkRow("Clear session history", onClearHistory)
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Privacy")
        Spacer(Modifier.height(8.dp))
        SectionCard {
            LinkRow("Privacy & data", onOpenPrivacy)
            HorizontalDivider(
                Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            LinkRow("About DriveGuard", onOpenAbout)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}
