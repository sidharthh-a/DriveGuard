package com.driveguard.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.driveguard.app.data.AppSettings
import com.driveguard.app.data.AppearanceMode
import com.driveguard.app.detection.Sensitivity

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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        SectionLabel("Monitoring")
        Sensitivity.entries.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.RadioButton(
                    selected = settings.sensitivityName == s.name,
                    onClick = { onSensitivityChange(s) },
                )
                Column {
                    Text(s.name.lowercase().replaceFirstChar { it.uppercase() })
                    Text(
                        s.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        SectionLabel("Alerts")
        ToggleRow("Sound", settings.soundEnabled, onSoundChange)
        ToggleRow("Vibration", settings.vibrationEnabled, onVibrationChange)
        Text(
            "Note: in silent or Do-Not-Disturb mode the alarm sound may be " +
                "suppressed by your device; vibration still runs if enabled.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        SectionLabel("Appearance")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            AppearanceMode.entries.forEach { mode ->
                androidx.compose.material3.FilterChip(
                    selected = settings.appearance == mode,
                    onClick = { onAppearanceChange(mode) },
                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        SectionLabel("Data")
        TextButton(onClick = onClearHistory) { Text("Clear session history") }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        SectionLabel("Privacy")
        TextButton(onClick = onOpenPrivacy) { Text("Privacy & data explanation") }
        TextButton(onClick = onOpenAbout) { Text("About DriveGuard") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
