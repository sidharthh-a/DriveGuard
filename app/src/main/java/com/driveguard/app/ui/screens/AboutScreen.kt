package com.driveguard.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.driveguard.app.ui.components.Mark
import com.driveguard.app.ui.components.SectionCard
import com.driveguard.app.ui.components.SectionLabel

/** About: identity, model transparency, limitations. */
@Composable
fun AboutScreen(appVersion: String) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Text(
            "About",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mark(tint = MaterialTheme.colorScheme.primary, size = 28.dp)
            Spacer(Modifier.size(12.dp))
            Column {
                Text("DriveGuard", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Version $appVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Real-time driver drowsiness detection, entirely on your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        SectionLabel("Detection")
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Text(
                "Google ML Kit's bundled face model locates your face and " +
                    "reports an openness probability for each eye, entirely " +
                    "on-device. A reading ML Kit cannot provide counts as open, " +
                    "and closure is only confirmed once both eyes agree.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Built with")
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Text(
                "Jetpack Compose, CameraX and Google ML Kit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Limitations")
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Text(
                "Not a medical or certified safety device.\n" +
                    "Performance depends on lighting, glasses, camera angle and " +
                    "face visibility.\n" +
                    "Alerts can be missed or false; never rely on it as your only " +
                    "safeguard against fatigue.\n" +
                    "No accuracy metric is claimed for the eye-state signal; " +
                    "validate behaviour in your own conditions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}
